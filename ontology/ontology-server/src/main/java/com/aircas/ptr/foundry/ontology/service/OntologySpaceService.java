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

    /**
     * 查询空间下的画布内容：全部对象、对象属性、对象间关系。
     *
     * <p>复用 {@link #createSpaceWithCanvasContent} 的请求参数类，前端渲染画布、编辑后可直接回传。</p>
     *
     * @param spaceId 对象空间 id
     * @return 画布内容
     */
    OntologySpaceCanvasCreateParam getSpaceCanvasContent(Integer spaceId);

    void updateSpace(OntologySpaceUpdateParam param);

    /**
     * 查询对象空间/主题列表。
     *
     * @param type 类型：0=空间（默认，按配置文件绑定的空间过滤），1=主题（按创建用户名过滤）；为空按空间处理
     */
    List<OntologySpaceVO> querySpace(Integer type);

    /**
     * 按名称精确查询空间（同时匹配 apiName 和 displayName，OR 关系）。
     *
     * @param name 空间名称（apiName 或 displayName）
     * @return 匹配的空间列表，无匹配时返回空列表
     */
    List<OntologySpaceVO> querySpaceByName(String name);

    OntologySpaceStatisticVO getStatistic(Integer spaceId);

    void deleteSpace(Integer spaceId);

    List<String> importOntologySpace(MultipartFile file);

    /**
     * 将空间数据导入到已存在的目标空间（不创建新空间，覆盖目标空间中同名/同API的对象）。
     *
     * @param targetSpaceId 目标空间ID
     * @param file          导出文件（完整空间结构）
     * @return 导入失败的对象名称列表
     */
    List<String> importOntologySpaceToTarget(Integer targetSpaceId, MultipartFile file);

    /**
     * 导出指定对象空间（含分类树、全部对象 schema，按 exportType 决定是否含实例数据）。
     *
     * @param spaceId    对象空间 id
     * @param exportType 导出类型（SCHEMA=仅结构, INSTANCE=含实例数据）
     * @return 空间导出结构
     */
    OntologySpaceCreateDTO exportOntologySpace(Integer spaceId, OntologyExportTypeEnum exportType);

    /**
     * 基于父空间创建子空间。
     *
     * @param param 子空间创建参数
     * @return 创建结果，包含子空间 id 及对象/关系映射
     */
    OntologySubspaceCreateVO createSubspace(OntologySubspaceCreateParam param);
}
