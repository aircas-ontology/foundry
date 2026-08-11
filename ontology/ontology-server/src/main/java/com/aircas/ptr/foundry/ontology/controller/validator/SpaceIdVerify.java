package com.aircas.ptr.foundry.ontology.controller.validator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = SpaceIdValidator.class)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SpaceIdVerify {
    String message() default "空间不存在";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}