package com.aircas.ptr.foundry.ontology.model.bo;


import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.util.Date;

@Data
public class OntologyLinkGroupBo {

    /**
     * 主键自增
     */
    private Long id;

    /**
     * link的unique identifier
     */
    private String uniqueIdentifier;

    /**
     * link的名称
     */
    private String name;

    /**
     * 软删除状态位，1有效，0无效
     */
    private Integer status;

    /**
     * 记录创建时间
     */
    private Date createTime;

    /**
     * 记录修改时间
     */
    private Date updateTime;

    /**
     * 开始本体unique identifier
     */
    private String ontologyUniqueIdentifierFrom;

    /**
     * 结束本体id
     */
    private String ontologyUniqueIdentifierTo;

    /**
     * 开始本体的某个属性，作为连接键
     */
    private String propertyUniqueIdentifierFrom;

    /**
     * 结束本体的某个属性，作为连接键
     */
    private String propertyUniqueIdentifierTo;

    /**
     * 实验状态，1激活、2测试中、3废弃
     */
    private Integer experimentalStatus;

    /**
     * 1: 1对1
     * 2: 1对多
     * 3: 多对1
     * 4: 多对多
     */

    private Integer mapping;

    private OntologyChildLinkBo forwardLink;

    private OntologyChildLinkBo backwardLink;

    public OntologyLinkGroupBo normalized() {
        if (ontologyUniqueIdentifierFrom.toUpperCase().compareTo(ontologyUniqueIdentifierTo.toUpperCase()) >= 0) {
            return this;
        } else {
            return revertForwardToBackward();
        }
    }

    private OntologyLinkGroupBo revertForwardToBackward() {
        OntologyLinkGroupBo ontologyLinkGroupBo = new OntologyLinkGroupBo();
        BeanUtils.copyProperties(this, ontologyLinkGroupBo);
        ontologyLinkGroupBo.forwardLink = this.backwardLink;
        ontologyLinkGroupBo.backwardLink = this.forwardLink;
        ontologyLinkGroupBo.ontologyUniqueIdentifierFrom = this.ontologyUniqueIdentifierTo;
        ontologyLinkGroupBo.ontologyUniqueIdentifierTo = this.ontologyUniqueIdentifierFrom;
        ontologyLinkGroupBo.propertyUniqueIdentifierFrom = this.propertyUniqueIdentifierTo;
        ontologyLinkGroupBo.propertyUniqueIdentifierTo = this.propertyUniqueIdentifierFrom;
        ontologyLinkGroupBo.mapping = revertMapping(this.mapping);
        return ontologyLinkGroupBo;
    }

    private static Integer revertMapping (int mapping) {
        switch (mapping) {
            case 1: return 1;
            case 2: return 3;
            case 3: return 2;
            case 4: return 4;
        }
        return 0;
    }
}
