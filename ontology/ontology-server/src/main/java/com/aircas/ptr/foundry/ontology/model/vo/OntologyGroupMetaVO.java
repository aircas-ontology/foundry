package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "本体分组搜索返回参数")
public class OntologyGroupMetaVO {

    @Schema(name = "groupName", description = "分组名称", example = "远海远域")
    private String groupName;

    @Schema(name = "groupId", description = "分组id", example = "fafd-fdasgf-ewgffds")
    private String groupId;

    private List<OntologyMetaInfoVO> metaVOS;
}
