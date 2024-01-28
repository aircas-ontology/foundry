package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.ontology.application.service.OntologyMetaService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyMetaBO;
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
 * @since 2023/12/11 16:06
 */

@Api(tags = "本体管理")
@RestController
@RequestMapping("/OntologyMeta")
public class OntologyMetaController {

     @Resource
     private OntologyMetaService ontologyMetaService;


     @PostMapping("/add")
     @ApiOperation(value = "新增本体")
     public DataResult<Integer> add(@RequestBody OntologyMetaBO ontologyMetaBO) {
          return DataResult.ofData(ontologyMetaService.add(ontologyMetaBO));
     }

     @DeleteMapping("/delete")
     @ApiOperation(value = "删除本体")
     public DataResult<Integer> delete(@RequestParam List<Long> ids) {
          return DataResult.ofData(ontologyMetaService.delete(ids));
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
}
