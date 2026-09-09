package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

public record JwtRsaKeyPair(RSAPublicKey publicKey, RSAPrivateKey privateKey) { }
