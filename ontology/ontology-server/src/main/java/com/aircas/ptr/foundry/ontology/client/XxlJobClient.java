package com.aircas.ptr.foundry.ontology.client;

import com.aircas.ptr.foundry.common.util.HttpUtil;
import com.aircas.ptr.foundry.ontology.config.XxlJobProperties;
import com.aircas.ptr.foundry.ontology.model.common.Constants;
import com.aircas.ptr.foundry.ontology.model.param.*;
import com.aircas.ptr.foundry.ontology.model.vo.XxlJobExecutorQueryVO;
import com.aircas.ptr.foundry.ontology.model.vo.XxlJobInfoQueryVO;
import com.aircas.ptr.foundry.ontology.model.vo.XxlJobResultVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
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


    public String createJobInfo(String jobDesc, String scheduleConf, XxlJobActionExecuteParam executorParam) throws Exception {

        var jobGroupId = getJobGroupId();

        var param = new XxlJobCreateParam()
                .setJobGroup(jobGroupId)
                .setJobDesc(jobDesc)
                .setScheduleConf(scheduleConf)
                .setCronGen_display(scheduleConf)
                .setExecutorHandler(Constants.ACTION_EXECUTE_JOB_NAME)
                .setExecutorParam(objectMapper.writeValueAsString(executorParam));

        Map<String, String> formDataMap = objectMapper.convertValue(param, Map.class);

        var addJobUrl = properties.getAdmin().getAddJobUrl();

        var resultVO = HttpUtil.postFormData(addJobUrl, new HashMap<>(), formDataMap, properties.getCookie(), new TypeReference<XxlJobResultVO>() {
        });
        return resultVO.getContent();
    }


    public void updateJobInfo(String jobId, String jobDesc, String scheduleConf, OntologyActionExecuteParam executorParam) throws Exception {

        var jobGroupId = getJobGroupId();
        var url = properties.getAdmin().getUpdateJobUrl();
        var param = new XxlJobUpdateParam()
                .setId(jobId)
                .setJobGroup(jobGroupId)
                .setJobDesc(jobDesc)
                .setExecutorHandler(Constants.ACTION_EXECUTE_JOB_NAME)
                .setExecutorParam(objectMapper.writeValueAsString(executorParam))
                .setSchedule_conf_CRON(scheduleConf)
                .setScheduleConf(scheduleConf)
                .setCronGen_display(scheduleConf);

        Map<String, String> map = objectMapper.convertValue(param, Map.class);
        HttpUtil.postFormData(url, new HashMap<>(), map, properties.getCookie(), new TypeReference<XxlJobResultVO>() {
        });
    }

    public void startJob(String jobId) {
        var url = properties.getAdmin().getStartJobUrl();
        Map<String, String> map = Maps.newHashMap();
        map.put("id", jobId);
        HttpUtil.postFormData(url, new HashMap<>(), map, properties.getCookie(), new TypeReference<XxlJobResultVO>() {
        });
    }

    public void stopJob(String jobId) {
        var url = properties.getAdmin().getStopJobUrl();
        Map<String, String> map = Maps.newHashMap();
        map.put("id", jobId);
        HttpUtil.postFormData(url, new HashMap<>(), map, properties.getCookie(), new TypeReference<XxlJobResultVO>() {
        });
    }


    public void removeJob(String jobId) {
        var url = properties.getAdmin().getRemoveJobUrl();
        Map<String, String> map = Maps.newHashMap();
        map.put("id", jobId);
        HttpUtil.postFormData(url, new HashMap<>(), map, properties.getCookie(), new TypeReference<XxlJobResultVO>() {
        });
    }



    private String getJobGroupId() {

        var executorQueryParam = new XxlJobExecutorQueryParam().setAppname(properties.getExecutor().getAppname());
        var queryJobExecutorUrl = properties.getAdmin().getQueryJobExecutorUrl();
        Map<String, String> executorQueryFormDataMap = objectMapper.convertValue(executorQueryParam, Map.class);

        var vo = HttpUtil.postFormData(queryJobExecutorUrl, new HashMap<>(), executorQueryFormDataMap, properties.getCookie(), new TypeReference<XxlJobExecutorQueryVO>() {
        });
        var jobGroupId = vo.getData().stream().filter(v -> v.getAppname().equals(properties.getExecutor().getAppname())).findFirst().get().getId();
        return jobGroupId.toString();
    }

    private String getJobInfoId(String jobDesc, String jobGroupId) {
        var queryParam = new XxlJobInfoQueryParam().setJobGroup(jobGroupId).setJobDesc(jobDesc);
        var url = properties.getAdmin().getQueryJobInfoUrl();
        Map<String, String> map = objectMapper.convertValue(queryParam, Map.class);

        var vo = HttpUtil.postFormData(url, new HashMap<>(), map, properties.getCookie(), new TypeReference<XxlJobInfoQueryVO>() {
        });
        var jobInfoId = vo.getData().get(0).getId();
        return jobInfoId.toString();
    }


}
