package com.aircas.ptr.foundry.ontology.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobProperties {

    private Admin admin;

    private String accessToken;

    private Executor executor;

    private String cookie;

    @Data
    public static class Admin {

        private String addresses;

        private String queryJobInfoUrl;

        private String queryJobExecutorUrl;

        private String addJobUrl;

        private String updateJobUrl;

        private String removeJobUrl;

        private String startJobUrl;

        private String stopJobUrl;

    }

    @Data
    public static class Executor {
        private String appname;
        private String address;
        private String ip;
        private int port;
        private String logPath;
        private int logRetentionDays;
    }


}
