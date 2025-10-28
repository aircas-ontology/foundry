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
public class SatellitePointVO  {

    private String time;

    private Double x;

    private Double y;

    private Double z;

    private Double vx;

    private Double vy;

    private Double vz;
}
