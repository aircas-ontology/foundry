package com.aircas.ptr.foundry.rule.executor.controller;

import com.aircas.ptr.foundry.common.base.ApiResult;
import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.rule.executor.entity.ActionHandleRule;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyAction;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyMeta;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyProperty;
import com.aircas.ptr.foundry.rule.executor.entity.param.ActionHandleParam;
import com.aircas.ptr.foundry.rule.executor.entity.vo.CheckDataVO;
import com.aircas.ptr.foundry.rule.executor.service.ICheckService;
import com.aircas.ptr.foundry.rule.executor.service.IOntologyServer;
import com.aircas.ptr.foundry.rule.executor.service.IRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/rule")
public class RuleController {

    @Autowired
    private IRuleService ruleService;

    @PostMapping("/check/action")
    public ApiResult checkData(@RequestBody CheckDataVO checkDataVO){


        return ruleService.checkData(checkDataVO);
    }
}
