package com.aircas.ptr.foundry.sync.config;


import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import tk.mybatis.spring.annotation.MapperScan;

import javax.sql.DataSource;

@Configuration
@MapperScan(basePackages = "com.aircas.ptr.foundry.sync.mapper", sqlSessionFactoryRef = "datalakeSqlSessionFactory")
public class DatalakeDataSourceConfiguration {

    @Primary
    @Bean("datalakeDataSource")
    @ConfigurationProperties("spring.datalake")
    public DataSource createMainDataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Primary
    @Bean("datalakeSqlSessionFactory")
    public SqlSessionFactory createMainSqlSessionFactory(@Qualifier("datalakeDataSource")DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
        sqlSessionFactoryBean.setDataSource(dataSource);
        String resourcePath = "classpath:mybatis/*.xml";
        sqlSessionFactoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(resourcePath));
        return sqlSessionFactoryBean.getObject();
    }

    @Bean(name = "datalakeTransactionManager")
    public DataSourceTransactionManager mainTransactionManager(@Qualifier("datalakeDataSource")DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

//    @Bean(name = "datalakeDataSourceProperties")
////    @ConfigurationProperties(prefix = "spring.datalake")
//    public DataSourceProperties datalakeDataSourceProperties() {
//        return new DataSourceProperties();
//    }
}
