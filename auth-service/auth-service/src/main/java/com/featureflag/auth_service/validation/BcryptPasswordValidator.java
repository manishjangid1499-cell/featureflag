package com.featureflag.auth_service.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class BcryptPasswordValidator implements ConstraintValidator<BcryptPassword, String> {
    public static boolean withinByteLimit(String value) {
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Required/minimum-length validation is separate; never truncate encoder input.
        return withinByteLimit(value);
    }
}
