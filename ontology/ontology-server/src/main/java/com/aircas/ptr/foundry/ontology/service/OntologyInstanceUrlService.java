package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.param.OntologyInstanceUrlSaveParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyInstanceUrl;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyInstanceUrlVO;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * 领域空间实例 URL 服务
 */
public interface OntologyInstanceUrlService extends IService<OntologyInstanceUrl> {

    /**
     * 保存实例 URL。
     *
     * <p>按（空间 + 对象 + 实例）唯一：已存在则覆盖 url，并把存入时间刷成本次写入时间，
     * 一个实例始终只保留一条记录。主键 id 与存入时间都不取自请求。</p>
     *
     * @param param 保存入参
     * @return 落库后的记录
     */
    OntologyInstanceUrlVO saveInstanceUrl(OntologyInstanceUrlSaveParam param);

    /**
     * 查询实例 URL，按存入时间倒序。
     *
     * @param spaceId    对象空间 id，必填
     * @param objectId   对象 id（unique_identifier），可为空（空则查该空间下全部对象）
     * @param instanceId 实例 id，可为空（空则查该对象下全部实例；非空时 objectId 一并生效）
     * @return 实例 URL 列表
     */
    List<OntologyInstanceUrlVO> listInstanceUrls(Integer spaceId, String objectId, String instanceId);
}
