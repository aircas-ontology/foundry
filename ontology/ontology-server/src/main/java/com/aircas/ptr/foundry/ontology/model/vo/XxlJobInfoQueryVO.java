package com.aircas.ptr.foundry.ontology.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author LiuYue
 * @date 2026/4/21
 * @description
 */
@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class XxlJobInfoQueryVO {

    private Integer recordsFiltered;

    private Integer recordsTotal;

    private List<XxlJobInfoQueryVO.JobInfo> data;


    @Data
    @Builder
    @Accessors(chain = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JobInfo {

        private Integer id;

        private String jobDesc;

        private Integer jobGroup;
    }
}
