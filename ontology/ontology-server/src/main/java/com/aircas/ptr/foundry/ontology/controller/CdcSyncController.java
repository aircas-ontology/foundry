package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.param.CdcFullSyncParam;
import com.aircas.ptr.foundry.ontology.model.vo.CdcFullSyncResultVO;
import com.aircas.ptr.foundry.ontology.service.CdcFullSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "CDC同步接口")
@RestController
@RequestMapping("/cdc")
@RequiredArgsConstructor
@Validated
public class CdcSyncController {

    private final CdcFullSyncService cdcFullSyncService;

    /**
     * CDC 初始化全量同步：以数据库当前内容为准，将主库（空间/本体/属性）与数据湖实例
     * 全量重建到 ES 索引并清理孤儿文档，语义等价 Debezium initial snapshot。
     * <p>
     * 幂等：文档按主键 _id upsert + 差集清理，可安全重复调用；
     * 并发保护：同一实例同时只允许一个全量同步，重复触发返回 409。
     */
    @PostMapping("/sync/full")
    @Operation(summary = "CDC初始化全量同步（幂等）")
    public RestResult<CdcFullSyncResultVO> fullSync(@RequestBody(required = false) CdcFullSyncParam param) {
        return RestResult.ofData(cdcFullSyncService.fullSync(param));
    }
}
