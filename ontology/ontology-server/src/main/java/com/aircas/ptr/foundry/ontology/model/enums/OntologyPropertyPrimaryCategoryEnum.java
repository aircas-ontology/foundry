package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.util.Arrays;



@AllArgsConstructor
@Getter
public enum OntologyPropertyPrimaryCategoryEnum {

    DESIGN_MANUFACTURING(1, "设计制造"),
    INVESTIGATION_MEASUREMENT(2, "实侦实测"),
    ANALYSIS_PREDICTION(3,"分析预测"),
    ROCKET_FORCE(4,"火箭军"),

    ;

    private final Integer value;
    private final String name;

    public static OntologyPropertyPrimaryCategoryEnum getByName(String name) {
        return Arrays.stream(OntologyPropertyPrimaryCategoryEnum.values()).filter(v -> v.getName().equals(name)).findFirst().orElse(null);
    }
}
