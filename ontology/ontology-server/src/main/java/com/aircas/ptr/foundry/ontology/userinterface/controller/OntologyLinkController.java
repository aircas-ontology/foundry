package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.application.service.OntologyLinkGroupService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyLinkGroupBo;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyLinkGraphVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyLinkGroupVO;
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

@Api(tags = "关系")
@RestController
@RequestMapping("/link")
public class OntologyLinkController {

    @Resource
    private OntologyLinkGroupService ontologyLinkGroupService;

    @PostMapping("/add")
    @ApiOperation(value = "新增本体之间的关系")
    public DataResult<Integer> add(@RequestBody OntologyLinkGroupBo ontologyLinkGroupBo) {
        return DataResult.ofData(ontologyLinkGroupService.add(ontologyLinkGroupBo));
    }


    @GetMapping("/by_ontology/{oId}")
    @ApiOperation(value = "根据本体id查询关系(1跳)")
    public DataResult<List<OntologyLinkGroupVO>> getLinkByOntologyUniqueIdentifier(@PathVariable String oId) {
        return DataResult.ofData(ontologyLinkGroupService.getLinkByOntologyUniqueIdentifier(oId));
    }

    @DeleteMapping("/by_ontology/{oId}")
    @ApiOperation(value = "根据本体id删除关系")
    public RestResult deleteLinkByUniqueIdentifier(@PathVariable @ApiParam(value = "本体id", required = true) String oId) {
        RestResult result = ontologyLinkGroupService.deleteLinkByOntologyUniqueIdentifier(oId);
        return result;
    }

    @GetMapping("/by_ontology/graph/{oId}")
    @ApiOperation(value = "根据本体id查询关系(1跳)(graph)")
    public DataResult<OntologyLinkGraphVO> getLinkGraphByOntologyUniqueIdentifier(@PathVariable String oId) {

        return DataResult.ofData(ontologyLinkGroupService.getLinkGraphByOntologyUniqueIdentifier(oId));
    }

    @GetMapping("/all")
    @ApiOperation(value = "得到所有的关系")
    public DataResult<List<OntologyLinkGroupVO>> getAllLinks() {
        return DataResult.ofData(ontologyLinkGroupService.getAll());
    }
}
