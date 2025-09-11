package com.aircas.ptr.foundry.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PostgresDataTypeEnum {

    Bigint("bigint"),
    Boolean("boolean"),
    Smallint("smallint"),
    Integer("integer"),
    Real("real"),
    Double("double precision"),
    Numeric("numeric"),
    Decimal("decimal"),
    Text("text"),
    Varchar("character varying"),
    Date("date"),
    Time("time without time zone"),
    Timestamp ("timestamp without time zone"),
    ;

    private final String value;
}
