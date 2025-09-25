package com.aircas.ptr.foundry.ontology.entity.controller;

import com.aircas.ptr.foundry.ontology.entity.model.document.OntologyNode;
import com.aircas.ptr.foundry.ontology.entity.model.document.OntologyRelation;
import com.aircas.ptr.foundry.ontology.entity.model.dto.BatchImportDTO;
import com.aircas.ptr.foundry.ontology.entity.model.dto.OntologyRelationDTO;
import com.aircas.ptr.foundry.ontology.entity.service.ExcelService;
import com.aircas.ptr.foundry.ontology.entity.service.GraphTraversalService;
import com.aircas.ptr.foundry.ontology.entity.service.OntologyService;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Api(tags = "本体管理接口")
@RestController
@RequestMapping("/api/ontology")
public class EntityNodeController {

    @Autowired
    private OntologyService ontologyService;

    @Autowired
    private GraphTraversalService graphTraversalService;

    @Autowired
    private ExcelService excelService;

    @ApiOperation("创建节点")
    @ApiResponses({
            @ApiResponse(code = 200, message = "创建成功"),
            @ApiResponse(code = 400, message = "参数错误")
    })
    @PostMapping("/nodes")
    public OntologyNode createNode(@ApiParam("节点信息") @RequestBody OntologyNode node) {
        return ontologyService.createNode(node);
    }

    @ApiOperation("根据ID获取节点")
    @GetMapping("/nodes/{id}")
    public OntologyNode getNode(@ApiParam("节点ID") @PathVariable String id) {
        return ontologyService.getNodeById(id);
    }

    @ApiOperation("获取所有节点")
    @GetMapping("/nodes")
    public List<OntologyNode> getAllNodes() {
        return ontologyService.getAllNodes();
    }

    @ApiOperation("根据名称获取节点")
    @GetMapping("/nodes/name/{name}")
    public OntologyNode getNodeByName(@ApiParam("节点名称") @PathVariable String name) {
        return ontologyService.getNodeByName(name);
    }

    @ApiOperation("删除节点")
    @DeleteMapping("/nodes/{id}")
    public void deleteNode(@ApiParam("节点ID") @PathVariable String id) {
        ontologyService.deleteNode(id);
    }

    // 关系相关接口
    @ApiOperation("创建关系")
    @ApiResponses({
            @ApiResponse(code = 200, message = "创建成功"),
            @ApiResponse(code = 400, message = "参数错误"),
            @ApiResponse(code = 404, message = "节点不存在")
    })
    @PostMapping("/relations")
    public OntologyRelation createRelation(@RequestBody OntologyRelationDTO relationDTO) {
        // 将DTO转换为实体
        OntologyRelation relation = relationDTO.toEntity();

        // 验证必要字段
        if (relation.get_from() == null || relation.get_to() == null) {
            throw new IllegalArgumentException("关系的from和to字段不能为空");
        }

        return ontologyService.createRelation(relation);
    }

    @ApiOperation("根据ID获取关系")
    @GetMapping("/relations/{id}")
    public OntologyRelation getRelation(@ApiParam("关系ID") @PathVariable String id) {
        return ontologyService.getRelationById(id);
    }

    @ApiOperation("获取从指定节点出发的关系")
    @GetMapping("/relations/from/{fromNode}")
    public List<OntologyRelation> getRelationsByFromNode(@ApiParam("起始节点ID") @PathVariable String fromNode) {
        return ontologyService.getRelationsByFromNode(fromNode);
    }

    @ApiOperation("获取指向指定节点的关系")
    @GetMapping("/relations/to/{toNode}")
    public List<OntologyRelation> getRelationsByToNode(@ApiParam("目标节点ID") @PathVariable String toNode) {
        return ontologyService.getRelationsByToNode(toNode);
    }

    @ApiOperation("获取指定类型的关系")
    @GetMapping("/relations/type/{relationType}")
    public List<OntologyRelation> getRelationsByType(@ApiParam("关系类型") @PathVariable String relationType) {
        return ontologyService.getRelationsByType(relationType);
    }

    @ApiOperation("删除关系")
    @DeleteMapping("/relations/{id}")
    public void deleteRelation(@ApiParam("关系ID") @PathVariable String id) {
        ontologyService.deleteRelation(id);
    }

    @ApiOperation("批量导入节点和关系")
    @ApiResponses({
            @ApiResponse(code = 200, message = "导入成功"),
            @ApiResponse(code = 400, message = "数据格式错误")
    })
    @PostMapping("/batch-import")
    public void batchImport(@ApiParam("批量导入数据") @RequestBody BatchImportDTO batchData) {
        ontologyService.batchImport(batchData);
    }

    @ApiOperation("关键字搜索节点")
    @GetMapping("/search")
    public List<OntologyNode> searchNodes(@ApiParam("搜索关键字") @RequestParam String keyword) {
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("keyword", keyword);
        return ontologyService.searchNodes(conditions);
    }

