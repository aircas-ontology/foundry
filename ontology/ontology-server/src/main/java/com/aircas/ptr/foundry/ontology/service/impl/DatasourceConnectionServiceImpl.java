package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableCommentVO;
import com.aircas.ptr.foundry.ontology.repository.datalakeMapper.DatasourceConnectionMapper;
import com.aircas.ptr.foundry.ontology.service.DatasourceConnectionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Service
@Slf4j
public class DatasourceConnectionServiceImpl
        extends ServiceImpl<DatasourceConnectionMapper, DatasourceConnection>
        implements DatasourceConnectionService {

    private static final String DB_TYPE_POSTGRESQL = "POSTGRESQL";
    private static final String DEFAULT_SCHEMA = "public";

    @Override
    public List<DatasourceConnectionVO> searchByKeyword(String keyword) {
        List<DatasourceConnection> list = baseMapper.searchByKeyword(keyword);
        return list.stream().map(this::toVO).toList();
    }

    @Override
    public List<TableCommentVO> listTableComments(Integer datasourceId) {
        DatasourceConnection conn = baseMapper.selectById(datasourceId);
        if (conn == null) {
            throw new BusinessException("数据源不存在", HttpStatus.NOT_FOUND);
        }
        if (!DB_TYPE_POSTGRESQL.equalsIgnoreCase(conn.getDbType())) {
            throw new BusinessException("当前仅支持 PostgreSQL 数据源", HttpStatus.BAD_REQUEST);
        }
        String jdbcUrl = conn.getJdbcUrl();
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new BusinessException("数据源 JDBC URL 为空", HttpStatus.BAD_REQUEST);
        }
        String schema = (conn.getSchemaName() != null && !conn.getSchemaName().isBlank())
                ? conn.getSchemaName() : DEFAULT_SCHEMA;

        List<TableCommentVO> result = new ArrayList<>();
        Properties props = new Properties();
        if (conn.getUsername() != null) {
            props.setProperty("user", conn.getUsername());
        }
        if (conn.getPassword() != null) {
            props.setProperty("password", conn.getPassword());
        }
        // 开启 remarksReporting，让 PostgreSQL 驱动把 obj_description 表注释填入 REMARKS
        props.setProperty("remarksReporting", "true");
        try (Connection dbConn = DriverManager.getConnection(jdbcUrl, props)) {
            DatabaseMetaData metaData = dbConn.getMetaData();
            try (ResultSet rs = metaData.getTables(null, schema, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    result.add(new TableCommentVO(
                            rs.getString("TABLE_NAME"),
                            rs.getString("REMARKS")));
                }
            }
        } catch (Exception e) {
            log.error("扫描数据源表注释失败，datasourceId={}", datasourceId, e);
            throw new BusinessException("扫描表注释失败: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return result;
    }

    /**
     * PO -> 对外 VO，仅暴露核心字段，主动丢弃密码等敏感信息
     */
    private DatasourceConnectionVO toVO(DatasourceConnection po) {
        return new DatasourceConnectionVO(
                po.getId(),
                po.getName(),
                po.getDbType(),
                po.getHost(),
                po.getPort(),
                po.getDbName(),
                po.getSchemaName(),
                po.getDescription()
        );
    }
}
