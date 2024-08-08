package com.aircas.ptr.foundry.ontology.entity.vo;

import com.aircas.ptr.foundry.ontology.entity.bo.OntologyChildLinkBo;
import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.*;


@Data
public class OntologyLinkGroupVO {

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
    @JsonFormat(pattern = DATE_FORMAT2)
    private Date createTime;

    /**
     * 记录修改时间
     */
    @JSONField(format = DATE_FORMAT2)
    private Date updateTime;

    /**
     * 开始本体unique identifier
     */
    private String ontologyUniqueIdentifierFrom;

    /**
     * 开始本体名称
     */
    private String ontologyNameFrom;

    /**
     * 开始本体icon
     */
    private String ontologyIconFrom;

    /**
     * 结束本体id
     */
    private String ontologyUniqueIdentifierTo;

    /**
     * 结束本体名称
     */
    private String ontologyNameTo;

    /**
     * 开始本体icon
     */
    private String ontologyIconTO;

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

    private OntologyChildLinkVO forwardLink;

    private OntologyChildLinkVO backwardLink;

}
