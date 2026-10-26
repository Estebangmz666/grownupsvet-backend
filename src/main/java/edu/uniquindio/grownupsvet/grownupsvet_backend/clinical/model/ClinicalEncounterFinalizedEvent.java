package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.model;

import java.util.UUID;

/** Published within finalization so durable follow-up can join that same commit. */
public record ClinicalEncounterFinalizedEvent(UUID encounterId) { }
