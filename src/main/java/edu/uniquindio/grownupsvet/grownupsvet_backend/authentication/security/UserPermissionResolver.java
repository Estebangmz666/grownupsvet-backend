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
            UserPermission.PET_UPDATE_SELF.name(),
            UserPermission.VETERINARIAN_PROFILE_READ.name(),
            UserPermission.VETERINARIAN_AVAILABILITY_READ_AVAILABLE.name());

    private static final List<String> STAFF_PERMISSIONS = List.of(
            UserPermission.PROFILE_PHOTO_READ_SELF.name(),
            UserPermission.PROFILE_PHOTO_UPDATE_SELF.name());

    /** Owner data and a staff member's professional data are intentionally separate resources. */
    public List<String> resolve(UserRole role) {
        return switch (role) {
            case OWNER -> OWNER_PERMISSIONS;
            case VETERINARIAN -> List.of(UserPermission.PROFILE_PHOTO_READ_SELF.name(),
                    UserPermission.PROFILE_PHOTO_UPDATE_SELF.name(),
                    UserPermission.VETERINARIAN_AVAILABILITY_READ_SELF.name());
            case ADMINISTRATOR -> List.of(UserPermission.PROFILE_PHOTO_READ_SELF.name(),
                    UserPermission.PROFILE_PHOTO_UPDATE_SELF.name(), UserPermission.VETERINARIAN_MANAGE.name(),
                    UserPermission.VETERINARIAN_AVAILABILITY_MANAGE.name());
            case SUPER_ADMIN -> List.of(UserPermission.PROFILE_PHOTO_READ_SELF.name(),
                    UserPermission.PROFILE_PHOTO_UPDATE_SELF.name(), UserPermission.ADMINISTRATOR_MANAGE.name());
        };
    }
}
