package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grownupsvet.availability")
public class AvailabilityProperties {
    private java.time.Duration minimumOwnerLeadTime = java.time.Duration.ofHours(2);
    private java.time.Duration maximumOwnerHorizon = java.time.Duration.ofDays(60);

    public java.time.Duration getMinimumOwnerLeadTime() { return minimumOwnerLeadTime; }
    public void setMinimumOwnerLeadTime(java.time.Duration value) { minimumOwnerLeadTime = value; }
    public java.time.Duration getMaximumOwnerHorizon() { return maximumOwnerHorizon; }
    public void setMaximumOwnerHorizon(java.time.Duration value) { maximumOwnerHorizon = value; }

    @jakarta.annotation.PostConstruct
    void validate() {
        if (minimumOwnerLeadTime == null || maximumOwnerHorizon == null
                || minimumOwnerLeadTime.isNegative() || minimumOwnerLeadTime.isZero()
                || maximumOwnerHorizon.compareTo(minimumOwnerLeadTime) <= 0) {
            throw new IllegalStateException("Availability owner window must have a positive minimum and a larger maximum.");
        }
    }
}
