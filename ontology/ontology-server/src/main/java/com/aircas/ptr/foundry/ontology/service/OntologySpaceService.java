package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyExportTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCanvasCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceUpdateParam;
import com.aircas.ptr.foundry.ontology.model.dto.OntologySpaceCreateDTO;
import com.aircas.ptr.foundry.ontology.model.param.OntologySubspaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceCanvasCreateVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceStatisticVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySubspaceCreateVO;
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
     * 导出指定本体空间（含分类树、全部本体 schema，按 exportType 决定是否含实例数据）。
     *
     * @param spaceId    本体空间 id
     * @param exportType 导出类型（SCHEMA=仅结构, INSTANCE=含实例数据）
     * @return 空间导出结构
     */
    OntologySpaceCreateDTO exportOntologySpace(Integer spaceId, OntologyExportTypeEnum exportType);

    /**
     * 基于父空间创建子空间。
     *
     * @param param 子空间创建参数
     * @return 创建结果，包含子空间 id 及本体/关系映射
     */
    OntologySubspaceCreateVO createSubspace(OntologySubspaceCreateParam param);
}
