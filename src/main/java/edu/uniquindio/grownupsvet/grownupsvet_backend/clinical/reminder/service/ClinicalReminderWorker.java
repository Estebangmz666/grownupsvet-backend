package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.reminder.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.model.ClinicalEncounterFinalizedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "grownupsvet.clinical.reminders", name = "polling-enabled", havingValue = "true", matchIfMissing = true)
public class ClinicalReminderWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClinicalReminderWorker.class);
    private final ClinicalReminderMaterializer materializer;
    private final ClinicalReminderEmailDispatcher dispatcher;
    private final TaskExecutor executor;
    private final AtomicBoolean submitted = new AtomicBoolean();

    public ClinicalReminderWorker(ClinicalReminderMaterializer materializer, ClinicalReminderEmailDispatcher dispatcher,
            @Qualifier("clinicalReminderExecutor") TaskExecutor executor) {
        this.materializer = materializer; this.dispatcher = dispatcher; this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void finalized(ClinicalEncounterFinalizedEvent event) { pollPending(); }

    @Scheduled(fixedDelayString = "${grownupsvet.clinical.reminders.poll-delay-millis:15000}",
            initialDelayString = "${grownupsvet.clinical.reminders.poll-delay-millis:15000}")
    public void pollPending() {
        if (!submitted.compareAndSet(false, true)) { return; }
        try {
            executor.execute(() -> {
                try { materializer.materializeDue(); dispatcher.dispatchPending(); }
                catch (RuntimeException failure) {
                    LOGGER.warn("Clinical reminder queue will be retried: exceptionType={}", failure.getClass().getName());
                } finally { submitted.set(false); }
            });
        } catch (TaskRejectedException failure) {
            submitted.set(false);
        }
    }
}
