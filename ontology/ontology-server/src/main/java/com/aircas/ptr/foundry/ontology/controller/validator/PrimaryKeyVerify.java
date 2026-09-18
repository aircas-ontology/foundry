package com.aircas.ptr.foundry.ontology.controller.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = PrimaryKeyValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface PrimaryKeyVerify {
    String message() default "主键不存在或有多个";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
