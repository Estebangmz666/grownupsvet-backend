package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.ratelimit.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.ratelimit.configuration.LoginAttemptRateLimitProperties;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.ratelimit.exception.LoginAttemptRateLimitException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.ratelimit.repository.LoginAttemptRateLimitRepository;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class LoginAttemptRateLimitService {
    private final LoginAttemptRateLimitProperties properties;
    private final LoginAttemptRateLimitRepository repository;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final byte[] hmacKey;

    public LoginAttemptRateLimitService(LoginAttemptRateLimitProperties properties,
            LoginAttemptRateLimitRepository repository, Clock clock, PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.repository = repository;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactions.setTimeout(5);
        this.hmacKey = properties.enabled() ? Base64.getDecoder().decode(properties.hmacSecret()) : new byte[0];
    }

    /** Admission commits before account lookup/Argon2 and survives invalid-credentials exceptions. */
    public void admit(String email, String transportPeerAddress) {
        if (!properties.enabled()) { return; }
        String peer = InetAddress.ofLiteral(transportPeerAddress).getHostAddress();
        String originKey = bucketKey("peer\0" + peer);
        String identifierOriginKey = bucketKey("identifier-peer\0" + User.normalizeEmail(email) + "\0" + peer);
        Long retryAfter = transactions.execute(status -> repository.admit(originKey, properties.originMaximum(),
                properties.originWindow(), identifierOriginKey, properties.identifierOriginMaximum(),
                properties.identifierOriginWindow(), clock.instant()));
        if (retryAfter == null) { throw new IllegalStateException("Login admission did not produce a decision."); }
        if (retryAfter > 0) { throw new LoginAttemptRateLimitException(retryAfter); }
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void removeExpiredCounters() {
        if (properties.enabled()) {
            transactions.executeWithoutResult(status -> repository.removeExpired(clock.instant()));
        }
    }

    private String bucketKey(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Login admission key generation is unavailable.");
        }
    }
}
