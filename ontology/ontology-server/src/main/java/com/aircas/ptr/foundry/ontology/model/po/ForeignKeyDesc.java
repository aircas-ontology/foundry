package com.aircas.ptr.foundry.ontology.model.po;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 数据湖表上一个单列声明外键。
 */
@Data
@Builder
@Accessors(chain = true)
public class ForeignKeyDesc implements Serializable {

    private String childColumn;

    private String parentTable;

    private String parentColumn;
}
