package com.aircas.ptr.foundry.sync.common;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class DataSourceConfig {
    @Value("${spring.datalake.url}")
    private String postgresDatalakeUrl;

    @Value("${spring.datalake.username}")
    private String postgresDatalakeUserName;

    @Value("${spring.datalake.password}")
    private String postgresDatalakePassword;

    @Value("${postgres.slot.name}")
    private String postgresSlotName;

    @Value("${postgres.table.schema}")
    private String postgresTableSchema;

    @Value("${postgres.table.prefix}")
    private String postgresTablePrefix;
}
