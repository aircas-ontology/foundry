package com.aircas.ptr.foundry.ontology.entity.model.dto;

import java.util.List;


public class TableCreateDTO {
    private String tableName;
    private String tableComment;
    private List<FieldDTO> fields;
    
    public static class FieldDTO {
        private String fieldName;
        private String fieldType;
        private String fieldComment;
        private boolean isPrimaryKey;
        private boolean isNullable;
        
        // getters and setters
        public String getFieldName() {
            return fieldName;
        }
        
        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }
        
        public String getFieldType() {
            return fieldType;
        }
        
        public void setFieldType(String fieldType) {
            this.fieldType = fieldType;
        }
        
        public String getFieldComment() {
            return fieldComment;
        }
        
        public void setFieldComment(String fieldComment) {
            this.fieldComment = fieldComment;
        }
        
        public boolean isPrimaryKey() {
            return isPrimaryKey;
        }
        
        public void setPrimaryKey(boolean primaryKey) {
            isPrimaryKey = primaryKey;
        }
        
        public boolean isNullable() {
            return isNullable;
        }
        
        public void setNullable(boolean nullable) {
            isNullable = nullable;
        }
    }
    
    // getters and setters
    public String getTableName() {
        return tableName;
    }
    
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
    
    public String getTableComment() {
        return tableComment;
    }
    
    public void setTableComment(String tableComment) {
        this.tableComment = tableComment;
    }
    
    public List<FieldDTO> getFields() {
        return fields;
    }
    
    public void setFields(List<FieldDTO> fields) {
        this.fields = fields;
    }
} 