package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.reminder.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
@ConfigurationProperties("grownupsvet.clinical.reminders")
public class ClinicalReminderProperties {
    private long pollDelayMillis = 15000;
    private Duration deliveryGrace = Duration.ofMinutes(5);
    private Duration leaseDuration = Duration.ofMinutes(2);
    private Duration retryDelay = Duration.ofSeconds(30);
    private int batchSize = 50;

    @PostConstruct
    public void validate() {
        if (pollDelayMillis < 1000 || pollDelayMillis > 3600000 || batchSize < 1 || batchSize > 200
                || deliveryGrace == null || deliveryGrace.compareTo(Duration.ofMillis(pollDelayMillis + 1000)) < 0
                || deliveryGrace.compareTo(Duration.ofDays(1)) > 0 || leaseDuration == null
                || leaseDuration.compareTo(Duration.ofSeconds(30)) < 0 || leaseDuration.compareTo(Duration.ofMinutes(10)) > 0
                || retryDelay == null || retryDelay.compareTo(Duration.ofSeconds(1)) < 0 || retryDelay.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalStateException("Clinical reminder polling, delivery grace, lease, retry delay and batch size must be bounded and consistent.");
        }
    }

    public long getPollDelayMillis() { return pollDelayMillis; }
    public void setPollDelayMillis(long value) { pollDelayMillis = value; }
    public Duration getDeliveryGrace() { return deliveryGrace; }
    public void setDeliveryGrace(Duration value) { deliveryGrace = value; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration value) { leaseDuration = value; }
    public Duration getRetryDelay() { return retryDelay; }
    public void setRetryDelay(Duration value) { retryDelay = value; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int value) { batchSize = value; }
}
