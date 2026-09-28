package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AppointmentCutoffScheduler {
    private final AppointmentService appointments;
    public AppointmentCutoffScheduler(AppointmentService appointments) { this.appointments = appointments; }

    @Scheduled(fixedDelayString = "${grownupsvet.appointments.cutoff-poll-delay-millis:30000}")
    public void applyDailyCutoff() { appointments.expirePendingAppointments(); }
}
