package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.ontology.application.service.ObjectService;
import com.aircas.ptr.foundry.ontology.application.service.OntologyService;
import com.aircas.ptr.foundry.ontology.application.service.OwlService;
import com.aircas.ptr.foundry.ontology.entity.vo.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Api(tags = "以OWL格式返回本体")
@RestController
@RequestMapping("/owl")
public class OwlController {

    @Resource
    OwlService owlService;

    @GetMapping("/getResource")
    public ResponseEntity<String> queryObjectByPrimaryKey(@RequestParam String uri) throws OWLOntologyCreationException, OWLOntologyStorageException {
        String owlXml = owlService.getResource(uri);
        return new ResponseEntity<String>(owlXml, HttpStatus.OK);
    }
}
