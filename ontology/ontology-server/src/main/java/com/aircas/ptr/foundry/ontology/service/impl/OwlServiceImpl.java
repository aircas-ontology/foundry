package com.aircas.ptr.foundry.ontology.service.impl;


import com.aircas.ptr.foundry.model.po.OntologyMeta;
import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.ontology.exception.ExceptionFactory;
import com.aircas.ptr.foundry.ontology.exception.OwlUriInvalidClassNotFoundException;
import com.aircas.ptr.foundry.ontology.exception.OwlUrilInvalidPrimaryKeyNotFoundException;
import com.aircas.ptr.foundry.ontology.service.ObjectService;
import com.aircas.ptr.foundry.ontology.service.OwlService;
import com.aircas.ptr.foundry.ontology.model.vo.ObjectOneInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.PropertyValueVO;
import com.aircas.ptr.foundry.ontology.repository.dao.OntologyMetaMapper;
import lombok.RequiredArgsConstructor;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.util.List;


@Service
@RequiredArgsConstructor
public class OwlServiceImpl implements OwlService {

    @Resource
    OntologyMetaMapper ontologyMetaMapper;

    @Resource
    ObjectService objectService;

    @Override
    public String getClassResource(String ontologyApi) throws OwlUriInvalidClassNotFoundException, OWLOntologyCreationException, OWLOntologyStorageException {

        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByApi(ontologyApi);
        if (ontologyMeta == null) {
            throw ExceptionFactory.getOwlUriInvalidClassNotFoundException(null);
        }
        OWLOntologyManager owlOntologyManager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = owlOntologyManager.createOntology();
        this.injectClasses(owlOntologyManager, ontology, ontologyMeta);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        owlOntologyManager.saveOntology(ontology, outputStream);

        return outputStream.toString();
    }

    @Override
    public String getIndividualResource(String ontologyApi, String primaryKey)
            throws OwlUriInvalidClassNotFoundException, OWLOntologyStorageException, OWLOntologyCreationException, OwlUrilInvalidPrimaryKeyNotFoundException {
        OntologyMeta ontologyMeta = ontologyMetaMapper.selectByApi(ontologyApi);
        if (ontologyMeta == null) {
            throw ExceptionFactory.getOwlUriInvalidClassNotFoundException(null);
        }
        ObjectOneInfoVO objectOneInfoVO = objectService.queryObjectByApiAndPrimaryKey(ontologyApi, primaryKey);
        if(objectOneInfoVO == null || objectOneInfoVO.getProperties().size() == 0) {
            throw ExceptionFactory.getOwlUrilInvalidPrimaryKeyNotFoundException(null);
        }
        OWLOntologyManager owlOntologyManager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = owlOntologyManager.createOntology();
        OWLClass owlClass = this.injectClasses(owlOntologyManager, ontology, ontologyMeta);
        OWLNamedIndividual namedIndividual = this.injectIndividual(owlOntologyManager, ontology, owlClass, ontologyMeta, objectOneInfoVO);

        this.injectProperties(owlOntologyManager, ontology, ontologyMeta, owlClass, namedIndividual, objectOneInfoVO.getProperties());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        owlOntologyManager.saveOntology(ontology, outputStream);

        return outputStream.toString();
    }


    //插入class的metadata，应该有选择性的插入，不应该全部插入
    //包含api, uniqueIdentifier, 名称，主键, URI,描述, 属性？
    // 属性，关系，function?
    private OWLClass injectClasses(OWLOntologyManager owlOntologyManager, OWLOntology ontology, OntologyMeta ontologyMeta) {
        OWLDataFactory factory = owlOntologyManager.getOWLDataFactory();
        OWLClass class1 = factory.getOWLClass(contructOwlClassUri(ontologyMeta.getApiName()));
        owlOntologyManager.addAxiom(ontology, factory.getOWLDeclarationAxiom(class1));
        return class1;
    }

    private String contructOwlClassUri(String ontologyApi) {
        return "http://ontology.iecas.com/class/" + ontologyApi;
    }


    private String contructOwlIndividualUri(String ontologyApi, String primaryKey) {
        return "http://ontology.iecas.com/individual/" + ontologyApi + "/" + primaryKey;
    }

    //插入property，包含：取值，type
    private OWLNamedIndividual injectIndividual(
            OWLOntologyManager owlOntologyManager,
            OWLOntology ontology,
            OWLClass owlClass,
            OntologyMeta ontologyMeta,
            ObjectOneInfoVO objectOneInfoVO) {
        OWLDataFactory factory = owlOntologyManager.getOWLDataFactory();
        OWLNamedIndividual namedIndividual = factory.getOWLNamedIndividual(contructOwlIndividualUri(ontologyMeta.getApiName(), objectOneInfoVO.getPrimaryKey()));
        owlOntologyManager.addAxiom(ontology, factory.getOWLDeclarationAxiom(namedIndividual));

        owlOntologyManager.addAxiom(ontology, factory.getOWLClassAssertionAxiom(owlClass, namedIndividual));
        return namedIndividual;
    }

