package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.repository.PasswordRecoveryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "grownupsvet.security.password-recovery", name = "enabled", havingValue = "true")
public class PasswordRecoveryMaintenance {
    private final PasswordRecoveryRepository repository;
    private final Clock clock;

    public PasswordRecoveryMaintenance(PasswordRecoveryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    @Scheduled(initialDelay = 3_600_000, fixedDelay = 3_600_000)
    public void removeExpiredState() {
        repository.deleteExpired(clock.instant().minus(Duration.ofHours(2)));
    }
}
