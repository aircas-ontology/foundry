package com.aircas.ptr.foundry.ontology.controller.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = OntologyDisplayNameValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface OntologyDisplayNameVerify {
    String message() default "本体DisplayName非法或已存在";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
