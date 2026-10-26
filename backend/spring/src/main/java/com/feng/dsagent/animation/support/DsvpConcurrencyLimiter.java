package com.feng.dsagent.animation.support;

import com.feng.dsagent.animation.error.AnimationErrorCode;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class DsvpConcurrencyLimiter {

    private final Semaphore permits;

    public DsvpConcurrencyLimiter(@Value("${app.animation.maximum-concurrent-simulations:4}") int maximumConcurrency) {
        if (maximumConcurrency < 1 || maximumConcurrency > 64) {
            throw new IllegalArgumentException("animation concurrency limit is invalid");
        }
        this.permits = new Semaphore(maximumConcurrency, true);
    }

    public Permit acquire() {
        if (!permits.tryAcquire()) {
            throw AnimationErrorCode.DSVP_CONCURRENCY_LIMITED.toException();
        }
        return new Permit(permits);
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore permits;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Permit(Semaphore permits) {
            this.permits = permits;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) permits.release();
        }
    }
}
