package com.aircas.ptr.foundry.sync.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import tk.mybatis.spring.annotation.MapperScan;

import javax.sql.DataSource;

/**
 * 数据源配置
 *
 * @author yibo.tang
 * @date 2021-04-25 14:05:23
 * @since 1.0
 */
@Configuration
@MapperScan(basePackages = "com.aircas.ptr.foundry.sync.repository.dao", sqlSessionFactoryRef = "pgSqlSessionFactory")
public class DataSouceConfig {

//    @Primary
//    @Bean("datalakeDataSource")
//    @ConfigurationProperties("spring.datasource.datalake")
//    public DataSource createMainDataSource() {
//        return DruidDataSourceBuilder.create().build();
//    }

    //    @Bean(name = "jdbcTemplate")
//    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
//        return new JdbcTemplate(dataSource);
//    }
//    @Bean
//    public SqlSessionFactory sqlSessionFactory(@Qualifier("datalakeDataSource") DataSource dataSource) throws Exception {
//        SqlSessionFactoryBean sessionFactoryBean = new SqlSessionFactoryBean();
//        sessionFactoryBean.setDataSource(dataSource);
//        return sessionFactoryBean.getObject();
//    }

//    @Bean(name = "datalakeDataSourceProperties1")
//    @ConfigurationProperties(prefix = "spring.datalake")
//    public DataSourceProperties datalakeDataSourceProperties() {
//        return new DataSourceProperties();
//    }
//
}
