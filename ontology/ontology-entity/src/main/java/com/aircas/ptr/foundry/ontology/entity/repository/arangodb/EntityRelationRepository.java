package com.aircas.ptr.foundry.ontology.entity.repository.arangodb;

import com.aircas.ptr.foundry.ontology.entity.model.document.EntityNode;
import com.aircas.ptr.foundry.ontology.entity.model.document.EntityRelation;
import com.arangodb.springframework.annotation.Query;
import com.arangodb.springframework.repository.ArangoRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EntityRelationRepository extends ArangoRepository<EntityRelation, String> {

    List<EntityRelation> findByFromIn(List<EntityNode> nodes);

    List<EntityRelation> findByToIn(List<EntityNode> nodes);

    @Query("FOR r IN relation FILTER r._id IN @relationIds REMOVE r IN relation")
    void deleteByIds(@Param("relationIds") List<String> relationIds);

    @Query(" FOR n IN node" +
            "    FILTER n.tableName == @tableName AND n.primaryKey == @primaryKeyValue" +
            "    LET nodeId = n._id" +
            "    FOR edge IN relation" +
            "        FILTER edge._from == nodeId OR edge._to == nodeId" +
            "        RETURN edge ")
    List<EntityRelation> queryRelationByTableNameAndPrimaryKey(@Param("tableName") String tableName,
                                                               @Param("primaryKeyValue") Object primaryKeyValue);


}