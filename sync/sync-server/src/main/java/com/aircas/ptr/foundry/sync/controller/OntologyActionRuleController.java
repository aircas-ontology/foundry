package com.aircas.ptr.foundry.sync.controller;

import com.aircas.ptr.foundry.common.dto.OntologyActionRuleDTO;
import com.aircas.ptr.foundry.common.response.Result;
import com.aircas.ptr.foundry.common.response.ResultUtil;
import com.aircas.ptr.foundry.common.vo.OntologyActionRuleVO;
import com.aircas.ptr.foundry.sync.domain.entity.OntologyActionRule;
import com.aircas.ptr.foundry.sync.server.OntologyActionRuleService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "本体行为配置")
@RestController
@RequestMapping(value = "OntologyActionRule")
@CrossOrigin("*")
public class OntologyActionRuleController {

    @Autowired
    OntologyActionRuleService ontologyActionRuleService;

    @ApiOperation("新增或修改本体行为配置")
    @PostMapping("add")
    public Result<String> addOntologyActionRule(@RequestBody OntologyActionRuleVO vo) {
        OntologyActionRule entity = new OntologyActionRule();
        BeanUtils.copyProperties(vo, entity);
        boolean b = ontologyActionRuleService.saveOrUpdate(entity);
        return ResultUtil.success();
    }

    @ApiOperation("查询本体行为配置")
    @GetMapping("queryById")
    public Result<OntologyActionRuleDTO> quseryOntologyActionRuleById(@RequestParam Long id) {
        OntologyActionRule ontologyActionRule = ontologyActionRuleService.getById(id);
        OntologyActionRuleDTO dto = new OntologyActionRuleDTO();
        BeanUtils.copyProperties(ontologyActionRule, dto);
        return ResultUtil.success(dto);
    }

    @ApiOperation("删除本体行为配置")
    @GetMapping("deleteById")
    public Result deleteById(@RequestParam Long id) {
        return ontologyActionRuleService.removeById(id)?ResultUtil.success("删除成功!"):ResultUtil.error("删除失败!");
    }

//    @ApiOperation("查询本体行为配置")
//    @GetMapping("queryById")
//    public Result<List<OntologyActionRuleDTO>> quseryOntologyActionRule(@RequestParam Long id) {
//        OntologyActionRule ontologyActionRule = ontologyActionRuleService.getById(id);
//        OntologyActionRuleDTO dto = new OntologyActionRuleDTO();
//        BeanUtils.copyProperties(ontologyActionRule, dto);
//        return ResultUtil.success(dto);
//    }
}



















