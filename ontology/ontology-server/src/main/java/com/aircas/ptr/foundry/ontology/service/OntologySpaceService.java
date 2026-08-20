package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceVO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface OntologySpaceService extends IService<OntologySpace> {

    Integer createSpace(OntologySpaceCreateParam param);

    void updateSpace(OntologySpaceUpdateParam param);

    List<OntologySpaceVO> querySpace();

    void deleteSpace(Integer spaceId);

    List<String> importOntologySpace(MultipartFile file);
}
