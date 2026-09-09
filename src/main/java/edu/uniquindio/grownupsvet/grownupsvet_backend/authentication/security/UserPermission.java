package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security;

/** Initial stable authorities. Resource ownership is still checked by each protected operation. */
public enum UserPermission {
    PROFILE_READ_SELF,
    PROFILE_UPDATE_SELF,
    PROFILE_PHOTO_READ_SELF,
    PROFILE_PHOTO_UPDATE_SELF
}
