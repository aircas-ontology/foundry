package com.aircas.ptr.foundry.ontology.entity.config;


import com.aircas.ptr.foundry.ontology.entity.repository.handler.OnInsertUpdateHandler;
import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
@MapperScan(basePackages = "com.aircas.ptr.foundry.ontology.entity.repository.mapper.main", sqlSessionFactoryRef = "mainSqlSessionFactory")
public class MainDataSourceConfiguration {

    @Primary
    @Bean("mainDataSource")
    @ConfigurationProperties("spring.datasource.main")
    public DataSource createMainDataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Primary
    @Bean("mainSqlSessionFactory")
    public SqlSessionFactory createMainSqlSessionFactory(@Qualifier("mainDataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean sessionFactoryBean = new MybatisSqlSessionFactoryBean();
        sessionFactoryBean.setDataSource(dataSource);
        String resourcePath = "classpath:mapper/main/*.xml";
        sessionFactoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(resourcePath));

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(StdOutImpl.class);
        sessionFactoryBean.setConfiguration(configuration);

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMetaObjectHandler(new OnInsertUpdateHandler());
        sessionFactoryBean.setGlobalConfig(globalConfig);

        return sessionFactoryBean.getObject();
    }

    @Bean(name = "mainTransactionManager")
    public DataSourceTransactionManager mainTransactionManager(@Qualifier("mainDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
