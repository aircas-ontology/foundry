package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.ApiResult;
import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.ontology.application.service.OntologyGroupService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyGroupBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyLinkGraphVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyGroupVO;
import com.aircas.ptr.foundry.ontology.repository.param.OntologyGroupAddParam;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:00
 */

@Api(tags = "分组")
@RestController
@RequestMapping("/group")
public class OntologyGroupController {

    @Resource
    private OntologyGroupService ontologyGroupService;

    @PostMapping("/add")
    @ApiOperation(value = "新增本体分组")
    public ApiResult add(@RequestBody OntologyGroupAddParam param) {

        Integer res = ontologyGroupService.add(param);
        if (res > 0) {
            return DataResult.success(res);
        }
        return DataResult.fail("新增失败");
    }

    @DeleteMapping("/delete")
    @ApiOperation(value = "删除本体分组")
    public DataResult<Integer> delete(@RequestParam List<Long> ids) {
        return DataResult.ofData(ontologyGroupService.delete(ids));
    }

    @PostMapping("/update")
    @ApiOperation(value = "修改本体分组")
    public ApiResult update(@RequestBody OntologyGroupBO ontologyGroupBO) {
        return DataResult.ofData(ontologyGroupService.update(ontologyGroupBO));
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "根据分组id查询一个本体分组")
    public DataResult<OntologyGroupVO> getOntologyGroupById(@PathVariable @ApiParam(value = "本体分组id", required = true) Long id) {
        return DataResult.ofData(ontologyGroupService.getOntologyGroupById(id));
    }

    @GetMapping("/list")
    @ApiOperation("分页查询本体分组")
    public DataResult<PageInfo<OntologyGroupVO>> getAllOntologyGroups(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        return DataResult.ofData(ontologyGroupService.list(page, size));
    }

    @GetMapping("/search")
    @ApiOperation("关键字检索本体分组")
    public DataResult<PageInfo<OntologyGroupVO>> searchOntologyGroups(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        return DataResult.ofData(ontologyGroupService.search(keyword, page, size));
    }

    @GetMapping("/link/graph/{id}")
    @ApiOperation("查询分组下所有本体的关系(graph)")
    public DataResult<List<OntologyLinkGraphVO>> getOntologyGroupLinks(@PathVariable String id) {

        return DataResult.ofData(ontologyGroupService.getOntologyGroupLinks(id));
    }
}
