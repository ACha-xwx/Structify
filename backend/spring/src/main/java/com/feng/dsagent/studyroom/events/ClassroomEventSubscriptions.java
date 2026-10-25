package com.feng.dsagent.studyroom.events;

import com.feng.dsagent.studyroom.events.service.ClassroomEventService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.feng.dsagent.common.ApiException;

/** Reads committed events only. Disconnecting never changes generation state. */
@Service
public class ClassroomEventSubscriptions {
    private static final int MAX_ACTIVE_SUBSCRIPTIONS = 64;
    private static final int MAX_ACTIVE_SUBSCRIPTIONS_PER_USER = 4;

    private final ClassroomEventService events;
    private final SubscriptionLimiter limiter;

    @Autowired
    public ClassroomEventSubscriptions(ClassroomEventService events) {
        this(events, MAX_ACTIVE_SUBSCRIPTIONS, MAX_ACTIVE_SUBSCRIPTIONS_PER_USER);
    }

    public ClassroomEventSubscriptions(ClassroomEventService events, int maximumSubscriptions, int maximumSubscriptionsPerUser) {
        this.events = events;
        this.limiter = new SubscriptionLimiter(maximumSubscriptions, maximumSubscriptionsPerUser);
    }

    public void requireRoom(long user,String stream,String room) { events.requireRoom(user,stream,room); }

    public SseEmitter subscribe(long user,String stream,long since) {
        var permit = limiter.acquire(user);
        var closed = new AtomicBoolean();
        Runnable close = () -> {
            if (closed.compareAndSet(false, true)) permit.close();
        };
        try {
            var initial=events.read(user,stream,since);
            var emitter=new SseEmitter(300_000L);
            emitter.onCompletion(close);
            emitter.onTimeout(() -> { close.run(); emitter.complete(); });
            emitter.onError(error -> close.run());
            Thread.startVirtualThread(() -> {
                long cursor=since, started=System.nanoTime(), heartbeat=started;
                var batch=initial;
                try {
                    while(!closed.get() && System.nanoTime()-started<295_000_000_000L) {
                        if(batch.reset()) {
                            cursor=batch.snapshot().cursor();
                            emitter.send(SseEmitter.event().name("reset").id(Long.toString(cursor)).data(batch.snapshot()));
                        } else for(var event:batch.events()) {
                            emitter.send(SseEmitter.event().name(event.kind()).id(Long.toString(event.sequence())).data(event));
                            cursor=event.sequence();
                        }
                        if(batch.snapshot().terminal() && cursor>=batch.snapshot().cursor()) break;
                        if(System.nanoTime()-heartbeat>15_000_000_000L) {
                            emitter.send(SseEmitter.event().comment("heartbeat")); heartbeat=System.nanoTime();
                        }
                        Thread.sleep(250);
                        batch=events.read(user,stream,cursor);
                    }
                    emitter.complete();
                } catch(InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); emitter.complete();
                } catch(IOException disconnected) {
                    close.run(); emitter.complete();
                } catch(RuntimeException inaccessible) {
                    // The next authenticated reconnect can return the authoritative 401/404 envelope.
                    emitter.complete();
                } finally {
                    close.run();
                }
            });
            return emitter;
        } catch (RuntimeException | Error failure) {
            close.run();
            throw failure;
        }
    }

    @Scheduled(fixedDelay=3_600_000L)
    public void cleanup() { events.cleanup(); }

    public static final class SubscriptionLimiter {
        private final Semaphore globalPermits;
        private final int permitsPerUser;
        private final Map<Long, UserGate> users = new HashMap<>();

        SubscriptionLimiter(int maximumSubscriptions, int maximumSubscriptionsPerUser) {
            if (maximumSubscriptions < 1 || maximumSubscriptionsPerUser < 1
                    || maximumSubscriptionsPerUser > maximumSubscriptions) {
                throw new IllegalArgumentException("classroom event subscription limits are invalid");
            }
            this.globalPermits = new Semaphore(maximumSubscriptions, true);
            this.permitsPerUser = maximumSubscriptionsPerUser;
        }

        Permit acquire(long user) {
            synchronized (users) {
                if (!globalPermits.tryAcquire()) throw limited();
                var gate = users.computeIfAbsent(user, ignored -> new UserGate(permitsPerUser));
                if (!gate.permits.tryAcquire()) {
                    globalPermits.release();
                    throw limited();
                }
                gate.activePermits++;
                return new Permit(this, user, gate);
            }
        }

        private void release(long user, UserGate gate) {
            synchronized (users) {
                gate.permits.release();
                gate.activePermits--;
                globalPermits.release();
                if (gate.activePermits == 0) users.remove(user, gate);
            }
        }

        private ApiException limited() {
            return new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "CLASSROOM_EVENT_SUBSCRIPTIONS_LIMITED",
                "课堂进度连接较多，请稍后重试"
            );
        }

        static final class Permit implements AutoCloseable {
            private final SubscriptionLimiter owner;
            private final long user;
            private final UserGate gate;
            private final AtomicBoolean closed = new AtomicBoolean();

            private Permit(SubscriptionLimiter owner, long user, UserGate gate) {
                this.owner = owner;
                this.user = user;
                this.gate = gate;
            }

            @Override
            public void close() {
                if (closed.compareAndSet(false, true)) owner.release(user, gate);
            }
        }

        private static final class UserGate {
            private final Semaphore permits;
            private int activePermits;

            private UserGate(int permits) { this.permits = new Semaphore(permits, true); }
        }
    }
}
