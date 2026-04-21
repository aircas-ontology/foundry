package com.aircas.ptr.foundry.ontology.model.param;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class XxlJobUpdateParam {

    private String jobGroup;
    private String jobDesc = "";
    private String author = "admin";
    //private String alarmEmail = "";
    private String scheduleType = "CRON";
    private String scheduleConf = "";
    private String cronGen_display = "";
    private String schedule_conf_CRON = "";
    //private String schedule_conf_FIX_RATE = "";
    //private String schedule_conf_FIX_DELAY = "";
    private String executorHandler = "";
    private String executorParam = "";
    private String executorRouteStrategy = "FIRST";
    //private String childJobId = "";
    private String misfireStrategy = "DO_NOTHING";
    private String executorBlockStrategy = "SERIAL_EXECUTION";
    private String executorTimeout = "0";
    private String executorFailRetryCount = "0";
    private String id;

}
