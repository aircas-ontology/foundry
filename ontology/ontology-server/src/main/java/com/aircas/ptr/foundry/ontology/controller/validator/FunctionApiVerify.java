package com.aircas.ptr.foundry.ontology.controller.validator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = FunctionApiValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface FunctionApiVerify {
    String message() default "Invalid function api";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
