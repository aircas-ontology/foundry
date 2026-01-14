package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@AllArgsConstructor
@Getter
public enum OntologyLinkMappingEnum {

    /**
     * 1: 1对1
     * 2: 1对多
     * 3: 多对1
     * 4: 多对多
     */

    ONE_TO_ONE(1, "1对1"),
    ONE_TO_MANY(2, "1对多"),
    MANY_TO_ONE(3, "多对1"),
    MANY_TO_MANY(4, "多对多"),
    ;

    private final Integer value;
    private final String name;

    public static OntologyLinkMappingEnum getByValue(Integer value) {
        return Arrays.stream(OntologyLinkMappingEnum.values()).filter(v -> v.getValue().equals(value)).findFirst().get();
    }
}
