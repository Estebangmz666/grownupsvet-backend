package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.observability;

/** Closed metric labels; never construct tags from request or resource data. */
public enum BusinessOperation {
    OWNER_SIGNUP("owner_signup"),
    PET_CREATE("pet_create"),
    APPOINTMENT_CREATE("appointment_create"),
    CLINICAL_DRAFT_CREATE("clinical_draft_create"),
    CLINICAL_DRAFT_SAVE("clinical_draft_save"),
    CLINICAL_TREATMENT_SAVE("clinical_treatment_save"),
    CLINICAL_FINALIZE("clinical_finalize");

    private final String metricTag;

    BusinessOperation(String metricTag) { this.metricTag = metricTag; }

    public String metricTag() { return metricTag; }
}
