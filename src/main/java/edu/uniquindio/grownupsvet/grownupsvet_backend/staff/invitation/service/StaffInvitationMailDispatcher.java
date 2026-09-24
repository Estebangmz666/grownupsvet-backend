package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.model.StaffInvitationCreatedEvent;
import edu.uniquindio.grownupsvet.grownupsvet_backend.staff.invitation.repository.StaffInvitationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.util.UUID;

@Component
public class StaffInvitationMailDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaffInvitationMailDispatcher.class);
    private final StaffInvitationRepository invitations;
    private final StaffInvitationDeliveryService delivery;
    private final TaskExecutor executor;
    private final Clock clock;

    public StaffInvitationMailDispatcher(StaffInvitationRepository invitations, StaffInvitationDeliveryService delivery,
            @Qualifier("staffInvitationExecutor") TaskExecutor executor, Clock clock) {
        this.invitations = invitations;
        this.delivery = delivery;
        this.executor = executor;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invitationCreated(StaffInvitationCreatedEvent event) { submit(event.invitationId()); }

    @Scheduled(fixedDelayString = "${grownupsvet.staff.invitations.dispatch-delay:15000}",
            initialDelayString = "${grownupsvet.staff.invitations.dispatch-delay:15000}")
    public void dispatchPending() {
        try {
            for (UUID invitationId : invitations.findPendingDeliveryIds(clock.instant(), 16)) { submit(invitationId); }
        } catch (RuntimeException exception) {
            LOGGER.warn("Staff invitation queue could not be polled; durable tasks will be retried.");
        }
    }

    private void submit(UUID invitationId) {
        try {
            executor.execute(() -> {
                try { delivery.deliver(invitationId); }
                catch (RuntimeException exception) {
                    LOGGER.warn("Staff invitation delivery transaction failed; its durable task remains available.");
                }
            });
        } catch (TaskRejectedException exception) {
            // A full queue or shutdown only postpones delivery; the next poll discovers the row again.
        }
    }
}
