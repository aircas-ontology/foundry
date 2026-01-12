package com.aircas.ptr.foundry.ontology.config;

import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class OrekitDataConfig {

    @Value("${orekit.data.path}")
    private String path;

    @Bean("dataProvidersManager")
    public DataProvidersManager dataProvidersManager() {
        DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
        File orekitData = new File(path);

        // 确保路径存在
        if (orekitData.exists()) {
            manager.addProvider(new DirectoryCrawler(orekitData));
        } else {
            throw new RuntimeException("orekit-data resource directory not found: " + path);
        }
        return manager;
    }
}
