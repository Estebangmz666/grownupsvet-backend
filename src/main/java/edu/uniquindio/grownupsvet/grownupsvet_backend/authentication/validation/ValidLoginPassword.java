package edu.uniquindio.grownupsvet.grownupsvet_backend.authentication.validation;

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

/** Limits credential input without reapplying password-creation policy during login. */
@Documented
@Constraint(validatedBy = LoginPasswordValidator.class)
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface ValidLoginPassword {
    String message() default "La contraseña no puede superar los 128 caracteres.";

    Class<?>[] groups() default { };

    Class<? extends Payload>[] payload() default { };
}
