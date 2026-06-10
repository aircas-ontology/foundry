package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OntologyLinkTypeEnum {

    COMPOSITION(1, "组合关系"),
    RECONNAISSANCE(2, "侦察关系"),
    STRIKE(3, "打击关系"),
    COORDINATION(4, "协同关系"),
    OTHER(5, "其他关系"),
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
