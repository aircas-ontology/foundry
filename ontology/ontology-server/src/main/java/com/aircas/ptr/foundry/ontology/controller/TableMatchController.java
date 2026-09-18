package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.param.TableMatchParam;
import com.aircas.ptr.foundry.ontology.model.vo.TableMatchResultVO;
import com.aircas.ptr.foundry.ontology.service.TableMatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表智能匹配接口
 * 前端传入自然语言描述与数据源id，后端返回匹配的主表 + 外键关联表 + 字段中文描述。
 */
@Tag(name = "表智能匹配")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/table-match")
public class TableMatchController {

    private final TableMatchService tableMatchService;

    @PostMapping("/predict")
    @Operation(summary = "根据自然语言描述匹配数据源中相关的表及字段")
    public RestResult<TableMatchResultVO> predict(@RequestBody @Valid TableMatchParam param) {
        return RestResult.ofData(tableMatchService.predict(param));
    }
}
