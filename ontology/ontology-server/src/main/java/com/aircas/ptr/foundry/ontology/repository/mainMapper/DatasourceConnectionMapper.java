package com.aircas.ptr.foundry.ontology.repository.mainMapper;

import com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据源连接配置 Mapper（主库数据源）。
 *
 * <p>位于 {@code mainMapper} 包，由 {@code mainSqlSessionFactory} 扫描，绑定主库
 * {@code postgres} 库的 {@code ontology} schema（JDBC URL 上 {@code currentSchema=ontology}，
 * 故 SQL 中一律使用不带 schema 前缀的表名）。自定义查询 SQL 见
 * {@code resources/mybatis-mapper/main/DatasourceConnectionMapper.xml}。</p>
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
