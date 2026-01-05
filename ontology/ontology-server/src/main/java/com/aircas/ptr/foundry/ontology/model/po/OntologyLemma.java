package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.common.constant.OntologyLemmaTypeEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

@Builder
@Accessors(chain = true)
@Data
@TableName("ontology_lemma")
public class OntologyLemma {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String ontologyUniqueIdentifier;

    private String title;

    private String content;

    private Integer parentId;

    private OntologyLemmaTypeEnum type;

    private Integer orderIndex;

    private String extraInfo;

    private Date createTime;

    private Date updateTime;
}
