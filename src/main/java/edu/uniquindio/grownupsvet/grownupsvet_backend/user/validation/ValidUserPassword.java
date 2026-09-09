package edu.uniquindio.grownupsvet.grownupsvet_backend.user.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Validates a new password without trimming or changing its contents. */
@Documented
@Constraint(validatedBy = UserPasswordValidator.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface ValidUserPassword {

    String message() default "La contraseña debe tener entre 15 y 128 caracteres y no ser común.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
