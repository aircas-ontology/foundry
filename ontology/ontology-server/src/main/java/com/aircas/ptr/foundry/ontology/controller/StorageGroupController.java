package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.param.StorageGroupCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.StorageGroupUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.StorageGroupVO;
import com.aircas.ptr.foundry.ontology.service.StorageGroupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Tag(name = "存储分组管理")
@RestController
@RequestMapping("/storage_group")
@RequiredArgsConstructor
@Validated
public class StorageGroupController {

    private final StorageGroupService storageGroupService;

    @PostMapping
    @Operation(summary = "新增存储分组")
    public RestResult create(@RequestBody @Valid StorageGroupCreateParam param) {
        storageGroupService.create(param);
        return RestResult.success();
    }

    @PutMapping
    @Operation(summary = "修改存储分组")
    public RestResult update(@RequestBody @Valid StorageGroupUpdateParam param) {
        storageGroupService.update(param);
        return RestResult.success();
    }

    @DeleteMapping
    @Operation(summary = "删除存储分组")
    public RestResult delete(@RequestParam @Parameter(description = "主键id") Integer id) {
        storageGroupService.delete(id);
        return RestResult.success();
    }

    @GetMapping("/list")
    @Operation(summary = "根据本体对象id查询存储分组列表")
    public RestResult<List<StorageGroupVO>> listByOntologyId(
            @RequestParam @Parameter(description = "本体对象唯一标识") String ontologyUniqueIdentifier) {
        List<StorageGroupVO> result = storageGroupService.listByOntologyId(ontologyUniqueIdentifier);
        return RestResult.ofData(result);
    }
}
