package com.aircas.ptr.foundry.topic.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.topic.model.param.DictCreateParam;
import com.aircas.ptr.foundry.topic.model.param.DictQueryParam;
import com.aircas.ptr.foundry.topic.model.param.DictUpdateParam;
import com.aircas.ptr.foundry.topic.model.vo.DictCreateVO;
import com.aircas.ptr.foundry.topic.model.vo.DictItemVO;
import com.aircas.ptr.foundry.topic.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "字典")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/dict")
public class DictController {

    private final DictService dictService;

    @PostMapping
    @Operation(summary = "创建字典")
    public RestResult<DictCreateVO> create(@RequestBody @Valid DictCreateParam param) {
        return RestResult.ofData(dictService.create(param));
    }

    @GetMapping
    @Operation(summary = "按字典组编号查询字典")
    public RestResult<List<DictItemVO>> list(@Valid DictQueryParam param) {
        return RestResult.ofData(dictService.listByGroup(param));
    }

    @PutMapping
    @Operation(summary = "按字典编号编辑字典")
    public RestResult<Void> update(@RequestBody @Valid DictUpdateParam param) {
        dictService.update(param);
        return RestResult.success();
    }
}
