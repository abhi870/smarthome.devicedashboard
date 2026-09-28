package com.abhishek.smarthome.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value must be a time zone id {@link java.time.ZoneId#of} accepts, e.g. {@code Asia/Kolkata}, {@code UTC} or
 * {@code +05:30} (case-sensitive). {@code null} and blank are valid — combine with {@code @NotBlank} when required.
 */
@Documented
@Constraint(validatedBy = TimeZoneIdValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface TimeZoneId {

	String message() default "must be a valid IANA time zone id, e.g. Asia/Kolkata";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
