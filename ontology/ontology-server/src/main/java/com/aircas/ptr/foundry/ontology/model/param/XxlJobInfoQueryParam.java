package com.aircas.ptr.foundry.ontology.model.param;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class XxlJobInfoQueryParam {

    private String jobGroup;
    private String triggerStatus = "-1";
    private String jobDesc;
//    private String executorHandler;
//    private String author;
    private String start = "0";
    private String length = "10";

}
