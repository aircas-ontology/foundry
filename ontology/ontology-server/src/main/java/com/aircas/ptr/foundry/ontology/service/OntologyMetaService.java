package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyExportTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.QuerySortEnum;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyOrderByEnum;
import com.aircas.ptr.foundry.ontology.model.dto.OntologyCreateDTO;
import com.aircas.ptr.foundry.ontology.model.param.OntologyAssetBuildParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyBatchDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyMetaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologySpace;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupMetaVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyImportResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaBriefVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaNodeVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaStatisticVO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;



public interface OntologyMetaService extends IService<OntologyMeta> {

    String createOntology(OntologyMetaCreateParam ontologyCreateParam);

    /**
     * 资产构建对象：先创建对象元数据，再在该对象下逐条创建属性，全程一个事务，任一步失败整单回滚。
     *
     * <p>与 {@link #createOntology(OntologyMetaCreateParam)} 的区别仅在于属性随请求一起落库，
     * 免去前端「建对象拿 id → 再建属性」的两次调用。校验口径完全复用单个属性新增接口
     * （{@code POST /property}），属性 apiName 重复、存储分组与对象 apiName 同名等问题同样报错回滚。</p>
     *
     * <p>分类兜底与画布落库一致：对象 categoryId 不传时挂到本空间对象分类根节点「全部」，
     * 属性 categoryId 不传时挂到该对象属性分类根节点「全部」。</p>
     *
     * <p>属性可带 metadata，服务端会从所有属性的 metadata 自动推导并创建元数据 schema（单根树，
     * 枚举值不推断），然后再创建属性。落库时序为「对象 → schema → 属性」。若同一字段在不同属性的
     * metadata 里既是叶子又是分支（结构冲突），整单回滚。</p>
     *
     * @param param 对象信息 + 属性列表
     * @return 新建对象的 uniqueIdentifier
     */
    String createOntologyWithProperties(OntologyAssetBuildParam param);

    List<OntologyGroupMetaVO> getByGroupId(String groupId, OntologyOrderByEnum orderBy, QuerySortEnum sort);

    OntologyMetaInfoVO getMetaByUniqueIdentifier(String uniqueIdentifier);

    OntologyMetaStatisticVO getStatistic(String uniqueIdentifier);

    void deleteOntology(String uniqueIdentifier);

    /**
     * 批量删除对象及其所有关联数据（属性、关系、分类树、行为等）。
     *
     * @param param 包含 spaceId 和 ontologyIdentifiers 列表
     */
    void batchDeleteOntologies(OntologyBatchDeleteParam param);

    void updateMeta(OntologyUpdateParam updateParam);

    List<OntologyMetaInfoVO> searchByKeyword(String keyword, Integer spaceId);

    /**
     * 列出指定对象空间下已存在的对象元数据（仅含启用中 status=1 的记录）。
     *
     * <p>供 Agent 在「功能四 对象关系推导」中拉取同空间的已有对象作为候选目标，
     * 与 {@link #searchByKeyword(String,Integer)} 的区别在于本方法按 spaceId 硬隔离，不接受关键字。</p>
     *
     * @param spaceId 对象空间 id（ontology_space 主键）
     * @return 空间内已存在的对象列表，无则返回空集合
     */
    List<OntologyMetaInfoVO> listBySpaceId(Integer spaceId);

    /**
     * 按空间名称 + 对象名称精确查询对象。
     *
     * <p>先按 spaceName（匹配 apiName 或 displayName）定位空间，
     * 再在该空间内按 objectName（匹配 apiName 或 displayName）精确查找对象。</p>
     *
     * @param spaceName  空间名称（apiName 或 displayName）
     * @param objectName 对象名称（apiName 或 displayName）
     * @return 匹配的对象列表，无匹配时返回空列表
     */
    List<OntologyMetaInfoVO> queryMetaBySpaceAndName(String spaceName, String objectName);

    /**
     * 根据本地对象id（ontology_meta 主键）查询对象简要信息。
     *
     * @param id 本地对象id
     * @return 含 id、对象名称、空间名称、uniqueIdentifier 的简要信息，对象不存在则返回 null
     */
    OntologyMetaBriefVO getMetaById(Long id);

