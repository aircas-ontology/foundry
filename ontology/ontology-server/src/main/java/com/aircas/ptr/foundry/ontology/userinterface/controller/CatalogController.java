package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.ontology.application.service.CatalogService;
import com.aircas.ptr.foundry.ontology.entity.vo.CatalogVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Api(tags = "数据目录")
@RestController
@ApiIgnore
public class CatalogController {

    @Resource
    CatalogService catalogService;

    @ApiOperation("获取完整目录树(外部接口)")
    @GetMapping("")
    public DataResult<List<CatalogVO>> getCatalogV2() {
        List<CatalogVO> catalogInfoList = catalogService.getAllCatalog()
                .stream()
                .filter(catalogBO -> catalogBO.getStatus().equals(Status.ENABLE.getValue()))
                .map(e -> {
                    CatalogVO catalogInfo = new CatalogVO();
                    catalogInfo.setId(e.getId());
                    catalogInfo.setNodeCode(e.getNodeCode());
                    catalogInfo.setName(e.getName());
                    catalogInfo.setLevel(e.getLevel());
                    catalogInfo.setFieldCode(e.getFieldCode());
                    catalogInfo.setFieldEnumItemCode(e.getFieldEnumItemCode());
                    catalogInfo.setParentId(e.getParentId());
                    return catalogInfo;
                })
                .collect(Collectors.toList());
        return DataResult.ofData(catalogInfoList);
    }
}
