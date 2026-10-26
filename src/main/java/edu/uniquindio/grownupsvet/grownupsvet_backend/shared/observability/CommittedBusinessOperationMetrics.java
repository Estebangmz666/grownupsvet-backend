package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumMap;
import java.util.Map;

/** Operational counters follow successful commits; metrics failures never fail committed business work. */
@Component
public class CommittedBusinessOperationMetrics {
    public static final String METRIC_NAME = "grownupsvet.business.commits";
    private static final Logger LOGGER = LoggerFactory.getLogger(CommittedBusinessOperationMetrics.class);
    private final Map<BusinessOperation, Counter> counters = new EnumMap<>(BusinessOperation.class);

    public CommittedBusinessOperationMetrics(MeterRegistry registry) {
        for (BusinessOperation operation : BusinessOperation.values()) {
            try {
                counters.put(operation, registry.counter(METRIC_NAME, "operation", operation.metricTag()));
            } catch (RuntimeException failure) {
                logUnavailableMetric(failure);
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void operationCommitted(BusinessOperationCommittedEvent event) {
        try {
            Counter counter = counters.get(event.operation());
            if (counter != null) { counter.increment(); }
        } catch (RuntimeException failure) {
            logUnavailableMetric(failure);
        }
    }

    private static void logUnavailableMetric(RuntimeException failure) {
        LOGGER.warn("Committed business metric unavailable: exceptionType={}", failure.getClass().getName());
    }
}
