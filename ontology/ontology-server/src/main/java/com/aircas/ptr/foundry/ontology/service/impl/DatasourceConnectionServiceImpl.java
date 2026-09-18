package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionVO;
import com.aircas.ptr.foundry.ontology.repository.mainMapper.DatasourceConnectionMapper;
import com.aircas.ptr.foundry.ontology.service.DatasourceConnectionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DatasourceConnectionServiceImpl
        extends ServiceImpl<DatasourceConnectionMapper, DatasourceConnection>
        implements DatasourceConnectionService {

    /**
     * 启用状态：1 启用，0 禁用（对应 DDL 中 status 字段）
     */
    private static final Integer STATUS_ENABLED = 1;

    @Override
    public List<DatasourceConnectionVO> listEnabled() {
        List<DatasourceConnection> list = list(new LambdaQueryWrapper<DatasourceConnection>()
                .eq(DatasourceConnection::getStatus, STATUS_ENABLED)
                .orderByDesc(DatasourceConnection::getUpdateTime));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * PO -> VO，主动丢弃 password 字段，避免敏感信息外泄
     */
    private DatasourceConnectionVO toVO(DatasourceConnection po) {
        return DatasourceConnectionVO.builder()
                .id(po.getId())
                .name(po.getName())
                .dbType(po.getDbType())
                .driverClass(po.getDriverClass())
                .jdbcUrl(po.getJdbcUrl())
                .host(po.getHost())
                .port(po.getPort())
                .dbName(po.getDbName())
                .schemaName(po.getSchemaName())
                .username(po.getUsername())
                .description(po.getDescription())
                .status(po.getStatus())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }
}