    @ApiOperation("获取节点及其关联关系")
    @GetMapping("/nodes/{id}/with-relations")
    public List<Map> getNodeWithRelations(@ApiParam("节点ID") @PathVariable String id) {
        return ontologyService.getNodeWithRelations(id);
    }

    @ApiOperation("获取节点及其活跃关系")
    @ApiResponses({
            @ApiResponse(code = 200, message = "查询成功"),
            @ApiResponse(code = 404, message = "节点不存在")
    })
    @GetMapping("/nodes/{id}/active-relations")
    public Map<String, Object> getNodeWithActiveRelations(@ApiParam("节点ID") @PathVariable String id) {
        return ontologyService.getNodeWithActiveRelations(id);
    }

    @ApiOperation("图遍历查询")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "startNodeId", value = "起始节点ID", required = true),
            @ApiImplicitParam(name = "maxDepth", value = "最大遍历深度", defaultValue = "3"),
            @ApiImplicitParam(name = "direction", value = "遍历方向(OUTBOUND/INBOUND/ANY)", defaultValue = "ANY")
    })
    @GetMapping("/traverse")
    public List<Map> traverseGraph(
            @RequestParam String startNodeId,
            @RequestParam(defaultValue = "3") int maxDepth,
            @RequestParam(defaultValue = "ANY") String direction) {
        return graphTraversalService.traverseGraph(startNodeId, maxDepth, direction);
    }

    @ApiOperation("查找最短路径")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "startNodeId", value = "起始节点ID", required = true),
            @ApiImplicitParam(name = "endNodeId", value = "目标节点ID", required = true)
    })
    @GetMapping("/shortest-path")
    public List<Map> findShortestPath(
            @RequestParam String startNodeId,
            @RequestParam String endNodeId) {
        return graphTraversalService.findShortestPath(startNodeId, endNodeId);
    }

    @ApiOperation("从Excel导入数据")
    @ApiResponses({
            @ApiResponse(code = 200, message = "导入成功"),
            @ApiResponse(code = 400, message = "Excel格式错误"),
            @ApiResponse(code = 500, message = "导入失败")
    })
    @PostMapping("/import/excel")
    public void importFromExcel(@ApiParam("Excel文件") @RequestParam("file") MultipartFile file) throws IOException {
        BatchImportDTO batchData = excelService.parseExcel(file);
        ontologyService.batchImport(batchData);
    }

    @ApiOperation("下载Excel导入模板")
    @GetMapping("/export/template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] template = excelService.generateExcelTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "ontology_template.xlsx");

        return ResponseEntity
                .ok()
                .headers(headers)
                .body(template);
    }

    @ApiOperation("更新节点")
    @ApiResponses({
            @ApiResponse(code = 200, message = "更新成功"),
            @ApiResponse(code = 404, message = "节点不存在"),
            @ApiResponse(code = 400, message = "参数错误")
    })
    @PutMapping("/nodes/{id}")
    public OntologyNode updateNode(
            @ApiParam("节点ID") @PathVariable String id,
            @ApiParam("节点信息") @RequestBody OntologyNode node) {
        return ontologyService.updateNode(id, node);
    }

    @ApiOperation("高级搜索节点")
    @ApiResponses({
            @ApiResponse(code = 200, message = "搜索成功")
    })
    @PostMapping("/nodes/search")
    public List<OntologyNode> searchNodes(
            @ApiParam("搜索条件") @RequestBody Map<String, Object> conditions) {
        return ontologyService.searchNodes(conditions);
    }

    @ApiOperation("查询某时间段内的节点及其活跃关系")
    @ApiResponses({
            @ApiResponse(code = 200, message = "查询成功"),
            @ApiResponse(code = 400, message = "参数错误")
    })
    @GetMapping("/active-relations-in-timespan")
    public ResponseEntity<?> getActiveRelationsInTimespan(
            @ApiParam("开始时间（必填）") @RequestParam String startTime,
            @ApiParam("结束时间（必填）") @RequestParam String endTime,
            @ApiParam("节点ID（非必填）") @RequestParam(required = false) String nodeId) {

        // 验证必填参数
        if (startTime == null || startTime.isEmpty() || endTime == null || endTime.isEmpty()) {
            return ResponseEntity.badRequest().body("开始时间和结束时间为必填项");
        }

        Map<String, Object> result = ontologyService.getActiveRelationsInTimespan(startTime, endTime, nodeId);
        return ResponseEntity.ok(result);
    }

    @ApiOperation("批量创建节点")
    @ApiResponses({
            @ApiResponse(code = 200, message = "创建成功"),
            @ApiResponse(code = 400, message = "参数错误")
    })
    @PostMapping("/nodes/batch")
    public List<OntologyNode> batchCreateNodes(@ApiParam("节点信息列表") @RequestBody List<OntologyNode> nodes) {
        List<OntologyNode> createdNodes = new ArrayList<>();
        for (OntologyNode node : nodes) {
            createdNodes.add(ontologyService.createNode(node));
        }
        return createdNodes;
    }
} 