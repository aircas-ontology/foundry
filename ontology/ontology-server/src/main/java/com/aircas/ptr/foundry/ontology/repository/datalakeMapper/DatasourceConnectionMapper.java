package com.aircas.ptr.foundry.ontology.repository.datalakeMapper;

import com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据源连接配置 Mapper（datalake 数据源）。
 *
 * <p>位于 {@code datalakeMapper} 包，由 {@code datalakeSqlSessionFactory} 扫描，绑定数据湖库
 * {@code entity_datasource}（默认 public schema）。自定义查询 SQL 见
 * {@code resources/mybatis-mapper/datalake/DatasourceConnectionMapper.xml}。</p>
 */
@Mapper
public interface DatasourceConnectionMapper extends BaseMapper<DatasourceConnection> {

    /**
     * 按关键词查询启用中（status=1）的数据源，名称 / 数据库类型模糊匹配，按更新时间倒序。
     *
     * @param keyword 搜索关键词，可为空（空则返回全部启用数据源）
     * @return 数据源连接配置列表
     */
    List<DatasourceConnection> searchByKeyword(@Param("keyword") String keyword);
}
