package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.repository.PasswordRecoveryRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.recovery.repository.PasswordRecoveryEmailTaskRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.time.Duration;

@Component
public class PasswordRecoveryMaintenance {
    private final PasswordRecoveryRepository repository;
    private final PasswordRecoveryEmailTaskRepository emailTasks;
    private final Clock clock;

    public PasswordRecoveryMaintenance(PasswordRecoveryRepository repository, PasswordRecoveryEmailTaskRepository emailTasks, Clock clock) {
        this.repository = repository;
        this.emailTasks = emailTasks;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Scheduled(initialDelay = 3_600_000, fixedDelay = 3_600_000)
    public void removeExpiredState() {
        // Each independent JDBC cleanup commits before the next table is touched. Sharing their
        // locks would invert request ordering (rate bucket -> account -> challenge/email task).
        // No decryption is needed: even a disabled channel can purge expired sensitive payloads.
        emailTasks.supersedeExpiredCodes(clock.instant());
        repository.deleteExpired(clock.instant().minus(Duration.ofHours(2)));
        emailTasks.purgeTerminalBefore(clock.instant().minus(Duration.ofDays(7)));
    }
}
