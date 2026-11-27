package com.aircas.ptr.foundry.ontology.client;

import com.aircas.ptr.foundry.common.exception.BusinessException;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
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
        checkSuccess(resultVO, "创建任务[" + jobDesc + "]", null);
        if (resultVO.getContent() == null || resultVO.getContent().isBlank()) {
            throw new BusinessException("xxl-job 创建任务[" + jobDesc + "]成功但未返回 jobId，响应 msg=" + resultVO.getMsg(),
                    HttpStatus.BAD_REQUEST);
        }
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
        var resultVO = HttpUtil.postFormData(url, new HashMap<>(), map, properties.getCookie(), new TypeReference<XxlJobResultVO>() {
        });
        checkSuccess(resultVO, "更新任务[" + jobDesc + "]", jobId);
    }

    public void startJob(String jobId) {
        var url = properties.getAdmin().getStartJobUrl();
        Map<String, String> map = Maps.newHashMap();
        map.put("id", jobId);
        var resultVO = HttpUtil.postFormData(url, new HashMap<>(), map, properties.getCookie(), new TypeReference<XxlJobResultVO>() {
        });
        checkSuccess(resultVO, "启动任务", jobId);
    }

    public void stopJob(String jobId) {
        var url = properties.getAdmin().getStopJobUrl();
        Map<String, String> map = Maps.newHashMap();
        map.put("id", jobId);
        var resultVO = HttpUtil.postFormData(url, new HashMap<>(), map, properties.getCookie(), new TypeReference<XxlJobResultVO>() {
        });
        checkSuccess(resultVO, "暂停任务", jobId);
    }


    public void removeJob(String jobId) {
        var url = properties.getAdmin().getRemoveJobUrl();
        Map<String, String> map = Maps.newHashMap();
        map.put("id", jobId);
        var resultVO = HttpUtil.postFormData(url, new HashMap<>(), map, properties.getCookie(), new TypeReference<XxlJobResultVO>() {
        });
        checkSuccess(resultVO, "删除任务", jobId);
    }


    /**
     * xxl-admin 的 HTTP 200 不代表业务成功：cookie 过期、jobId 不存在等都以 code/msg 返回。
     * 不校验 code 会把失败当成功落本地状态，形成删不掉、停不下来的僵尸任务。
     */
    private void checkSuccess(XxlJobResultVO resultVO, String action, String jobId) {
        var code = resultVO == null ? null : resultVO.getCode();
        if (code == null || code != 200) {
            throw new BusinessException("xxl-job " + action + "失败"
                    + (jobId == null || jobId.isBlank() ? "" : "，jobId=" + jobId)
                    + "：code=" + code + "，msg=" + (resultVO == null ? "响应为空" : resultVO.getMsg())
                    + "（jobId 无效或调度中心登录态过期时会出现，请确认 xxl-job cookie 是否有效）", HttpStatus.BAD_REQUEST);
        }
    }

    private String getJobGroupId() {

        var executorQueryParam = new XxlJobExecutorQueryParam().setAppname(properties.getExecutor().getAppname());
        var queryJobExecutorUrl = properties.getAdmin().getQueryJobExecutorUrl();
        Map<String, String> executorQueryFormDataMap = objectMapper.convertValue(executorQueryParam, Map.class);

        var vo = HttpUtil.postFormData(queryJobExecutorUrl, new HashMap<>(), executorQueryFormDataMap, properties.getCookie(), new TypeReference<XxlJobExecutorQueryVO>() {
        });
        var jobGroupId = vo.getData() == null ? null : vo.getData().stream()
                .filter(v -> v.getAppname().equals(properties.getExecutor().getAppname()))
                .findFirst()
                .map(v -> v.getId().toString())
                .orElse(null);
        if (jobGroupId == null) {
            throw new BusinessException("xxl-job 调度中心未找到执行器：" + properties.getExecutor().getAppname(),
                    HttpStatus.BAD_REQUEST);
        }
        return jobGroupId;
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