    //插入property，包含：取值，type
    private void injectProperties(
            OWLOntologyManager owlOntologyManager,
            OWLOntology ontology,
            OntologyMeta ontologyMeta,
            OWLClass owlClass,
            OWLNamedIndividual namedIndividual,
            List<PropertyValueVO> properties) {
        OWLDataFactory factory = owlOntologyManager.getOWLDataFactory();
        for (PropertyValueVO propertyValueVO: properties) {
            String displayName = propertyValueVO.getDisplayName();
            String value = propertyValueVO.getValue().toString();
            String desc = propertyValueVO.getDescription();
            OntologyDataTypeEnum propertyType = propertyValueVO.getPropertyType();

            OWLDataProperty dataProperty = factory.getOWLDataProperty(displayName);
            owlOntologyManager.addAxiom(ontology, factory.getOWLDeclarationAxiom(dataProperty));

            //属性和individual关联
            OWLAxiom dataPropertyAssertionAxiom =  factory.getOWLDataPropertyAssertionAxiom(dataProperty, namedIndividual, value);
            owlOntologyManager.addAxiom(ontology, dataPropertyAssertionAxiom);

            //domain
            OWLAxiom domainAxiom = factory.getOWLDataPropertyDomainAxiom(dataProperty, owlClass);
            owlOntologyManager.addAxiom(ontology, domainAxiom);

            //range
            OWL2Datatype owlDatatype = this.getOwlDataTypeFromPropertyType(propertyType);
            OWLAxiom rangeAxiom = factory.getOWLDataPropertyRangeAxiom(dataProperty, owlDatatype);
            owlOntologyManager.addAxiom(ontology, rangeAxiom);

            //comment, 使用description
            OWLAnnotationProperty labelProperty = factory.getRDFSLabel();
            OWLAnnotation labelAnnotation = factory.getOWLAnnotation(labelProperty, factory.getOWLLiteral(desc));
            OWLAxiom labelAxiom = factory.getOWLAnnotationAssertionAxiom(dataProperty.getIRI(), labelAnnotation);
            owlOntologyManager.addAxiom(ontology, labelAxiom);

        }
    }

    private OWL2Datatype getOwlDataTypeFromPropertyType(OntologyDataTypeEnum propertyType) {
        return propertyType.transfer2Owl();
    }

    //目前只插入一层的link，link的link先不插入
    private void injectLinks() {

    }

    //对function进行插入，但是在function挂载之前先不做。
    private void injectFunctions() {

    }


//    //根据uri获取资源
//    @Override
//    public String getResource(String uri) throws exception {
//        UriComponent uriComponent = resolveURL(uri);
//
//
//        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
//        OWLOntology ontology = manager.createOntology();
//
//        OWLDataFactory factory = manager.getOWLDataFactory();
//        OWLClass class1 = factory.getOWLClass(IRI.create("舰船本体"));
//        OWLNamedIndividual namedIndividual = factory.getOWLNamedIndividual("华盛顿号");
//        OWLAxiom axiom = factory.getOWLClassAssertionAxiom(class1, namedIndividual);
//        manager.addAxiom(ontology, axiom);
//        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//        manager.saveOntology(ontology, outputStream);
//        return outputStream.toString();
//    }

//    //此方法识别以下两种URI
//
//    /**
//     * 以下为类
//     * http://127.0.0.1/ontology/owl/class#xtmb
//     * 以下为individual
//     * htp://127.0.0.1/ontology/owl/xtmb/individual#7
//     */
//
//    private UriComponent resolveURL(String url) throws exception {
//        URI uri = null;
//        try {
//            uri = new URI(url);
//        } catch (URISyntaxException e) {
//            throw ExceptionFactory.getOwlUriInvalidException(e);
//        }
//        String fullPath = uri.getPath();
//        if (fullPath == null) {
//            throw ExceptionFactory.getOwlUriInvalidException(null);
//        }
//        String [] pathComponent = fullPath.split("/");
//        if (pathComponent.length == 0) {
//            throw ExceptionFactory.getOwlUriInvalidException(null);
//        }
//        String api = pathComponent[0];
//        UriComponent component = new UriComponent();
//        component.api = api;
//        if (pathComponent.length >= 2) {
//            component.primaryKey = pathComponent[1];
//        }
//        return component;
//     }
//
//     private  OntologyMeta queryByApi(String api) {
//        return null;
//     }


}
