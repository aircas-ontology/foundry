package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.ontology.service.OntologyDomainService;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Api(tags = "本体领域")
@RestController
@RequestMapping("/domain")
public class OntologyDomainController {

    @Resource
    OntologyDomainService ontologyDomainService;

    @ApiOperation("查询某一领域下可用的本体")
    @GetMapping("/getOntologyByDomain")
    public DataResult<List<OntologyVO>> getOntologyByDomain(@RequestParam String domain, @RequestParam String name) {

        List<OntologyVO> OntologyInfoList = ontologyDomainService.getOntologyByDomain(domain, name)
                .stream()
                .filter(catalogBO -> catalogBO.getStatus().equals(Status.ENABLE.getValue()))
                .map(e -> {
                    OntologyVO ontologyInfo = new OntologyVO();
                    ontologyInfo.setId(e.getId());
                    ontologyInfo.setName(e.getName());
                    ontologyInfo.setDomain(e.getDomain());
                    ontologyInfo.setDesc(e.getDesc());
                    ontologyInfo.setIcon(e.getIcon());
                    return ontologyInfo;
                })
                .collect(Collectors.toList());
        return DataResult.ofData(OntologyInfoList);
    }

    @ApiOperation("统计某领域下的本体数量")
    @GetMapping("/getCountByDomain")
    public DataResult<Long> getCountByDomain(@RequestParam String domain) {
        return DataResult.ofData(ontologyDomainService.getCountByDomain(domain));
    }
}
