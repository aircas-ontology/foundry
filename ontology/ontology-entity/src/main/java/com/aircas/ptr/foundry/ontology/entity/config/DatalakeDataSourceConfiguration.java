package com.aircas.ptr.foundry.ontology.entity.config;


import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
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
@MapperScan(basePackages = "com.aircas.ptr.foundry.ontology.entity.repository.mapper.datalake", sqlSessionFactoryRef = "datalakeSqlSessionFactory")
public class DatalakeDataSourceConfiguration {


    @Bean("datalakeDataSource")
    @ConfigurationProperties("spring.datasource.datalake")
    public DataSource createDatalakeDataSource() {
        return DruidDataSourceBuilder.create().build();
    }


    @Bean("datalakeSqlSessionFactory")
    public SqlSessionFactory createDatalakeSqlSessionFactory(@Qualifier("datalakeDataSource") DataSource dataSource) throws Exception {

        MybatisSqlSessionFactoryBean sessionFactoryBean = new MybatisSqlSessionFactoryBean();
        sessionFactoryBean.setDataSource(dataSource);
        String resourcePath = "classpath:mapper/datalake/*.xml";
        sessionFactoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(resourcePath));

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(StdOutImpl.class);
        sessionFactoryBean.setConfiguration(configuration);

        return sessionFactoryBean.getObject();
    }

//    @Bean(name = "datalakeTransactionManager")
//    public DataSourceTransactionManager datalakeTransactionManager(@Qualifier("datalakeDataSource") DataSource dataSource) {
//        return new DataSourceTransactionManager(dataSource);
//    }
}
