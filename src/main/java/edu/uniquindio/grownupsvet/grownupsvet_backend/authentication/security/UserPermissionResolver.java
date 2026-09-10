package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security;

import edu.uniquindio.grownupsvet.grownupsvet_backend.user.model.UserRole;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserPermissionResolver {
    private static final List<String> OWNER_PERMISSIONS = List.of(
            UserPermission.PROFILE_READ_SELF.name(),
            UserPermission.PROFILE_UPDATE_SELF.name(),
            UserPermission.PROFILE_DEACTIVATE_SELF.name(),
            UserPermission.PROFILE_PHOTO_READ_SELF.name(),
            UserPermission.PROFILE_PHOTO_UPDATE_SELF.name(),
            UserPermission.PET_CREATE_SELF.name(),
            UserPermission.PET_READ_SELF.name(),
            UserPermission.PET_UPDATE_SELF.name());

    private static final List<String> STAFF_PERMISSIONS = List.of(
            UserPermission.PROFILE_PHOTO_READ_SELF.name(),
            UserPermission.PROFILE_PHOTO_UPDATE_SELF.name());

    /** Owner data and a staff member's professional data are intentionally separate resources. */
    public List<String> resolve(UserRole role) {
        return switch (role) {
            case OWNER -> OWNER_PERMISSIONS;
            case VETERINARIAN, ADMINISTRATOR -> STAFF_PERMISSIONS;
        };
    }
}
