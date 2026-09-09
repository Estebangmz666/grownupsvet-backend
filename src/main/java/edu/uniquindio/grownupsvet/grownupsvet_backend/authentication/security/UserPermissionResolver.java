package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserPermissionResolver {
    private static final List<String> SELF_PROFILE_PERMISSIONS = List.of(
            UserPermission.PROFILE_READ_SELF.name(),
            UserPermission.PROFILE_UPDATE_SELF.name(),
            UserPermission.PROFILE_PHOTO_READ_SELF.name(),
            UserPermission.PROFILE_PHOTO_UPDATE_SELF.name());

    /** All current account types may manage their own profile; domain permissions are added with real operations. */
    public List<String> resolve(UserRole role) {
        return switch (role) {
            case OWNER, VETERINARIAN, ADMINISTRATOR -> SELF_PROFILE_PERMISSIONS;
        };
    }
}
