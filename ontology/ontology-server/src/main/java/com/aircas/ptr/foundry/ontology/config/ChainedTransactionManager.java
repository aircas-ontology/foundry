package com.aircas.ptr.foundry.ontology.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 自定义 ChainedTransactionManager，替代 Spring Data 3.x 中已移除的实现。
 * <p>
 * 按顺序在多个事务管理器上开启事务，提交时按正序提交，回滚时按逆序回滚。
 * 注意：这是一个简化实现，不支持分布式事务的完全语义（如 XA），
 * 但在大多数场景下可以满足多数据源事务管理的需求。
 */
@Slf4j
public class ChainedTransactionManager implements PlatformTransactionManager {

    private final List<PlatformTransactionManager> transactionManagers;

    public ChainedTransactionManager(PlatformTransactionManager... transactionManagers) {
        this.transactionManagers = new ArrayList<>(Arrays.asList(transactionManagers));
    }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException {
        // 使用第一个事务管理器作为主事务，其余作为附属事务
        List<TransactionStatus> statuses = new ArrayList<>();
        for (PlatformTransactionManager tm : transactionManagers) {
            TransactionStatus status = tm.getTransaction(definition);
            statuses.add(status);
        }
        return new ChainedTransactionStatus(statuses);
    }

    @Override
    public void commit(TransactionStatus status) throws TransactionException {
        ChainedTransactionStatus chainedStatus = (ChainedTransactionStatus) status;
        List<TransactionStatus> statuses = chainedStatus.getStatuses();

        // 逆序提交：先提交"未激活"共享 TransactionSynchronizationManager 同步的事务管理器，
        // 会清空共享同步状态，导致后续提交抛 "Tran        // 最后再提交激活了同步的那个（通常是首个，即 main）。否则首个提交后其 cleanupAfterCompletionsaction synchronization is not active"。
        for (int i = statuses.size() - 1; i >= 0; i--) {
            TransactionStatus ts = statuses.get(i);
            try {
                transactionManagers.get(i).commit(ts);
            } catch (TransactionException e) {
                log.error("提交第{}个事务管理器失败，开始回滚尚未提交的事务", i, e);
                // 仅回滚本轮尚未提交（索引 < i）的事务；已提交部分受限于链式语义无法回滚
                for (int j = i - 1; j >= 0; j--) {
                    try {
                        transactionManagers.get(j).rollback(statuses.get(j));
                    } catch (TransactionException rollbackEx) {
                        log.error("回滚第{}个事务管理器也失败", j, rollbackEx);
                    }
                }
                throw e;
            }
        }
    }

    @Override
    public void rollback(TransactionStatus status) throws TransactionException {
        ChainedTransactionStatus chainedStatus = (ChainedTransactionStatus) status;
        List<TransactionStatus> statuses = chainedStatus.getStatuses();

        // 按逆序回滚所有事务
        for (int i = statuses.size() - 1; i >= 0; i--) {
            try {
                transactionManagers.get(i).rollback(statuses.get(i));
            } catch (TransactionException e) {
                log.error("回滚第{}个事务管理器失败", i, e);
                // 继续回滚剩余事务
            }
        }
    }

    /**
     * 组合多个 TransactionStatus 的包装类
     */
    private static class ChainedTransactionStatus implements TransactionStatus {
        private final List<TransactionStatus> statuses;

        ChainedTransactionStatus(List<TransactionStatus> statuses) {
            this.statuses = statuses;
        }

        List<TransactionStatus> getStatuses() {
            return statuses;
        }

        @Override
        public Object createSavepoint() throws TransactionException {
            return null;
        }

        @Override
        public void rollbackToSavepoint(Object savepoint) throws TransactionException {
        }

        @Override
        public void releaseSavepoint(Object savepoint) throws TransactionException {
        }

        @Override
        public boolean isNewTransaction() {
            return statuses.stream().anyMatch(TransactionStatus::isNewTransaction);
        }

        @Override
        public boolean hasSavepoint() {
            return false;
        }

        @Override
        public void setRollbackOnly() {
            statuses.forEach(TransactionStatus::setRollbackOnly);
        }

        @Override
        public boolean isRollbackOnly() {
            return statuses.stream().anyMatch(TransactionStatus::isRollbackOnly);
        }

        @Override
        public boolean isCompleted() {
            return statuses.stream().allMatch(TransactionStatus::isCompleted);
        }
    }
}
