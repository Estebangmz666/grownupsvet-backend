package edu.uniquindio.grownupsvet.grownupsvet_backend.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/** Temporary branch-only proof for C84: this test must fail and must never be merged. */
class AuditCiExpectedFailureTests {
    @Test
    void rejectsIntegrationWhenTheRequiredBackendCheckFails() {
        fail("AUDIT_CONTROLLED_FAILURE_C84_2026_10_26: expected failure proving protected-main enforcement; do not merge.");
    }
}
