package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "本体关系sum")
public class OntologyLinkCountVO {

    @ApiModelProperty(value = "本体1的名称", example = "轨迹")
    private String ontologyName1;
    @ApiModelProperty(value = "本体2的uid", example = "deb6c235-4b0b-4dff-95c0-7e2a594b24d8")
    private String ontologyId1;

    @ApiModelProperty(value = "本体2的名称", example = "舰船")
    private String ontologyName2;
    @ApiModelProperty(value = "本体2的uid", example = "545649a4-8bba-4d0c-b265-362e95fd4ffb")
    private String ontologyId2;

    @ApiModelProperty(value = "本体间链接数量", example = "2")
    private Integer linkCount;

    @Override
    public boolean equals(Object obj) {

        if (!(obj instanceof OntologyLinkCountVO)) {
            return false;
        }
        OntologyLinkCountVO other = (OntologyLinkCountVO) obj;
        if (this.ontologyId1.equals(other.getOntologyId1()) && this.ontologyId2.equals(other.getOntologyId2())) {
            return true;
        }
        if (this.ontologyId1.equals(other.getOntologyId2()) && this.ontologyId2.equals(other.getOntologyId1())) {
            return true;
        }
        return false;
    }
}
