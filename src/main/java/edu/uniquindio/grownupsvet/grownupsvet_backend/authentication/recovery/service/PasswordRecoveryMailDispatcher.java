package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded, in-memory delivery queue; SMTP has finite timeouts configured separately. */
@Component
public class PasswordRecoveryMailDispatcher {
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), Thread.ofPlatform().daemon(true)
            .name("password-recovery-mail-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());

    public boolean submit(Runnable task) {
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException exception) {
            return false;
        }
    }

    @PreDestroy
    public void close() {
        // Do not keep the application alive or deliver obsolete messages after shutdown.
        executor.shutdownNow();
    }
}
