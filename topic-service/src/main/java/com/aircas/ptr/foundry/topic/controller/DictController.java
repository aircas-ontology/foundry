package com.aircas.ptr.foundry.topic.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.topic.model.param.DictCreateParam;
import com.aircas.ptr.foundry.topic.model.param.DictUpdateParam;
import com.aircas.ptr.foundry.topic.model.vo.DictCreateVO;
import com.aircas.ptr.foundry.topic.model.vo.DictItemVO;
import com.aircas.ptr.foundry.topic.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public RestResult<List<DictItemVO>> list(@Parameter(description = """
                                                     字典组编号。
                                                     查询事件主题类别：传入 event_topic_type；
                                                     查询事件主题地理区域：传入 geo_region；
                                                     查询任务类型：传入 task_type；
                                                     查询任务区域：传入 task_region
                                                     """,
                                             examples = {
                                                     @ExampleObject(name = "事件主题类别", value = "event_topic_type",
                                                             description = "查询事件主题类别"),
                                                     @ExampleObject(name = "事件主题地理区域", value = "geo_region",
                                                             description = "查询事件主题地理区域"),
                                                     @ExampleObject(name = "任务类型", value = "task_type",
                                                             description = "查询任务类型"),
                                                     @ExampleObject(name = "任务区域", value = "task_region",
                                                             description = "查询任务区域")
                                             })
                                             @RequestParam
                                             @NotBlank(message = "字典组编号不能为空")
                                             @Size(max = 64, message = "字典组编号最长64个字符")
                                             String groupCode) {
        return RestResult.ofData(dictService.listByGroup(groupCode));
    }

    @PutMapping
    @Operation(summary = "按字典编号编辑字典")
    public RestResult<Void> update(@RequestBody @Valid DictUpdateParam param) {
        dictService.update(param);
        return RestResult.success();
    }
}
