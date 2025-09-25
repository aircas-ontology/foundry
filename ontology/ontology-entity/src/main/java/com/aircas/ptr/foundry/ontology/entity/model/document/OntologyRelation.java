package com.aircas.ptr.foundry.ontology.entity.model.document;

import com.arangodb.springframework.annotation.Document;
import com.arangodb.springframework.annotation.Edge;
import com.arangodb.springframework.annotation.From;
import com.arangodb.springframework.annotation.To;
import org.springframework.data.annotation.Id;

import java.util.Map;

@Edge("relations")
@Document("relations")
public class OntologyRelation {
    @Id
    private String _id;
    
    @From
    private String _from;
    
    @To
    private String _to;
    
    private String _key;
    private String type;
    private Double weight;
    private Map<String, Object> properties;
    private Long createTime;
    private Long updateTime;
    private String description;
    private String source;
    private Double confidence;
    private String status;
    
    public OntologyRelation() {}
    
    public OntologyRelation(String _from, String _to, String type) {
        this._from = _from;
        this._to = _to;
        this.type = type;
    }
    
    // getter和setter方法
    public String get_id() {
        return _id;
    }
    
    public void set_id(String _id) {
        this._id = _id;
    }
    
    public String get_from() {
        return _from;
    }
    
    public void set_from(String _from) {
        this._from = _from;
    }
    
    public String get_to() {
        return _to;
    }
    
    public void set_to(String _to) {
        this._to = _to;
    }
    
    public String get_key() {
        return _key;
    }
    
    public void set_key(String _key) {
        this._key = _key;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public Double getWeight() {
        return weight;
    }
    
    public void setWeight(Double weight) {
        this.weight = weight;
    }
    
    public Map<String, Object> getProperties() {
        return properties;
    }
    
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
    
    public Long getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }
    
    public Long getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    public Double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
} 