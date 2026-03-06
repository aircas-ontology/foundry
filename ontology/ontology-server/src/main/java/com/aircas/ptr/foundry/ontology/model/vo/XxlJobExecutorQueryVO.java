package com.aircas.ptr.foundry.ontology.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class XxlJobExecutorQueryVO {

    private Integer recordsFiltered;

    private Integer recordsTotal;

    private List<ExecutorData> data;


    @Data
    @Builder
    @Accessors(chain = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutorData {

        private Integer id;

        private String appname;

        private String title;
    }
}
