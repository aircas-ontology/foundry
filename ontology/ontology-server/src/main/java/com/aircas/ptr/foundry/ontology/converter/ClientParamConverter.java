package com.aircas.ptr.foundry.ontology.converter;

import com.aircas.ptr.foundry.ontology.client.param.DataSourceColumnParam;
import com.aircas.ptr.foundry.ontology.client.param.DataSourceParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyDatasourceParam;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;

import java.util.stream.Collectors;

public class ClientParamConverter {

    public static DataSourceParam convert(OntologyDatasourceParam param) {

        if (param == null || CollectionUtils.isEmpty(param.getColumnParamList())) {
            return null;
        }

        var columnParamList = param.getColumnParamList().stream().map(v -> DataSourceColumnParam.builder()
                .columnName(v.getApiName())
                .columnType(v.getColumnType().getValue())
                .description(v.getDescription())
                .isAssociateKey(v.getIsAssociateKey())
                .isPrimaryKey(v.getIsPrimaryKey())
                .primaryDataSourceKey(v.getPrimaryDataSourceKey())
                .tableName(v.getTableName())
                .build())
                .collect(Collectors.toList());

        return DataSourceParam.builder()
                .columnParamList(columnParamList)
                .build();
    }
}
