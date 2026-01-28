package com.aircas.ptr.foundry.ontology.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Accessors(chain = true)
public class BaseSatelliteVO<T> {

    private Boolean success;

    private String code;

    private String msg;

    private T data;
}
