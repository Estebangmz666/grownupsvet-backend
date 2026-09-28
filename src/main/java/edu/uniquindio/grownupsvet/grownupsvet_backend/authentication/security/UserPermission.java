package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.security;

/** Initial stable authorities. Resource ownership is still checked by each protected operation. */
public enum UserPermission {
    PROFILE_READ_SELF,
    PROFILE_UPDATE_SELF,
    PROFILE_DEACTIVATE_SELF,
    PROFILE_PHOTO_READ_SELF,
    PROFILE_PHOTO_UPDATE_SELF,
    PET_CREATE_SELF,
    PET_READ_SELF,
    PET_UPDATE_SELF,
    ADMINISTRATOR_MANAGE,
    VETERINARIAN_MANAGE,
    VETERINARIAN_PROFILE_READ,
    VETERINARIAN_AVAILABILITY_MANAGE,
    VETERINARIAN_AVAILABILITY_READ_SELF,
    VETERINARIAN_AVAILABILITY_READ_AVAILABLE,
    APPOINTMENT_CREATE_SELF,
    APPOINTMENT_READ_SELF,
    APPOINTMENT_READ_ASSIGNED,
    APPOINTMENT_READ_ALL,
    APPOINTMENT_MANAGE,
    APPOINTMENT_OWNER_EMAIL_READ;

    /** Compile-time strings for annotations, kept in one place with the enum values. */
    public static final class Constants {
        public static final String PROFILE_READ_SELF = "PROFILE_READ_SELF";
        public static final String PROFILE_UPDATE_SELF = "PROFILE_UPDATE_SELF";
        public static final String PROFILE_DEACTIVATE_SELF = "PROFILE_DEACTIVATE_SELF";
        public static final String PROFILE_PHOTO_READ_SELF = "PROFILE_PHOTO_READ_SELF";
        public static final String PROFILE_PHOTO_UPDATE_SELF = "PROFILE_PHOTO_UPDATE_SELF";
        public static final String PET_CREATE_SELF = "PET_CREATE_SELF";
        public static final String PET_READ_SELF = "PET_READ_SELF";
        public static final String PET_UPDATE_SELF = "PET_UPDATE_SELF";
        public static final String ADMINISTRATOR_MANAGE = "ADMINISTRATOR_MANAGE";
        public static final String VETERINARIAN_MANAGE = "VETERINARIAN_MANAGE";
        public static final String VETERINARIAN_PROFILE_READ = "VETERINARIAN_PROFILE_READ";
        public static final String VETERINARIAN_AVAILABILITY_MANAGE = "VETERINARIAN_AVAILABILITY_MANAGE";
        public static final String VETERINARIAN_AVAILABILITY_READ_SELF = "VETERINARIAN_AVAILABILITY_READ_SELF";
        public static final String VETERINARIAN_AVAILABILITY_READ_AVAILABLE = "VETERINARIAN_AVAILABILITY_READ_AVAILABLE";
        public static final String APPOINTMENT_CREATE_SELF = "APPOINTMENT_CREATE_SELF";
        public static final String APPOINTMENT_READ_SELF = "APPOINTMENT_READ_SELF";
        public static final String APPOINTMENT_READ_ASSIGNED = "APPOINTMENT_READ_ASSIGNED";
        public static final String APPOINTMENT_READ_ALL = "APPOINTMENT_READ_ALL";
        public static final String APPOINTMENT_MANAGE = "APPOINTMENT_MANAGE";
        public static final String APPOINTMENT_OWNER_EMAIL_READ = "APPOINTMENT_OWNER_EMAIL_READ";

        private Constants() { }
    }
}
