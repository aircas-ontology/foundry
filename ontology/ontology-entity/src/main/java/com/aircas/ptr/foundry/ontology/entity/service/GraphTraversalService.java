package com.aircas.ptr.foundry.ontology.entity.service;

import com.arangodb.springframework.core.ArangoOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class GraphTraversalService {
    @Autowired
    private ArangoOperations arangoOperations;
    
    public List<Map> traverseGraph(String startNodeId, int maxDepth, String direction) {
        String query = "FOR v, e, p IN 1..@maxDepth " +
                      (direction.equals("OUTBOUND") ? "OUTBOUND" : 
                       direction.equals("INBOUND") ? "INBOUND" : "ANY") +
                      " @startVertex relations " +
                      "RETURN {node: v, edge: e, path: p}";
        
        Map<String, Object> bindVars = new HashMap<>();
        bindVars.put("startVertex", "nodes/" + startNodeId);
        bindVars.put("maxDepth", maxDepth);
        
        return arangoOperations.query(query, bindVars, null, Map.class).asListRemaining();
    }
    
    public List<Map> findShortestPath(String startNodeId, String endNodeId) {
        String query = "FOR path IN SHORTEST_PATH " +
                      "@startVertex TO @endVertex relations " +
                      "RETURN path";
        
        Map<String, Object> bindVars = new HashMap<>();
        bindVars.put("startVertex", "nodes/" + startNodeId);
        bindVars.put("endVertex", "nodes/" + endNodeId);
        
        return arangoOperations.query(query, bindVars, null, Map.class).asListRemaining();
    }
} 