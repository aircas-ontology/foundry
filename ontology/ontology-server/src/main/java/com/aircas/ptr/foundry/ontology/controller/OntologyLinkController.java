package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkDirectionEnum;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.OntologyLinkCreateParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkGraphVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyLinkInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.service.OntologyLinkGroupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;



@Tag(name = "本体关系管理")
@RestController
@Validated
@RequestMapping("/link")
public class OntologyLinkController {

    @Resource
    private OntologyLinkGroupService ontologyLinkGroupService;

    @PostMapping("")
    @Operation(summary = "创建本体之间的关系")
    public RestResult createLink(@RequestBody OntologyLinkCreateParam linkCreateParam) {
        ontologyLinkGroupService.createLink(linkCreateParam);
        return RestResult.success();
    }

    @GetMapping("")
    @Operation(summary = "根据link uniqid查询关系")
    public RestResult<OntologyLinkInfoVO> getLinkByUniqueIdentifier(@RequestParam(required = true, name = "uniqueIdentifier") @Parameter(description = "uniqueIdentifier") String uniqueIdentifier) {
        return RestResult.ofData(ontologyLinkGroupService.getLinkByUniqueIdentifier(uniqueIdentifier));
    }


    @DeleteMapping("/{linkUniqIdentifier}")
    @Operation(summary = "根据link uniqid删除关系")
    public RestResult deleteLinkByUniqueIdentifier(@PathVariable(required = true, name = "linkUniqIdentifier") String linkUniqIdentifier) {
        ontologyLinkGroupService.deleteLinkByLinkUniqueIdentifier(linkUniqIdentifier);
        return RestResult.success();
    }

    @GetMapping("/ontology_meta")
    @Operation(summary = "查询与本体关联的其他本体")
    public RestResult<List<OntologyMetaInfoVO>> getLinkedOntology(@RequestParam(required = true, name = "ontologyUniqueIdentifier") @Parameter(description = "本体uniqueIdentifier")
                                                                  @OntologyIdVerify String ontologyUniqueIdentifier) {
        return RestResult.ofData(ontologyLinkGroupService.getLinkedOntology(ontologyUniqueIdentifier));
    }


    @GetMapping("/by_ontology")
    @Operation(summary = "根据本体id查询关系(1跳)")
    public RestResult<List<OntologyLinkInfoVO>> getLinkByOntologyUniqueIdentifier(@RequestParam(required = true, name = "ontologyUniqueIdentifier") @Parameter(description = "本体uniqueIdentifier")
                                                                                  @OntologyIdVerify String ontologyUniqueIdentifier,
                                                                                  @RequestParam(required = false, name = "direction", defaultValue = "ALL") @Parameter(description = "关系方向") OntologyLinkDirectionEnum direction) {
        return RestResult.ofData(ontologyLinkGroupService.getLinksByOntologyUniqueIdentifier(ontologyUniqueIdentifier, direction));
    }

    @GetMapping("/by_group")
    @Operation(summary = "根据group id查询组内本体所有关系(1跳)")
    public RestResult<List<OntologyLinkInfoVO>> getLinkByGroupId(@RequestParam(required = false, name = "groupId") @Parameter(description = "分组id") String groupId) {
        List<OntologyLinkInfoVO> result = ontologyLinkGroupService.getLinksByGroupId(groupId);
        return RestResult.ofData(result);
    }

    @GetMapping("/by_category")
    @Operation(summary = "根据关系分类id查询关系列表")
    public RestResult<List<OntologyLinkInfoVO>> getByCategoryId(@RequestParam(name = "categoryId", required = false) @Parameter(description = "关系分类id，不传则查询全部关系") Integer categoryId) {
        return RestResult.ofData(ontologyLinkGroupService.getByCategoryId(categoryId));
    }

    @GetMapping("/graph")
    @Operation(summary = "查询关系图数据(本体-关系-本体)，需传空间id；传本体对象id则以该对象为中心返回其关系，不传则返回本空间全部关系图")
    public RestResult<OntologyLinkGraphVO> getLinkGraph(@RequestParam(name = "spaceId") @Parameter(description = "本体空间id") Integer spaceId,
                                                        @RequestParam(required = false, name = "ontologyUniqueIdentifier")
                                                        @Parameter(description = "本体对象uniqueIdentifier，不传则返回本空间全部关系图") String ontologyUniqueIdentifier) {
        return RestResult.ofData(ontologyLinkGroupService.getLinkGraph(spaceId, ontologyUniqueIdentifier));
    }


}
