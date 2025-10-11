package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.GroupIdVerify;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.EntityLinkQueryParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.EntityLinkInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkInfoVO;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkGroupService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:00
 */

@Api(tags = "本体关系")
@RestController
@Validated
@RequestMapping("/link")
public class OntologyLinkController {

    @Resource
    private OntologyLinkGroupService ontologyLinkGroupService;

    @PostMapping("")
    @ApiOperation(value = "创建本体之间的关系")
    public RestResult createLink(@RequestBody OntologyLinkCreateParam linkCreateParam) {
        ontologyLinkGroupService.createLink(linkCreateParam);
        return RestResult.success();
    }

    @PutMapping("")
    @ApiOperation(value = "修改本体之间的关系")
    public RestResult updateLink(@RequestBody OntologyLinkUpdateParam linkUpdateParam) {
        return RestResult.success();
    }

    @DeleteMapping("/{uniqIdentifier}")
    @ApiOperation(value = "根据link uniqid删除关系")
    public RestResult deleteLinkByUniqueIdentifier(@PathVariable(required = true, name = "uniqIdentifier") @Valid String uniqueIdentifier) {
        /**
         * todo 需要检查关联关系是否被行为使用到
         */
        ontologyLinkGroupService.deleteLinkByOntologyUniqueIdentifier(uniqueIdentifier);
        return RestResult.success();
    }


    @GetMapping("/by_ontology")
    @ApiOperation(value = "根据本体id查询关系(1跳)")
    public RestResult<List<OntologyLinkInfoVO>> getLinkByOntologyUniqueIdentifier(@RequestParam(required = true, name = "ontologyUniqueIdentifier") @ApiParam(value = "本体uniqueIdentifier", required = true)
                                                                                  @OntologyIdVerify String ontologyUniqueIdentifier) {
        return RestResult.ofData(ontologyLinkGroupService.getLinksByOntologyUniqueIdentifier(ontologyUniqueIdentifier));
    }

    @GetMapping("/by_group")
    @ApiOperation(value = "根据group id查询组内本体所有关系(1跳)")
    public RestResult<List<OntologyLinkInfoVO>> getLinkByGroupId(@RequestParam(required = true, name = "groupId") @ApiParam(value = "分组id", required = true)
                                                                 @GroupIdVerify String groupId) {
        List<OntologyLinkInfoVO> result = ontologyLinkGroupService.getLinksByGroupId(groupId);
        return RestResult.ofData(result);
    }

    @PostMapping("/entity")
    @ApiOperation(value = "查询实体关系(1跳)")
    public RestResult<List<EntityLinkInfoVO>> getEntityLink(@RequestBody @Valid EntityLinkQueryParam queryParam) {
        return RestResult.ofData(null);
    }


//    @GetMapping("/by_ontology/graph")
//    @ApiOperation(value = "根据本体id查询关系(1跳)(graph)")
//    public RestResult<OntologyLinkGraphVO> getLinkGraphByOntologyUniqueIdentifier(@RequestParam(required = true, name = "ontologyUniqueIdentifier") @ApiParam(value = "本体uniqueIdentifier", required = true) String ontologyUniqueIdentifier) {
//
//        return RestResult.ofData(ontologyLinkGroupService.getLinkGraphByOntologyUniqueIdentifier(oId));
//    }

    @GetMapping("/all")
    @ApiOperation(value = "查询所有本体的所有关系")
    public RestResult<List<OntologyLinkInfoVO>> getAllLinks() {
        return RestResult.ofData(null);
    }
}
