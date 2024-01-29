package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.ontology.application.service.OntologyLinkService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyLinkBO;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyLinkGroupBo;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyMetaBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyLinkVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyMetaVO;
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

@Api(tags = "本体关系管理")
@RestController
@RequestMapping("/OntologyLink")
public class OntologyLinkController {

    @Resource
    private OntologyLinkService ontologyLinkService;

    @PostMapping("/add")
    @ApiOperation(value = "新增本体之间的关系")
    public DataResult<Integer> add(@RequestBody OntologyLinkGroupBo ontologyLinkGroupBo) {
        return DataResult.ofData(ontologyLinkService.add(ontologyLinkGroupBo));
    }

//    @PostMapping("/add")
//    @ApiOperation(value = "新增本体之间的关系")
//    public DataResult<Integer> add(@RequestBody OntologyLinkBO ontologyLinkBO) {
//        return DataResult.ofData(ontologyLinkService.add(ontologyLinkBO));
//    }
//
//    @DeleteMapping("/delete")
//    @ApiOperation(value = "删除本体之间的关系")
//    public DataResult<Integer> delete(@RequestParam List<Long> ids) {
//        return DataResult.ofData(ontologyLinkService.delete(ids));
//    }
//
//    @PostMapping("/update")
//    @ApiOperation(value = "修改本体之间的关系")
//    public DataResult<Integer> update(@RequestBody OntologyLinkBO ontologyLinkBO) {
//        return DataResult.ofData(ontologyLinkService.update(ontologyLinkBO));
//    }
//
//    @GetMapping("/queryById")
//    @ApiOperation(value = "根据本体间关系id查询一个本体之间的关系")
//    public DataResult<OntologyLinkVO> getOntologyLinkById(@RequestParam @ApiParam(value = "本体间关系的id", required = true) Long id) {
//        return DataResult.ofData(ontologyLinkService.getOntologyLinkById(id));
//    }
}
