package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCanvasCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceUpdateParam;
import com.aircas.ptr.foundry.ontology.model.dto.OntologySpaceCreateDTO;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceCanvasCreateVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceStatisticVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceVO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface OntologySpaceService extends IService<OntologySpace> {

    Integer createSpace(OntologySpaceCreateParam param);

    OntologySpaceCanvasCreateVO createSpaceWithCanvasContent(OntologySpaceCanvasCreateParam param);

    void updateSpace(OntologySpaceUpdateParam param);

    List<OntologySpaceVO> querySpace();

    OntologySpaceStatisticVO getStatistic(Integer spaceId);

    void deleteSpace(Integer spaceId);

    List<String> importOntologySpace(MultipartFile file);

    /**
     * 导出指定本体空间（含分类树、全部本体 schema 与实例数据），结构对齐空间导入模板。
     *
     * @param spaceId 本体空间 id
     * @return 空间导出结构
     */
    OntologySpaceCreateDTO exportOntologySpace(Integer spaceId);
}
