package com.aircas.ptr.foundry.ontology.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * 在主库开启独立短事务。调用方在进入这里之前完成远程扫描和脚本校验，
 * 避免网络等待占用数据库事务与连接。
 */
@Service
public class MainTransactionExecutor {

    @Transactional(transactionManager = "mainTransactionManager", rollbackFor = Exception.class)
    public <T> T execute(Supplier<T> action) {
        return action.get();
    }
}
