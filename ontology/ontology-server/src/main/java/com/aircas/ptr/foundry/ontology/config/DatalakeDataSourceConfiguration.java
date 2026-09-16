package com.aircas.ptr.foundry.ontology.config;


import com.aircas.ptr.foundry.ontology.repository.handler.OnInsertUpdateHandler;
import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceBuilder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@MapperScan(basePackages = "com.aircas.ptr.foundry.ontology.repository.datalakeMapper", sqlSessionFactoryRef = "datalakeSqlSessionFactory")
public class DatalakeDataSourceConfiguration {

    @Primary
    @Bean("datalakeDataSource")
    @ConfigurationProperties("spring.datasource.datalake")
    public DataSource createMainDataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Primary
    @Bean("datalakeSqlSessionFactory")
    public SqlSessionFactory createMainSqlSessionFactory(@Qualifier("datalakeDataSource") DataSource dataSource) throws Exception {

        MybatisSqlSessionFactoryBean sessionFactoryBean = new MybatisSqlSessionFactoryBean();
        sessionFactoryBean.setDataSource(dataSource);
        String resourcePath = "classpath:mybatis-mapper/datalake/*.xml";
        sessionFactoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(resourcePath));

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setLogImpl(StdOutImpl.class);
        //configuration.setLogImpl(NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        sessionFactoryBean.setConfiguration(configuration);

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMetaObjectHandler(new OnInsertUpdateHandler());
        sessionFactoryBean.setGlobalConfig(globalConfig);

        // 添加分页插件
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        sessionFactoryBean.setPlugins(interceptor);

        return sessionFactoryBean.getObject();
    }

    @Bean(name = "datalakeTransactionManager")
    public PlatformTransactionManager mainTransactionManager(@Qualifier("datalakeDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
