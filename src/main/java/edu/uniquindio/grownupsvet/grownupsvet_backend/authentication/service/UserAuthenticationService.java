package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.dto.UserLoginRequestDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.dto.UserLoginResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.exception.InvalidUserCredentialsException;
import edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security.UserPermissionResolver;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.User;
import edu.uniquindio.grownupsvet.grownupsvet_backend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserAuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserPermissionResolver permissionResolver;
    private final JwtTokenService jwtTokenService;
    private final String dummyPasswordHash;

    public UserAuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                     UserPermissionResolver permissionResolver, JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionResolver = permissionResolver;
        this.jwtTokenService = jwtTokenService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** Always performs one password verification so an unknown email follows the expensive path too. */
    @Transactional(readOnly = true)
    public UserLoginResponseDTO authenticate(UserLoginRequestDTO request) {
        String normalizedEmail = User.normalizeEmail(request.email());
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        String storedPasswordHash = user == null ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), storedPasswordHash);
        if (user == null || !user.isActive() || !passwordMatches) {
            throw new InvalidUserCredentialsException();
        }
        return jwtTokenService.issue(user, permissionResolver.resolve(user.getRole()));
    }
}
