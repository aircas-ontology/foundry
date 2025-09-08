package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.ontology.service.OwlService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Api(tags = "OWL")
@RestController
@RequestMapping("/owl")
public class OwlController {

    @Autowired
    private HttpServletRequest request;

    @Resource
    OwlService owlService;

    @GetMapping("/class/{ontologyApi}")
    public ResponseEntity<String> getClassResource(@PathVariable String ontologyApi) throws Exception {
        String owlXml = owlService.getClassResource(ontologyApi);
        return new ResponseEntity(owlXml, HttpStatus.OK);
    }

    @GetMapping("/individual/{ontologyApi}/{primaryKey}")
    public ResponseEntity<String> getResource(@PathVariable String ontologyApi,@PathVariable String primaryKey) throws Exception {
        String owlXml = owlService.getIndividualResource(ontologyApi, primaryKey);
        return new ResponseEntity(owlXml, HttpStatus.OK);
    }
}
