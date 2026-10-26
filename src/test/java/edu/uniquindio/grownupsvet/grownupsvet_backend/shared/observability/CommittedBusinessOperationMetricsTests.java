package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class CommittedBusinessOperationMetricsTests {
    @Test
    void aFailedCounterNeverThrowsAfterBusinessCommitOrLogsTheBackendMessage(CapturedOutput output) {
        MeterRegistry registry = mock(MeterRegistry.class);
        Counter counter = mock(Counter.class);
        when(registry.counter(eq(CommittedBusinessOperationMetrics.METRIC_NAME), any(String[].class))).thenReturn(counter);
        doThrow(new IllegalStateException("private-backend-diagnostic-value")).when(counter).increment();
        CommittedBusinessOperationMetrics observer = new CommittedBusinessOperationMetrics(registry);

        assertThatCode(() -> observer.operationCommitted(new BusinessOperationCommittedEvent(BusinessOperation.OWNER_SIGNUP)))
                .doesNotThrowAnyException();
        assertThat(output.getAll()).contains("exceptionType=java.lang.IllegalStateException")
                .doesNotContain("private-backend-diagnostic-value");
    }

    @Test
    void unavailableMetricRegistrationDoesNotPreventBusinessServicesFromStarting(CapturedOutput output) {
        MeterRegistry registry = mock(MeterRegistry.class);
        when(registry.counter(eq(CommittedBusinessOperationMetrics.METRIC_NAME), any(String[].class)))
                .thenThrow(new IllegalArgumentException("private-registration-diagnostic-value"));

        assertThatCode(() -> {
            CommittedBusinessOperationMetrics observer = new CommittedBusinessOperationMetrics(registry);
            observer.operationCommitted(new BusinessOperationCommittedEvent(BusinessOperation.CLINICAL_FINALIZE));
        }).doesNotThrowAnyException();
        assertThat(output.getAll()).contains("exceptionType=java.lang.IllegalArgumentException")
                .doesNotContain("private-registration-diagnostic-value");
    }
}
