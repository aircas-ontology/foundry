package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@ApiModel(description = "本体分组搜索返回参数")
public class OntologyGroupMetaVO {

    @ApiModelProperty(name = "groupName", value = "分组名称", example = "远海远域")
    private String groupName;

    @ApiModelProperty(name = "groupId", value = "分组id", example = "fafd-fdasgf-ewgffds")
    private String groupId;

    private List<OntologyMetaInfoVO> metaVOS;
}
