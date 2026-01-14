package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkDirectionEnum;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCreateParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkGroupService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
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

    @GetMapping("")
    @ApiOperation(value = "根据link uniqid查询关系")
    public RestResult<OntologyLinkInfoVO> getLinkByUniqueIdentifier(@RequestParam(required = true, name = "uniqueIdentifier") @ApiParam(value = "uniqueIdentifier", required = true) String uniqueIdentifier) {
        return RestResult.ofData(ontologyLinkGroupService.getLinkByUniqueIdentifier(uniqueIdentifier));
    }


    @DeleteMapping("/{linkUniqIdentifier}")
    @ApiOperation(value = "根据link uniqid删除关系")
    public RestResult deleteLinkByUniqueIdentifier(@PathVariable(required = true, name = "linkUniqIdentifier") String linkUniqIdentifier) {
        ontologyLinkGroupService.deleteLinkByLinkUniqueIdentifier(linkUniqIdentifier);
        return RestResult.success();
    }

    @GetMapping("/ontology_meta")
    @ApiOperation(value = "查询与本体关联的其他本体")
    public RestResult<List<OntologyMetaInfoVO>> getLinkedOntology(@RequestParam(required = true, name = "ontologyUniqueIdentifier") @ApiParam(value = "本体uniqueIdentifier", required = true)
                                                                  @OntologyIdVerify String ontologyUniqueIdentifier) {
        return RestResult.ofData(ontologyLinkGroupService.getLinkedOntology(ontologyUniqueIdentifier));
    }


    @GetMapping("/by_ontology")
    @ApiOperation(value = "根据本体id查询关系(1跳)")
    public RestResult<List<OntologyLinkInfoVO>> getLinkByOntologyUniqueIdentifier(@RequestParam(required = true, name = "ontologyUniqueIdentifier") @ApiParam(value = "本体uniqueIdentifier", required = true)
                                                                                  @OntologyIdVerify String ontologyUniqueIdentifier,
                                                                                  @RequestParam(required = false, name = "direction", defaultValue = "ALL") @ApiParam(value = "关系方向", required = false) OntologyLinkDirectionEnum direction) {
        return RestResult.ofData(ontologyLinkGroupService.getLinksByOntologyUniqueIdentifier(ontologyUniqueIdentifier, direction));
    }

    @GetMapping("/by_group")
    @ApiOperation(value = "根据group id查询组内本体所有关系(1跳)")
    public RestResult<List<OntologyLinkInfoVO>> getLinkByGroupId(@RequestParam(required = false, name = "groupId") @ApiParam(value = "分组id", required = false) String groupId) {
        List<OntologyLinkInfoVO> result = ontologyLinkGroupService.getLinksByGroupId(groupId);
        return RestResult.ofData(result);
    }


}
