package com.aircas.ptr.foundry.ontology.controller.validator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = GroupIdValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface GroupIdVerify {
    String message() default "Invalid groupId";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
