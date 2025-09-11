package com.aircas.ptr.foundry.ontology.model.param;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @className: EntityNodeParam
 * @author: yangj
 * @date: 2025/4/10 12:10
 * @version: 1.0
 * @description: 实体节点参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EntityNodeParam {

    private String category;
    private String createBy;
    private String createTime;
    private String description;
    private String id;
    private Boolean isDeleted;
    private String key;
    private String name;
    private String properties;
    private String remarks;
    private String source;
    private String status;
    private String type;
    private String updateBy;
    private String updateTime;
    private Integer version;

    public EntityNodeParam(String id, String name, String description, String category, String type) {
        String nowStr = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()).replace(" ", "T");
        this.category = category;
        this.createBy = "ontology";
        this.createTime = nowStr;
        this.description = description;
        this.id = id;
        this.isDeleted = false;
        this.key = id;
        this.name = name;
        this.properties = null;
        this.remarks = type;
        this.source = category;
        this.status = "active";
        this.type = type;
        this.updateBy = "ontology";
        this.updateTime = nowStr;
        this.version = 1;
    }
}
