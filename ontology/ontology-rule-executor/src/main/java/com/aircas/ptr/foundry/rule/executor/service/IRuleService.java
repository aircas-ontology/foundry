package com.aircas.ptr.foundry.rule.executor.service;

import com.aircas.ptr.foundry.common.base.ApiResult;
import com.aircas.ptr.foundry.rule.executor.entity.vo.CheckDataVO;
import org.springframework.web.bind.annotation.RequestBody;

public interface IRuleService {

    ApiResult checkData(CheckDataVO checkDataVO);
}
