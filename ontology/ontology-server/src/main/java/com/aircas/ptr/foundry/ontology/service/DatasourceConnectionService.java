package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableCommentVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 数据源连接配置服务
 */
public interface DatasourceConnectionService extends IService<DatasourceConnection> {

    /**
     * 按关键词查询启用中（status=1）的数据源连接配置。
     *
     * @param keyword 搜索关键词，可为空（空则返回全部启用数据源）；按名称 / 数据库类型模糊匹配
     * @return 数据源列表（仅暴露 Agent 所需核心字段，不含密码等敏感信息）
     */
    List<DatasourceConnectionVO> searchByKeyword(String keyword);

    /**
     * 扫描指定数据源的表注释（通过 JDBC DatabaseMetaData 实时获取，仅支持 PostgreSQL）。
     *
     * @param datasourceId datasource_connection 表主键
     * @return 表名 + 表注释列表
     */
    List<TableCommentVO> listTableComments(Integer datasourceId);
}
