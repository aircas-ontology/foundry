package com.aircas.ptr.foundry.ontology.userinterface.controller;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.rolling.RollingFileAppender;
import com.aircas.ptr.foundry.common.util.FileUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "日志")
@RestController
@RequestMapping("/log")
@CrossOrigin
public class LogController {

    @ApiOperation("获取当前info日志")
    @ResponseBody
    @GetMapping("/info/current")
    public List<String> getInfoCurrentLog() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        String logFile = ((RollingFileAppender) context.getLogger("ROOT").getAppender("INFO")).getFile();
        List<String> result = FileUtil.readFileLines(logFile, 100);
        return result;
    }

    @ApiOperation("获取当前error日志")
    @ResponseBody
    @GetMapping("/error/current")
    public List<String> getErrorCurrentLog() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        String logFile = ((RollingFileAppender) context.getLogger("ROOT").getAppender("ERROR")).getFile();
        List<String> result = FileUtil.readFileLines(logFile, 100);
        return result;
    }

}
