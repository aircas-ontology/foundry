import com.aircas.ptr.foundry.ontology.OntologyServerApplication
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO
import com.aircas.ptr.foundry.ontology.service.impl.FileServiceImpl
import com.google.common.collect.Lists
import groovy.util.logging.Slf4j

@Slf4j
class ShipTargetDetect {

    FunctionResultVO<List<String>> handle(String inputFilePath, String modelFilePath, String outputFilePath) {
        List<String> res = Lists.newArrayList()
        var fileService = OntologyServerApplication.context.getBean(FileServiceImpl.class)
        File output = new File(outputFilePath)
        if (output.isDirectory()) {
            for (File f : output.listFiles()) {
                res.add(fileService.getPreviewUrlByFile(f))
            }
        }
        return FunctionResultVO.builder().data(res).build();
    }
}