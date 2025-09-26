package com.aircas.ptr.foundry.ontology.entity.model.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RowDTO {

    private String columnName;

    private Object columnValue;
}