    List<OntologyMetaInfoVO> getByCategoryId(Integer categoryId);

    List<OntologyMetaNodeVO> getOntologyTreeByByGroupId(String groupId);

    /**
     * 查询指定空间的对象继承层级树。
     *
     * <p>基于 {@code parentUniqueIdentifier} 字段构建树形结构，返回所有根节点（无父对象的节点）及其子孙。</p>
     *
     * @param spaceId 对象空间 id
     * @return 继承层级树的根节点列表
     */
    List<OntologyMetaNodeVO> getInheritanceTree(Integer spaceId);


    /**
     * 批量导入对象。
     *
     * <p>与 {@link #importOntologiesToSpace} 的覆盖式导入相反：目标空间已有同名对象时不删除、不报错，
     * 而是在原名后追加 {@code _1}（仍冲突则递增）后另建一个新对象。</p>
     *
     * <p>关系不在对象导入当场创建，全部对象建完后统一创建：端点优先解析为本 JSON 内的新对象，
     * 只有一端在本 JSON 内时另一端回目标空间查找，另一端找不到则跳过该关系。</p>
     *
     * @param file          导出或手写的对象 JSON
     * @param targetSpaceId 目标空间 ID，为空时按 {@code metadata.ontologySpaceName} 解析空间
     * @return 导入失败名单与本次改名映射
     */
    OntologyImportResultVO importOntologies(MultipartFile file, Integer targetSpaceId);

    /**
     * 从 JSON 字符串导入对象。
     *
     * @param jsonContent JSON 字符串，格式与导出模板一致
     * @return 导入失败的对象 displayName 列表
     */
    List<String> importOntologiesFromJson(String jsonContent);

    /**
     * 导入对象到指定空间（不验证 displayName 唯一性，自动使用目标空间 ID）。
     *
     * @param targetSpaceId 目标空间 ID
     * @param file          导出的 JSON 文件
     * @return 导入失败的对象 displayName 列表
     */
    List<String> importOntologiesToSpace(Integer targetSpaceId, MultipartFile file);

    /**
     * 从 JSON 字符串导入对象到指定空间。
     *
     * @param targetSpaceId 目标空间 ID
     * @param jsonContent   JSON 字符串，格式与导出模板一致
     * @return 导入失败的对象 displayName 列表
     */
    List<String> importOntologiesToSpaceFromJson(Integer targetSpaceId, String jsonContent);

    /**
     * 导出对象（含 schema、对象级关系，可选实例数据），结构对齐导入模板。
     *
     * <p>关系端点可能指向同空间其它对象，displayName 解析覆盖对象所属空间的全部启用对象；
     * 不附带函数定义（函数为空间级资源，随空间导出给出）。</p>
     *
     * @param uniqueIdentifiers 对象唯一标识，多个用逗号分隔；须属于同一空间，不存在或已停用的跳过（全部取不到才报错）
     * @param exportType        导出类型（SCHEMA=仅结构, INSTANCE=含实例数据）
     * @return 对象导出列表，顺序与入参一致；relations 段带出对象关联的关系，端点以 displayName 表达，
     * 两端都在本次导出列表内的关系只挂在首个涉及它的对象下
     */
    List<OntologyCreateDTO> exportOntology(String uniqueIdentifiers, OntologyExportTypeEnum exportType);

    /**
     * 导出指定空间的全部对象（含 schema，按 exportType 决定是否含实例数据）。
     *
     * @param spaceId    对象空间 id
     * @param exportType 导出类型
     * @return 对象导出列表，无对象时返回空列表
     */
    List<OntologyCreateDTO> exportOntologies(Integer spaceId, OntologyExportTypeEnum exportType);

    /**
     * 空间已加载时的重载：供空间导出复用已查出的 space，避免重复 getById。
     *
     * @param space      对象空间实体
     * @param exportType 导出类型
     * @return 对象导出列表
     */
    List<OntologyCreateDTO> exportOntologies(OntologySpace space, OntologyExportTypeEnum exportType);
}
