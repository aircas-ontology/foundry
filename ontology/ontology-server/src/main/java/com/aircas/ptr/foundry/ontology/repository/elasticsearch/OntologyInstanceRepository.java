package com.aircas.ptr.foundry.ontology.repository.elasticsearch;

import com.aircas.ptr.foundry.ontology.model.dto.elasticsearch.EsOntologyInstanceDTO;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OntologyInstanceRepository
        extends ElasticsearchRepository<EsOntologyInstanceDTO, String> {

    List<EsOntologyInstanceDTO> findByOntologyUid(String ontologyUid);
}
