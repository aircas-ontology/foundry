package com.aircas.ptr.foundry.ontology.entity.config;

import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.springframework.annotation.EnableArangoRepositories;
import com.arangodb.springframework.config.ArangoConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableArangoRepositories(basePackages = {"com.aircas.ptr.foundry.ontology.entity.repository.arangodb"})
public class ArangoConfig implements ArangoConfiguration {

    @Value("${spring.data.arangodb.host}")
    private String host;

    @Value("${spring.data.arangodb.port}")
    private Integer port;

    @Value("${spring.data.arangodb.user}")
    private String user;

    @Value("${spring.data.arangodb.password}")
    private String password;

    @Value("${spring.data.arangodb.connections.max:8}")
    private Integer maxConnections;

    @Value("${spring.data.arangodb.timeout.connect:5000}")
    private Integer connectTimeout;

    @Value("${spring.data.arangodb.timeout.request:10000}")
    private Long requestTimeout;

    @Value("${spring.data.arangodb.database}")
    private String database;

    @Value("${spring.data.arangodb.collections}")
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
                .useProtocol(com.arangodb.Protocol.HTTP_JSON) // 使用HTTP协议，更稳定
                .keepAliveInterval(1000); // 保持连接活跃
    }

    @Override
    public String database() {
        return database;
    }

    @PostConstruct
    public void initCollections() {
        ArangoDatabase db = arango().build().db(database());
        List<String> collectionList = Arrays.stream(collections.split(",")).collect(Collectors.toList());
        if (!db.exists()) {
            arango().build().createDatabase(database);
        }
        collectionList.forEach(v -> {
            if (!db.collection(v).exists()) {
                db.createCollection(v);
            }
        });

    }
} 