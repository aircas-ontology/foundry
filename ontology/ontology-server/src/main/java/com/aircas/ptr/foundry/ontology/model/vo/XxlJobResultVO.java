package com.aircas.ptr.foundry.ontology.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class XxlJobResultVO {

    private Integer code;

    private String msg;

    private String content;
}
