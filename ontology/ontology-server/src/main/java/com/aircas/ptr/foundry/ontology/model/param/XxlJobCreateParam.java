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
public class XxlJobCreateParam {

    private String jobGroup="";
    private String jobDesc="";
    private String author="";
    private String alarmEmail="";
    private String scheduleType="";
    private String scheduleConf="";
    private String cronGen_display="";
    private String schedule_conf_CRON="";
    private String schedule_conf_FIX_RATE="";
    private String schedule_conf_FIX_DELAY="";
    private String glueType="";
    private String executorHandler="";
    private String executorParam="";
    private String executorRouteStrategy="";
    private String childJobId="";
    private String misfireStrategy="";
    private String executorBlockStrategy="";
    private String executorTimeout="";
    private String executorFailRetryCount="";
    private String glueRemark="";
    private String glueSource="";

}
