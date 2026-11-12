package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.OntologyInstanceUrlSaveParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyInstanceUrlVO;
import com.aircas.ptr.foundry.ontology.service.OntologyInstanceUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.util.List;

/**
 * 领域空间实例 URL 接口：按（空间 + 对象 + 实例）保存并查询实例对应的 URL。
 * <p>
 * 落库到主库 {@code ontology.ontology_instance_url}，唯一键就是这三个 id，所以 POST 是覆盖写：
 * 同一实例再次提交只会更新 url 与存入时间，不新增行。id 与存入时间由后台生成，请求体不接收。
 * <p>
 * 本路径不在 {@code WebMvcConfig} 的鉴权白名单内，默认要求 JWT。
 */
@Tag(name = "领域空间实例 URL")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/instance/url")
public class OntologyInstanceUrlController {

    private final OntologyInstanceUrlService ontologyInstanceUrlService;

    @Operation(summary = "保存实例 URL（按空间+对象+实例唯一，已存在则覆盖 url 并把存入时间刷成本次时间）")
    @PostMapping
    public RestResult<OntologyInstanceUrlVO> save(@RequestBody @Valid OntologyInstanceUrlSaveParam param) {
        return RestResult.ofData(ontologyInstanceUrlService.saveInstanceUrl(param));
    }

    @Operation(summary = "查询实例 URL（spaceId 必填；objectId / instanceId 不传则查该空间下全部，按存入时间倒序）")
    @GetMapping("/list")
    public RestResult<List<OntologyInstanceUrlVO>> list(
            @RequestParam @SpaceIdVerify @Parameter(description = "对象空间 id") Integer spaceId,
            @RequestParam(required = false) @Parameter(description = "对象 id（ontology_meta.unique_identifier）") String objectId,
            @RequestParam(required = false) @Parameter(description = "实例 id") String instanceId) {
        return RestResult.ofData(ontologyInstanceUrlService.listInstanceUrls(spaceId, objectId, instanceId));
    }
}
