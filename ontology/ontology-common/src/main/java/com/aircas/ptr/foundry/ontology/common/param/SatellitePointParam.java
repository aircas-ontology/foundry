package com.aircas.ptr.foundry.ontology.common.param;


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
public class SatellitePointParam {

    /**
     * format: 2025-10-23 18:02:13.986
     */
    private String startTime;

    /**
     * format: 2025-10-23 18:02:13.986
     */
    private String endTime;

    /**
     * seconds
     */
    private Integer interval;

    private String tle1;

    private String tle2;

    /**
     * SGP4/TwoBody
     */
    private String type;

}
