package com.aircas.ptr.foundry.model.po;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;


@Data
public class OntologyFunctionMappingIn {
    /**
     * Column: id
     */
    private Long id;

    /**
     * Column: ontology_function_id
     * Remark: function的名称
     */
    private Long ontologyFunctionId;

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

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date createTime;

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}