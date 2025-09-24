package com.aircas.ptr.foundry.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;


@Data
@Entity
@TableName("ontology_action_mapping_in")
public class OntologyActionMappingIn {
    /**
     * Column: id
     */
    @Id
    @Column(name = "id")
    private Long id;

    /**
     * Column: ontology_function_id
     * Remark: function的名称
     */
    private Long ontologyActionId;

    /**
     * Column: parameter_name
     * Remark: 参数的名字
     */
    private String parameterName;

    /**
     * Column: property_unique_identifier
     * Remark: 属性的唯一标识，如果为this, 则表示当前本体对象。
     */
    private String propertyUniqueIdentifier;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}