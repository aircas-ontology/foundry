package com.aircas.ptr.foundry.ontology.repository.arangodb;

import com.aircas.ptr.foundry.ontology.model.document.EntityNode;
import com.arangodb.springframework.annotation.Query;
import com.arangodb.springframework.repository.ArangoRepository;
import com.google.common.collect.Lists;
import lombok.var;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EntityNodeRepository extends ArangoRepository<EntityNode, String> {

    @Query("FOR n IN node" +
            "    FILTER n.ontologyUniqIdentifier == @ontologyUniqIdentifier" +
            "    LIMIT 1" +
            "    RETURN n")
    EntityNode findOneByOntologyUniqIdentifier(@Param("ontologyUniqIdentifier") String ontologyUniqIdentifier);


    EntityNode findByOntologyUniqIdentifierAndPrimaryKey(String ontologyUniqIdentifier,
                                                         Object primaryKey);

    List<EntityNode> findByTableName(String tableName);

    List<EntityNode> findByOntologyUniqIdentifier(String ontologyUniqIdentifier);

    @Query("FOR n IN node FILTER n._id IN @nodeIds REMOVE n IN node")
    void deleteByIds(@Param("nodeIds") List<String> nodeIds);

    default void batchSave(List<EntityNode> nodes) {
        var partition = Lists.partition(nodes, 1000);
        partition.forEach(p -> saveAll(p));
    }



    @Query("FOR n IN node " +
            "    FILTER n.ontologyUniqIdentifier == @ontologyUniqIdentifier " +
            "    UPDATE n WITH { displayName: TO_STRING(n.primaryKey) } IN node")
    void updateDisplayNameEqualPrimaryKey(@Param("ontologyUniqIdentifier") String ontologyUniqIdentifier);

}