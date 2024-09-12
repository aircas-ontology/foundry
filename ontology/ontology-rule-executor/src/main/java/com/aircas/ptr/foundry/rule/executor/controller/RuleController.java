package com.aircas.ptr.foundry.rule.executor.controller;

import com.aircas.ptr.foundry.common.base.ApiResult;
import com.aircas.ptr.foundry.rule.executor.entity.vo.CheckDataVO;
import com.aircas.ptr.foundry.rule.executor.service.IRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
