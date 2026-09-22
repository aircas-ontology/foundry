package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.enums.QuerySortEnum;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyOrderByEnum;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyCreateDTO;
import com.aircas.ptr.foundry.ontology.model.dto.OntologySpaceExportData;
import com.aircas.ptr.foundry.ontology.model.param.OntologyMetaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupMetaVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaNodeVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaStatisticVO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;



public interface OntologyMetaService extends IService<OntologyMeta> {

    String createOntology(OntologyMetaCreateParam ontologyCreateParam);

    List<OntologyGroupMetaVO> getByGroupId(String groupId, OntologyOrderByEnum orderBy, QuerySortEnum sort);

    OntologyMetaInfoVO getMetaByUniqueIdentifier(String uniqueIdentifier);

    OntologyMetaStatisticVO getStatistic(String uniqueIdentifier);

    void deleteOntology(String uniqueIdentifier);

    void updateMeta(OntologyUpdateParam updateParam);

    List<OntologyMetaInfoVO> searchByKeyword(String keyword);

    /**
     * 列出指定本体空间下已存在的本体元数据（仅含启用中 status=1 的记录）。
     *
     * <p>供 Agent 在「功能四 对象关系推导」中拉取同空间的已有对象作为候选目标，
     * 与 {@link #searchByKeyword(String)} 的区别在于本方法按 spaceId 硬隔离，不接受关键字。</p>
     *
     * @param spaceId 本体空间 id（ontology_space 主键）
     * @return 空间内已存在的本体列表，无则返回空集合
     */
    List<OntologyMetaInfoVO> listBySpaceId(Integer spaceId);

    List<OntologyMetaInfoVO> getByCategoryId(Integer categoryId);

    List<OntologyMetaNodeVO> getOntologyTreeByByGroupId(String groupId);


    List<String> importOntologies(MultipartFile file);

    /**
     * 导出单个本体（含 schema 与实例数据），结构对齐导入模板。
     *
     * <p>关系端点可能指向同空间其它本体，displayName 解析覆盖该本体所属空间的全部启用本体；
     * 不附带函数定义（函数为空间级资源，随空间导出给出）。</p>
     *
     * @param uniqueIdentifier 本体唯一标识
     * @return 单元素本体导出列表
     */
    List<OntologyCreateDTO> exportOntology(String uniqueIdentifier);

    /**
     * 一次装配导出指定空间的全部本体与空间级函数。
     *
     * <p>供空间导出使用：本体装配与函数收集共享同一次 space/metas 加载与函数详情缓存，
     * 避免重复查询。函数为空间级资源，按空间内本体 action 引用的 functionApi 去重收集。</p>
     *
     * @param spaceId 本体空间 id
     * @return 本体与函数的导出承载结构，无本体时两者均为空列表
     */
    OntologySpaceExportData exportOntologiesWithFunctions(Integer spaceId);
}
