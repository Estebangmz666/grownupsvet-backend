package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.observability;

import java.util.Objects;

/** Internal transaction notification containing no identifiers or personal/clinical data. */
public record BusinessOperationCommittedEvent(BusinessOperation operation) {
    public BusinessOperationCommittedEvent { Objects.requireNonNull(operation, "operation is required"); }
}
