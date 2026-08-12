package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceVO;
import com.aircas.ptr.foundry.ontology.service.OntologySpaceService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Api(tags = "本体空间管理")
@RestController
@RequestMapping("/space")
@RequiredArgsConstructor
@Validated
public class OntologySpaceController {

    private final OntologySpaceService ontologySpaceService;


    @PostMapping
    @ApiOperation(value = "创建本体空间")
    public RestResult<Integer> createSpace(@RequestBody @Valid OntologySpaceCreateParam param) {
        var id = ontologySpaceService.createSpace(param);
        return RestResult.ofData(id);
    }

    @PutMapping
    @ApiOperation(value = "修改本体空间")
    public RestResult updateSpace(@RequestBody @Valid OntologySpaceUpdateParam param) {
        ontologySpaceService.updateSpace(param);
        return RestResult.success();
    }


    @GetMapping
    @ApiOperation(value = "查询本体空间列表")
    public RestResult<List<OntologySpaceVO>> querySpace() {
        List<OntologySpaceVO> res = ontologySpaceService.querySpace();
        return RestResult.ofData(res);
    }

    @DeleteMapping("/{spaceId}")
    @ApiOperation(value = "删除本体空间")
    public RestResult deleteSpace(@PathVariable(required = true, name = "spaceId") Integer spaceId) {
        ontologySpaceService.deleteSpace(spaceId);
        return RestResult.success();
    }

}
