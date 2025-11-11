package com.aircas.ptr.foundry.ontology.repository.arangodb;

import com.aircas.ptr.foundry.ontology.model.document.EntityNode;
import com.aircas.ptr.foundry.ontology.model.document.EntityRelation;
import com.arangodb.springframework.annotation.Query;
import com.arangodb.springframework.repository.ArangoRepository;
import com.google.common.collect.Lists;
import lombok.var;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EntityRelationRepository extends ArangoRepository<EntityRelation, String> {

    List<EntityRelation> findByFrom(List<EntityNode> nodes);

    List<EntityRelation> findByTo(List<EntityNode> nodes);

    @Query("FOR r IN relation FILTER r._id IN @relationIds REMOVE r IN relation")
    void deleteByIds(@Param("relationIds") List<String> relationIds);

    void deleteByOntologyLinkId(@Param("ontologyLinkId") String ontologyLinkId);


    @Query(" FOR n IN node" +
            "    FILTER n.ontologyUniqIdentifier == @ontologyUniqueIdentifier AND n.primaryKey == @entityPrimaryKey" +
            "    LET nodeId = n._id" +
            "    FOR edge IN relation" +
            "        FILTER (edge._from == nodeId OR edge._to == nodeId) AND edge.status == 'ENABLE'" +
            "        RETURN edge ")
    List<EntityRelation> queryEnableRelationsByEntity(@Param("ontologyUniqueIdentifier") String ontologyUniqueIdentifier, @Param("entityPrimaryKey") Object entityPrimaryKey);


    default void batchSave(List<EntityRelation> nodes) {
        var partition = Lists.partition(nodes, 1000);
        partition.forEach(p -> saveAll(p));
    }
}