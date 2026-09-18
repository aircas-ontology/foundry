package com.aircas.ptr.foundry.ontology.controller.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = DatasourceValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface DatasourceVerify {
    String message() default "无效的数据源 或 填写的数据源数据不完整";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
