package com.aircas.ptr.foundry.ontology.repository.elasticsearch;

import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologySpaceDTO;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OntologySpaceRepository
        extends ElasticsearchRepository<EsOntologySpaceDTO, Long> {
}
