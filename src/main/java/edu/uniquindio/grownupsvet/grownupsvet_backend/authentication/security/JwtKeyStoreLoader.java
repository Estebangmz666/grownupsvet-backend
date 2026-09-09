package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security;

import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

final class JwtKeyStoreLoader {
    private JwtKeyStoreLoader() { }

    static JwtRsaKeyPair load(JwtSecurityProperties properties) {
        requireText(properties.keyStorePath(), "JWT_KEYSTORE_PATH");
        requireText(properties.keyStorePassword(), "JWT_KEYSTORE_PASSWORD");
        requireText(properties.keyAlias(), "JWT_KEY_ALIAS");
        String keyPasswordValue = StringUtils.hasText(properties.keyPassword())
                ? properties.keyPassword() : properties.keyStorePassword();
        Path keyStorePath = Path.of(properties.keyStorePath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(keyStorePath)) {
            throw new IllegalStateException("JWT keystore file is not available at the configured path.");
        }

        char[] storePassword = properties.keyStorePassword().toCharArray();
        char[] keyPassword = keyPasswordValue.toCharArray();
        try (InputStream input = Files.newInputStream(keyStorePath)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(input, storePassword);
            Key key = keyStore.getKey(properties.keyAlias(), keyPassword);
            Certificate certificate = keyStore.getCertificate(properties.keyAlias());
            if (!(key instanceof RSAPrivateKey privateKey)
                    || certificate == null || !(certificate.getPublicKey() instanceof RSAPublicKey publicKey)) {
                throw new IllegalStateException("Configured JWT alias must contain an RSA private key and certificate.");
            }
            if (publicKey.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("JWT RSA key must contain at least 2048 bits.");
            }
            return new JwtRsaKeyPair(publicKey, privateKey);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("JWT keystore could not be loaded with the configured credentials.", exception);
        } finally {
            Arrays.fill(storePassword, '\0');
            Arrays.fill(keyPassword, '\0');
        }
    }

    private static void requireText(String value, String environmentVariable) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(environmentVariable + " must be configured.");
        }
    }
}
