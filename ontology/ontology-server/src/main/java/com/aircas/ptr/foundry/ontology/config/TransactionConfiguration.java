package com.aircas.ptr.foundry.ontology.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.transaction.ChainedTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class TransactionConfiguration {

    @Bean(name = "chainedTransactionManager")
    public PlatformTransactionManager chainedTransactionManager(
            @Qualifier("mainTransactionManager") PlatformTransactionManager mainTransactionManager,
            @Qualifier("datalakeTransactionManager") PlatformTransactionManager datalakeTransactionManager) {
        return new ChainedTransactionManager(mainTransactionManager, datalakeTransactionManager);
    }
}
