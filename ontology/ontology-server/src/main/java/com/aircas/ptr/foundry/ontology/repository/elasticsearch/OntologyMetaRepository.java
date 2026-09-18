package com.aircas.ptr.foundry.ontology.repository.elasticsearch;

import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.OntologyMetaDTO;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OntologyMetaRepository extends ElasticsearchRepository<OntologyMetaDTO, Long> {
}
