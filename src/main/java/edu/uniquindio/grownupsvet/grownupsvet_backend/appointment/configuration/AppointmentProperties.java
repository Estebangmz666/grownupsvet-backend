package edu.uniquindio.grownupsvet.grownupsvet_backend.appointment.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "grownupsvet.appointments")
public class AppointmentProperties {
    private LocalTime workdayStartTime;
    private ZoneId businessZone = ZoneId.of("America/Bogota");
    private int minimumOwnerAdvanceDays = 1;
    private int maximumOwnerHorizonDays = 60;
    private long cutoffPollDelayMillis = 30_000;
    private long emailPollDelayMillis = 15_000;
    private int maximumEmailAttempts = 5;
    private String senderAddress = "";

    public LocalTime getWorkdayStartTime() { return workdayStartTime; }
    public void setWorkdayStartTime(LocalTime value) { workdayStartTime = value; }
    public ZoneId getBusinessZone() { return businessZone; }
    public void setBusinessZone(ZoneId value) { businessZone = value; }
    public int getMinimumOwnerAdvanceDays() { return minimumOwnerAdvanceDays; }
    public void setMinimumOwnerAdvanceDays(int value) { minimumOwnerAdvanceDays = value; }
    public int getMaximumOwnerHorizonDays() { return maximumOwnerHorizonDays; }
    public void setMaximumOwnerHorizonDays(int value) { maximumOwnerHorizonDays = value; }
    public long getCutoffPollDelayMillis() { return cutoffPollDelayMillis; }
    public void setCutoffPollDelayMillis(long value) { cutoffPollDelayMillis = value; }
    public long getEmailPollDelayMillis() { return emailPollDelayMillis; }
    public void setEmailPollDelayMillis(long value) { emailPollDelayMillis = value; }
    public int getMaximumEmailAttempts() { return maximumEmailAttempts; }
    public void setMaximumEmailAttempts(int value) { maximumEmailAttempts = value; }
    public String getSenderAddress() { return senderAddress; }
    public void setSenderAddress(String value) { senderAddress = value; }

    @jakarta.annotation.PostConstruct
    void validate() {
        if (workdayStartTime == null || businessZone == null || minimumOwnerAdvanceDays < 1
                || maximumOwnerHorizonDays < minimumOwnerAdvanceDays || cutoffPollDelayMillis < 1000
                || emailPollDelayMillis < 1000 || maximumEmailAttempts < 1) {
            throw new IllegalStateException("Appointment scheduling properties are invalid or incomplete.");
        }
        if (!ZoneId.of("America/Bogota").equals(businessZone)) {
            throw new IllegalStateException("Appointment business zone must be America/Bogota.");
        }
    }
}
