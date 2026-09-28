package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.model;

import java.util.UUID;

public record AppointmentNotificationsQueuedEvent(UUID appointmentId) { }
