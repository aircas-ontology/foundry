package com.aircas.ptr.foundry.ontology.entity.repository.arangodb;

import com.aircas.ptr.foundry.ontology.entity.model.document.EntityNode;
import com.arangodb.springframework.annotation.Query;
import com.arangodb.springframework.repository.ArangoRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EntityNodeRepository extends ArangoRepository<EntityNode, String> {

    List<EntityNode> findByTableName(String tableName);

    @Query("FOR n IN node FILTER n._id IN @nodeIds REMOVE n IN node")
    void deleteByIds(@Param("nodeIds") List<String> nodeIds);
}