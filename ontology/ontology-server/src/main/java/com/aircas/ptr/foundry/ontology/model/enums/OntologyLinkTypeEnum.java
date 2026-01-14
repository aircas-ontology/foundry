package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OntologyLinkTypeEnum {

    COMPOSITION(1, "组合关系"),
    OTHER(2, "其他关系"),
    ;

    private final Integer value;
    private final String name;

    public static Status mappingToStatus(OntologyLinkTypeEnum value) {
        switch (value) {
            case COMPOSITION:
                return Status.ENABLE;
            default:
                return Status.DELETE;
        }
    }
}
