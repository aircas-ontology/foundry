package com.aircas.ptr.foundry.ontology.common.vo;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
public class BaseSatelliteResp<T> {

    private Boolean success;

    private String code;

    private String msg;

    private T data;
}
