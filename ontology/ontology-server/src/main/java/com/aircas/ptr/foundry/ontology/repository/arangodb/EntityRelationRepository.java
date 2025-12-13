package com.aircas.ptr.foundry.ontology.repository.arangodb;

import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.ontology.model.document.EntityNode;
import com.aircas.ptr.foundry.ontology.model.document.EntityRelation;
import com.arangodb.springframework.annotation.Query;
import com.arangodb.springframework.repository.ArangoRepository;
import com.google.common.collect.Lists;
import lombok.var;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface EntityRelationRepository extends ArangoRepository<EntityRelation, String> {

    List<EntityRelation> findByFrom(List<EntityNode> nodes);

    List<EntityRelation> findByTo(List<EntityNode> nodes);

    @Query("FOR r IN relation FILTER r._id IN @relationIds REMOVE r IN relation")
    void deleteByIds(@Param("relationIds") List<String> relationIds);

    void deleteByOntologyLinkId(@Param("ontologyLinkId") String ontologyLinkId);


    @Query("   LET adjustedTime = DATE_NOW()" +
            "   FOR n IN node" +
            "                FILTER n.ontologyUniqIdentifier == @ontologyUniqueIdentifier AND n.primaryKey ==@entityPrimaryKey " +
            "                LET nodeId = n._id " +
            "               FOR edge IN relation " +
            "                   FILTER (edge._from == nodeId OR edge._to == nodeId) AND (( DATE_TIMESTAMP(edge.startTime) < adjustedTime AND adjustedTime < DATE_TIMESTAMP(edge.endTime) ) OR edge.status == 'ENABLE' ) " +
            "                   RETURN edge ")
    List<EntityRelation> queryEnableRelationsByEntity(@Param("ontologyUniqueIdentifier") String ontologyUniqueIdentifier,
                                                      @Param("entityPrimaryKey") Object entityPrimaryKey);


    @Query(
            "LET fromNode = (FOR n IN node" +
                    "                FILTER n.ontologyUniqIdentifier == @fromOntologyUniqueIdentifier AND n.primaryKey == @fromEntityPrimaryKey " +
                    "                RETURN n._id)[0]" +
                    "LET toNode = (FOR n IN node" +
                    "              FILTER n.ontologyUniqIdentifier == @toOntologyUniqueIdentifier AND n.primaryKey == @toEntityPrimaryKey" +
                    "              RETURN n._id)[0]" +
                    "FOR edge IN relation" +
                    "    FILTER edge._from == fromNode AND edge._to == toNode AND edge.ontologyLinkId == @linkId " +
                    "    RETURN edge")
    EntityRelation queryRelationByFromNodeAndToNode(@Param("fromOntologyUniqueIdentifier") String fromOntologyUniqueIdentifier,
                                                    @Param("fromEntityPrimaryKey") Object fromEntityPrimaryKey,
                                                    @Param("toOntologyUniqueIdentifier") String toOntologyUniqueIdentifier,
                                                    @Param("toEntityPrimaryKey") Object toEntityPrimaryKey,
                                                    @Param("linkId") String linkId);


    @Query("FOR r IN relation FILTER r._key == @id UPDATE r WITH { startTime: @startTime, endTime: @endTime, status: @status } IN relation")
    void updateRelation(@Param("startTime") Date startTime,
                        @Param("endTime") Date endTime,
                        @Param("status") Status status,
                        @Param("id") String id);


    default void batchSave(List<EntityRelation> nodes) {
        var partition = Lists.partition(nodes, 1000);
        partition.forEach(p -> saveAll(p));
    }
}