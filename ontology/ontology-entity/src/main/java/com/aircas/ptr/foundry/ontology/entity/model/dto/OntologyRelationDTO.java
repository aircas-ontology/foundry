package com.aircas.ptr.foundry.ontology.entity.model.dto;


import com.aircas.ptr.foundry.ontology.entity.entity.OntologyRelation;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OntologyRelationDTO {
    private  String id;
    private String key;
    private String from;
    private String to;
    private String type;
    private String description;
    private Double confidence;
    private Double weight;
    private String status;
    private String source;
    private String createTime;  // ISO 8601格式的日期时间字符串
    private String updateTime;  // ISO 8601格式的日期时间字符串
    private Map<String, Object> properties;
    
    /**
     * 转换为实体类
     */
    public OntologyRelation toEntity() {
        OntologyRelation relation = new OntologyRelation();
        
        // 设置关键字段
        relation.set_key(this.key);
        
        // 确保from和to字段包含正确的集合前缀
        if (this.from != null) {
            if (!this.from.startsWith("nodes/") && !this.from.contains("/")) {
                relation.set_from("nodes/" + this.from);
            } else {
                relation.set_from(this.from);
            }
        }
        
        if (this.to != null) {
            if (!this.to.startsWith("nodes/") && !this.to.contains("/")) {
                relation.set_to("nodes/" + this.to);
            } else {
                relation.set_to(this.to);
            }
        }
        
        // 设置其他字段
        relation.set_id(this.id);
        relation.setType(this.type);
        relation.setDescription(this.description);
        relation.setConfidence(this.confidence);
        relation.setWeight(this.weight);
        relation.setStatus(this.status);
        relation.setSource(this.source);
        relation.setProperties(this.properties);
        
        // 处理创建时间
        if (this.createTime != null && !this.createTime.isEmpty()) {
            try {
                // 尝试解析ISO 8601格式的日期时间字符串
                ZonedDateTime dateTime = ZonedDateTime.parse(this.createTime);
                relation.setCreateTime(dateTime.toInstant().toEpochMilli());
            } catch (DateTimeParseException e) {
                // 如果解析失败，使用当前时间
                relation.setCreateTime(System.currentTimeMillis());
            }
        } else {
            relation.setCreateTime(System.currentTimeMillis());
        }
        
        // 处理更新时间
        if (this.updateTime != null && !this.updateTime.isEmpty()) {
            try {
                ZonedDateTime dateTime = ZonedDateTime.parse(this.updateTime);
                relation.setUpdateTime(dateTime.toInstant().toEpochMilli());
            } catch (DateTimeParseException e) {
                relation.setUpdateTime(System.currentTimeMillis());
            }
        } else {
            relation.setUpdateTime(System.currentTimeMillis());
        }
        
        return relation;
    }
} 