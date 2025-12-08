package com.aircas.ptr.foundry.ontology.config;


import com.aircas.ptr.foundry.ontology.repository.handler.OnInsertUpdateHandler;
import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.PaginationInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tk.mybatis.spring.annotation.MapperScan;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@MapperScan(basePackages = "com.aircas.ptr.foundry.ontology.repository.mainMapper", sqlSessionFactoryRef = "mainSqlSessionFactory")
public class MainDataSourceConfiguration {

    @Primary
    @Bean("mainDataSource")
    @ConfigurationProperties("spring.datasource.main")
    public DataSource createMainDataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Primary
    @Bean("mainSqlSessionFactory")
    public SqlSessionFactory createMainSqlSessionFactory(@Qualifier("mainDataSource")DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean sessionFactoryBean = new MybatisSqlSessionFactoryBean();
        sessionFactoryBean.setDataSource(dataSource);
        String resourcePath = "classpath:mybatis-mapper/main/*.xml";
        sessionFactoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(resourcePath));

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(StdOutImpl.class);
        sessionFactoryBean.setConfiguration(configuration);

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setMetaObjectHandler(new OnInsertUpdateHandler());
        sessionFactoryBean.setGlobalConfig(globalConfig);

        // 添加分页拦截器
        PaginationInterceptor paginationInterceptor = new PaginationInterceptor();
        sessionFactoryBean.setPlugins(paginationInterceptor);

        return sessionFactoryBean.getObject();
    }

    @Primary
    @Bean(name = "mainTransactionManager")
    public PlatformTransactionManager mainTransactionManager(@Qualifier("mainDataSource")DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
