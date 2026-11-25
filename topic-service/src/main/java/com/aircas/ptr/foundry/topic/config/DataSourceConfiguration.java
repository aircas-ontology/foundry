package com.aircas.ptr.foundry.topic.config;

import com.aircas.ptr.foundry.topic.repository.handler.OnInsertUpdateHandler;
import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceBuilder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * 单数据源。简单 CRUD 用 MyBatis-Plus {@code BaseMapper}。
 * 需要自定义 SQL 时，把 XML 放到 {@code classpath:mybatis-mapper/main/}，
 * 并在 {@code sqlSessionFactory} 上设置 {@code mapperLocations}。
 */
@Configuration
@EnableTransactionManagement
@EnableConfigurationProperties(DataSourceProperties.class)
@MapperScan(basePackages = "com.aircas.ptr.foundry.topic.repository", sqlSessionFactoryRef = "sqlSessionFactory")
public class DataSourceConfiguration {

    @Primary
    @Bean("dataSource")
    @ConfigurationProperties("spring.datasource.main")
    public DataSource dataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Primary
    @Bean("sqlSessionFactory")
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean sessionFactoryBean = new MybatisSqlSessionFactoryBean();
        sessionFactoryBean.setDataSource(dataSource);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        sessionFactoryBean.setConfiguration(configuration);

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMetaObjectHandler(new OnInsertUpdateHandler());
        sessionFactoryBean.setGlobalConfig(globalConfig);

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        sessionFactoryBean.setPlugins(interceptor);

        return sessionFactoryBean.getObject();
    }

    @Primary
    @Bean("transactionManager")
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
