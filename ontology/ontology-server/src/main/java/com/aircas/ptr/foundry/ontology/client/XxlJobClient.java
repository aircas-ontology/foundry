package com.aircas.ptr.foundry.ontology.client;

import com.aircas.ptr.foundry.common.util.HttpUtil;
import com.aircas.ptr.foundry.ontology.config.XxlJobProperties;
import com.aircas.ptr.foundry.ontology.model.common.Constants;
import com.aircas.ptr.foundry.ontology.model.param.OntologyActionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.XxlJobCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.XxlJobExecutorQueryParam;
import com.aircas.ptr.foundry.ontology.model.vo.XxlJobExecutorQueryVO;
import com.aircas.ptr.foundry.ontology.model.vo.XxlJobResultVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class XxlJobClient {

    @Resource
    private XxlJobProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();


    public void createJobInfo(String jobDesc, String scheduleConf, OntologyActionExecuteParam executorParam) throws Exception {

        var executorQueryParam = new XxlJobExecutorQueryParam().setAppname(properties.getExecutor().getAppname());
        var queryJobExecutorUrl = properties.getAdmin().getQueryJobExecutor();
        Map<String, String> executorQueryFormDataMap = objectMapper.convertValue(executorQueryParam, Map.class);

        var vo = HttpUtil.postFormData(queryJobExecutorUrl, new HashMap<>(), executorQueryFormDataMap, new TypeReference<XxlJobExecutorQueryVO>() {
        });
        var jobGroup = vo.getData().get(0).getId();

        var param = new XxlJobCreateParam()
                .setAuthor("admin")
                .setJobGroup(jobGroup.toString())
                .setJobDesc(jobDesc)
                .setScheduleType("CRON")
                .setScheduleConf(scheduleConf)
                .setCronGen_display(scheduleConf)
                .setGlueType("BEAN")
                .setExecutorHandler(Constants.ACTION_EXECUTE_JOB_NAME)
                .setExecutorParam(objectMapper.writeValueAsString(executorParam))
                .setExecutorRouteStrategy("FIRST")
                .setMisfireStrategy("DO_NOTHING")
                .setExecutorBlockStrategy("SERIAL_EXECUTION")
                .setExecutorTimeout("0")
                .setExecutorFailRetryCount("0")
                .setGlueRemark("GLUE代码初始化");

        Map<String, String> formDataMap = objectMapper.convertValue(param, Map.class);

        var addJobUrl = properties.getAdmin().getAddJobUrl();

        HttpUtil.postFormData(addJobUrl, new HashMap<>(), formDataMap, new TypeReference<XxlJobResultVO>() {
        });
    }


}
