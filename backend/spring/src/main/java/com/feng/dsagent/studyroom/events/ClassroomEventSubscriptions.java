package com.feng.dsagent.studyroom.events;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Reads committed events only. Disconnecting never changes generation state. */
@Service
public class ClassroomEventSubscriptions {
    private final ClassroomEventService events;
    public ClassroomEventSubscriptions(ClassroomEventService events) { this.events=events; }
    public void requireRoom(long user,String stream,String room) { events.requireRoom(user,stream,room); }

    public SseEmitter subscribe(long user,String stream,long since) {
        var initial=events.read(user,stream,since);
        var emitter=new SseEmitter(300_000L);
        var closed=new AtomicBoolean();
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> {closed.set(true);emitter.complete();});
        emitter.onError(error -> closed.set(true));
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
                closed.set(true); emitter.complete();
            } catch(RuntimeException inaccessible) {
                // The next authenticated reconnect can return the authoritative 401/404 envelope.
                emitter.complete();
            }
        });
        return emitter;
    }

    @Scheduled(fixedDelay=3_600_000L)
    void cleanup() { events.cleanup(); }
}
