package com.aircas.ptr.foundry.ontology.entity.model.dto;

import java.util.List;

public class BatchImportDTO {
    private List<NodeDTO> nodes;
    private List<RelationDTO> relations;
    
    public static class NodeDTO {
        private String name;
        private String description;
        
        // getters and setters
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
    }
    
    public static class RelationDTO {
        private String fromNodeName;
        private String toNodeName;
        private String relationType;
        private String description;
        
        // getters and setters
        public String getFromNodeName() {
            return fromNodeName;
        }
        
        public void setFromNodeName(String fromNodeName) {
            this.fromNodeName = fromNodeName;
        }
        
        public String getToNodeName() {
            return toNodeName;
        }
        
        public void setToNodeName(String toNodeName) {
            this.toNodeName = toNodeName;
        }
        
        public String getRelationType() {
            return relationType;
        }
        
        public void setRelationType(String relationType) {
            this.relationType = relationType;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
    }
    
    // getters and setters for main class
    public List<NodeDTO> getNodes() {
        return nodes;
    }
    
    public void setNodes(List<NodeDTO> nodes) {
        this.nodes = nodes;
    }
    
    public List<RelationDTO> getRelations() {
        return relations;
    }
    
    public void setRelations(List<RelationDTO> relations) {
        this.relations = relations;
    }
} 