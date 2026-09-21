package com.aircas.ptr.foundry.ontology.config;

import com.arangodb.ArangoDB;
import com.arangodb.springframework.annotation.EnableArangoRepositories;
import com.arangodb.springframework.config.ArangoConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableArangoRepositories(basePackages = {"com.aircas.ptr.foundry.ontology.repository.arangodb"})
public class ArangoConfig implements ArangoConfiguration {

    @Value("${spring.datasource.arangodb.host}")
    private String host;

    @Value("${spring.datasource.arangodb.port}")
    private Integer port;

    @Value("${spring.datasource.arangodb.user}")
    private String user;

    @Value("${spring.datasource.arangodb.password}")
    private String password;

    @Value("${spring.datasource.arangodb.connections.max:8}")
    private Integer maxConnections;

    @Value("${spring.datasource.arangodb.timeout.connect:5000}")
    private Integer connectTimeout;

    @Value("${spring.datasource.arangodb.timeout.request:10000}")
    private Long requestTimeout;

    @Value("${spring.datasource.arangodb.database}")
    private String database;

    @Value("${spring.datasource.arangodb.collections}")
    private String collections;

    @Override
    public ArangoDB.Builder arango() {
        return new ArangoDB.Builder()
                .host(host, port)
                .user(user)
                .password(password)
                .maxConnections(maxConnections)
                .timeout(connectTimeout)
                .connectionTtl(requestTimeout)
                .protocol(com.arangodb.Protocol.HTTP_JSON)
                .keepAliveInterval(1000);
    }

    @Override
    public String database() {
        return database;
    }

} 