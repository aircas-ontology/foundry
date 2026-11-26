package com.aircas.ptr.foundry.topic.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationCreateParam;
import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationQueryParam;
import com.aircas.ptr.foundry.topic.model.param.ImplicitRelationUpdateParam;
import com.aircas.ptr.foundry.topic.model.vo.ImplicitRelationDetailVO;
import com.aircas.ptr.foundry.topic.model.vo.ImplicitRelationVO;
import com.aircas.ptr.foundry.topic.service.ImplicitRelationService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "隐含关系")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/implicit-relation")
public class ImplicitRelationController {

    private final ImplicitRelationService implicitRelationService;

    @PostMapping
    @Operation(summary = "创建隐含关系")
    public RestResult<Long> create(@RequestBody @Valid ImplicitRelationCreateParam param) {
        return RestResult.ofData(implicitRelationService.create(param));
    }

    @PutMapping
    @Operation(summary = "更新隐含关系")
    public RestResult<Void> update(@RequestBody @Valid ImplicitRelationUpdateParam param) {
        implicitRelationService.update(param);
        return RestResult.success();
    }

    @GetMapping
    @Operation(summary = "分页查询隐含关系")
    public RestResult<IPage<ImplicitRelationVO>> list(@Valid ImplicitRelationQueryParam param) {
        return RestResult.ofData(implicitRelationService.listRelations(param));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除隐含关系")
    public RestResult<Void> delete(@PathVariable Long id) {
        implicitRelationService.delete(id);
        return RestResult.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "按编号查询隐含关系详情")
    public RestResult<ImplicitRelationDetailVO> get(@PathVariable Long id) {
        return RestResult.ofData(implicitRelationService.get(id));
    }
}
