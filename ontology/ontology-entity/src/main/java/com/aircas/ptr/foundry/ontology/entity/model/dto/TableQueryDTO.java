package com.aircas.ptr.foundry.ontology.entity.model.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;

@ApiModel("表查询条件")
public class TableQueryDTO {
    
    @ApiModelProperty("字段查询条件")
    private List<FieldCondition> conditions;
    
    @ApiModel("字段条件")
    public static class FieldCondition {
        @ApiModelProperty("字段名")
        private String fieldName;
        
        @ApiModelProperty("查询值")
        private Object value;
        
        @ApiModelProperty("范围查询的结束值")
        private Object endValue;
        
        @ApiModelProperty("是否精确匹配")
        private boolean exactMatch = true;
        
        @ApiModelProperty("查询类型：EQUAL/LIKE/RANGE")
        private QueryType queryType = QueryType.EQUAL;
        
        // getters and setters
        public String getFieldName() {
            return fieldName;
        }
        
        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }
        
        public Object getValue() {
            return value;
        }
        
        public void setValue(Object value) {
            this.value = value;
        }
        
        public Object getEndValue() {
            return endValue;
        }
        
        public void setEndValue(Object endValue) {
            this.endValue = endValue;
        }
        
        public boolean isExactMatch() {
            return exactMatch;
        }
        
        public void setExactMatch(boolean exactMatch) {
            this.exactMatch = exactMatch;
        }
        
        public QueryType getQueryType() {
            return queryType;
        }
        
        public void setQueryType(QueryType queryType) {
            this.queryType = queryType;
        }
    }
    
    public enum QueryType {
        EQUAL,  // 等于
        LIKE,   // 模糊匹配
        RANGE   // 范围查询
    }
    
    // getters and setters
    public List<FieldCondition> getConditions() {
        return conditions;
    }
    
    public void setConditions(List<FieldCondition> conditions) {
        this.conditions = conditions;
    }
} 