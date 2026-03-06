package com.aircas.ptr.foundry.ontology.model.param;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class XxlJobExecutorQueryParam {

    private String appname;

    private String title = "";

    private String start = "0";

    private String length = "10";

}
