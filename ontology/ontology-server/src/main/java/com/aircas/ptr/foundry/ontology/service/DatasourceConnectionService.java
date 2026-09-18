package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.po.DatasourceConnection;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 数据源连接配置服务
 */
public interface DatasourceConnectionService extends IService<DatasourceConnection> {

    /**
     * 查询所有启用中的数据源连接配置
     *
     * @return 启用状态（status=1）的数据源列表
     */
    List<DatasourceConnectionVO> listEnabled();
}
