package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security;

/** Initial stable authorities. Resource ownership is still checked by each protected operation. */
public enum UserPermission {
    PROFILE_READ_SELF,
    PROFILE_UPDATE_SELF,
    PROFILE_DEACTIVATE_SELF,
    PROFILE_PHOTO_READ_SELF,
    PROFILE_PHOTO_UPDATE_SELF;

    /** Compile-time strings for annotations, kept in one place with the enum values. */
    public static final class Constants {
        public static final String PROFILE_READ_SELF = "PROFILE_READ_SELF";
        public static final String PROFILE_UPDATE_SELF = "PROFILE_UPDATE_SELF";
        public static final String PROFILE_DEACTIVATE_SELF = "PROFILE_DEACTIVATE_SELF";
        public static final String PROFILE_PHOTO_READ_SELF = "PROFILE_PHOTO_READ_SELF";
        public static final String PROFILE_PHOTO_UPDATE_SELF = "PROFILE_PHOTO_UPDATE_SELF";

        private Constants() { }
    }
}
