package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.ontology.application.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyMetaBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyGroupMetaVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyMetaVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyPropertyVO;
import com.aircas.ptr.foundry.ontology.repository.param.OntologyMetaAddParam;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.websocket.server.PathParam;
import java.util.List;


@Api(tags = "元数据")
@RestController
@RequestMapping("/OntologyMeta")
public class OntologyMetaController {

    @Resource
    private OntologyMetaService ontologyMetaService;

    @PostMapping("/add")
    @ApiOperation(value = "新增本体")
    public DataResult<OntologyMetaVO> add(@RequestBody OntologyMetaAddParam param) {

        return DataResult.ofData(ontologyMetaService.add(param));
    }

    @PostMapping("/delete")
    @ApiOperation(value = "删除本体")
    public DataResult<Integer> delete(@RequestParam @ApiParam(value = "本体id", required = true) String uniqueIdentifier) {
        return DataResult.ofData(ontologyMetaService.delete(uniqueIdentifier));
    }

    @PostMapping("/update")
    @ApiOperation(value = "修改本体")
    public DataResult<Integer> update(@RequestBody OntologyMetaBO ontologyMetaBO) {
        return DataResult.ofData(ontologyMetaService.update(ontologyMetaBO));
    }

    @GetMapping("/queryById")
    @ApiOperation(value = "根据id查询一个本体")
    public DataResult<OntologyMetaVO> getOntologyById(@RequestParam @ApiParam(value = "本体id", required = true) Long id) {
        return DataResult.ofData(ontologyMetaService.getOntologyById(id));
    }

    @GetMapping("/queryByUniqueIdentifier")
    @ApiOperation(value = "根据unique identifier查询一个本体")
    public DataResult<OntologyMetaVO> getOntologyById(@RequestParam @ApiParam(value = "本体unique identifer", required = true) String uniqueIdentifier) {
        return DataResult.ofData(ontologyMetaService.getOntologyByUniqueIdentifier(uniqueIdentifier));
    }

    @GetMapping("/getAll")
    @ApiOperation(value = "查询所有本体的metadata")
    public DataResult<List<OntologyMetaVO>> getAllOntologies() {
        return DataResult.ofData(ontologyMetaService.getAllOntologies());
    }

    @GetMapping("/search")
    @ApiOperation(value = "搜索本体", notes = "通过关键字匹配本体，包括本体名称、本体描述、本体别名")
    public DataResult<List<OntologyMetaVO>> searchOntologies(@RequestParam(required = false) String keyword) {

        return DataResult.ofData(ontologyMetaService.searchOntologies(keyword));
    }

    @GetMapping("/search/group")
    @ApiOperation(value = "本体搜索，并分组返回", notes = "通过关键字匹配本体，并以本体分组形式返回")
    public DataResult<PageInfo<OntologyGroupMetaVO>> searchGroupOntologies(
            @RequestParam @ApiParam(value = "关键字", defaultValue = "舰船", required = false) String keyword,
            @RequestParam @ApiParam(value = "页数", required = false, defaultValue = "1") Integer page,
            @RequestParam @ApiParam(value = "每页条数", required = false, defaultValue = "10") Integer size) {

        return DataResult.ofData(ontologyMetaService.searchGroupOntologies(keyword, page, size));
    }
}
