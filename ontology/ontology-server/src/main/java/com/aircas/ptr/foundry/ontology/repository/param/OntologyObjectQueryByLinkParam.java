package com.aircas.ptr.foundry.ontology.repository.param;

import com.aircas.ptr.foundry.ontology.entity.vo.ObjectOneInfoVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "本体数据查询，依据链接id")
public class OntologyObjectQueryByLinkParam {

    @ApiModelProperty(value = "本体id", example = "545649a4-8bba-4d0c-b265-362e95fd4ffb", required = true)
    private String ontologyId;

    @ApiModelProperty(value = "关系id", example = "7b4ff29f-44b5-425a-9979-4305b6f60293", required = true)
    private String linkId;

    @ApiModelProperty(value = "页数", required = false, example = "1")
    private Integer page;

    @ApiModelProperty(value = "条数", required = false, example = "10")
    private Integer size;

    @ApiModelProperty(value = "实体对象", required = true, example = "{\n" +
            "  \"primaryKey\": \"342454\",\n" +
            "  \"displayName\": \"WMEC615信任号\",\n" +
            "  \"properties\": [\n" +
            "    {\n" +
            "      \"isTitleKey\": 0,\n" +
            "      \"isPrimaryKey\": 0,\n" +
            "      \"displayName\": \"国家地区\",\n" +
            "      \"description\": \"国家地区情况\",\n" +
            "      \"propertyType\": \"String\",\n" +
            "      \"apiName\": \"gjdq\",\n" +
            "      \"value\": \"美国\",\n" +
            "      \"uniqueIdentifier\": \"545649a4-8bba-4d0c-b265-362e85fd4fe4\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"isTitleKey\": 0,\n" +
            "      \"isPrimaryKey\": 1,\n" +
            "      \"displayName\": \"编号\",\n" +
            "      \"description\": \"这是目标的编号\",\n" +
            "      \"propertyType\": \"String\",\n" +
            "      \"apiName\": \"mbbh\",\n" +
            "      \"value\": \"342454\",\n" +
            "      \"uniqueIdentifier\": \"545649a4-8bba-4d0c-b265-362e85fd4fe1\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"isTitleKey\": 0,\n" +
            "      \"isPrimaryKey\": 0,\n" +
            "      \"displayName\": \"隶属单位\",\n" +
            "      \"description\": \"\",\n" +
            "      \"propertyType\": \"String\",\n" +
            "      \"apiName\": \"lsdw\",\n" +
            "      \"value\": \"ts20231222125838_106892609_106892609HYTSSJ.rar\",\n" +
            "      \"uniqueIdentifier\": \"ac363ca2-a73c-4a89-b40d-971140f1d1fe\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"isTitleKey\": 0,\n" +
            "      \"isPrimaryKey\": 0,\n" +
            "      \"displayName\": \"服役状态\",\n" +
            "      \"description\": \"服役状态\",\n" +
            "      \"propertyType\": \"String\",\n" +
            "      \"apiName\": \"fyzt\",\n" +
            "      \"value\": \"现役\",\n" +
            "      \"uniqueIdentifier\": \"545649a4-8bba-4d0c-b265-362e85fd4fe9\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"isTitleKey\": 0,\n" +
            "      \"isPrimaryKey\": 0,\n" +
            "      \"displayName\": \"目标类别\",\n" +
            "      \"description\": \"\",\n" +
            "      \"propertyType\": \"String\",\n" +
            "      \"apiName\": \"mblb\",\n" +
            "      \"value\": \"水面目标\",\n" +
            "      \"uniqueIdentifier\": \"397d4334-3f4e-47dc-82e2-c27c5215062c\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"isTitleKey\": 0,\n" +
            "      \"isPrimaryKey\": 0,\n" +
            "      \"displayName\": \"威胁半径\",\n" +
            "      \"description\": \"\",\n" +
            "      \"propertyType\": \"Decimal\",\n" +
            "      \"apiName\": \"wxbj\",\n" +
            "      \"value\": \"-10000.00000000000000000000000000000000000000000000000000000\",\n" +
            "      \"uniqueIdentifier\": \"4b1041ef-a86b-4124-bb13-9fb3ae705033\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"isTitleKey\": 0,\n" +
            "      \"isPrimaryKey\": 0,\n" +
            "      \"displayName\": \"是否单目标\",\n" +
            "      \"description\": \"\",\n" +
            "      \"propertyType\": \"String\",\n" +
            "      \"apiName\": \"dmb\",\n" +
            "      \"value\": \"是\",\n" +
            "      \"uniqueIdentifier\": \"38f48828-e54a-4e2c-9317-eff82c96e73c\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"isTitleKey\": 0,\n" +
            "      \"isPrimaryKey\": 0,\n" +
            "      \"displayName\": \"敌我属性\",\n" +
            "      \"description\": \"是否敌人\",\n" +
            "      \"propertyType\": \"String\",\n" +
            "      \"apiName\": \"dwsx\",\n" +
            "      \"value\": \"敌\",\n" +
            "      \"uniqueIdentifier\": \"545649a4-8bba-4d0c-b265-362e85fd4fe3\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"isTitleKey\": 0,\n" +
            "      \"isPrimaryKey\": 0,\n" +
            "      \"displayName\": \"目标性质\",\n" +
            "      \"description\": \"\",\n" +
            "      \"propertyType\": \"String\",\n" +
            "      \"apiName\": \"mbxz\",\n" +
            "      \"value\": \"军用目标\",\n" +
            "      \"uniqueIdentifier\": \"8a5d1197-fd7e-4422-a6cf-e7565102827e\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"isTitleKey\": 0,\n" +
            "      \"isPrimaryKey\": 0,\n" +
            "      \"displayName\": \"机舷号\",\n" +
            "      \"description\": \"机舷号\",\n" +
            "      \"propertyType\": \"String\",\n" +
            "      \"apiName\": \"jxh\",\n" +
            "      \"value\": \"WMEC615\",\n" +
            "      \"uniqueIdentifier\": \"545649a4-8bba-4d0c-b265-362e85fd4fe6\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"isTitleKey\": 0,\n" +
            "      \"isPrimaryKey\": 0,\n" +
            "      \"displayName\": \"吨数\",\n" +
            "      \"description\": \"吨数\",\n" +
            "      \"propertyType\": \"String\",\n" +
            "      \"apiName\": \"ds\",\n" +
            "      \"value\": \"1020\",\n" +
            "      \"uniqueIdentifier\": \"545649a4-8bba-4d0c-b265-362e85fd4f10\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"isTitleKey\": 0,\n" +
            "      \"isPrimaryKey\": 0,\n" +
            "      \"displayName\": \"目标种类\",\n" +
            "      \"description\": \"目标种类\",\n" +
            "      \"propertyType\": \"String\",\n" +
            "      \"apiName\": \"mbzl\",\n" +
            "      \"value\": \"海警船\",\n" +
            "      \"uniqueIdentifier\": \"545649a4-8bba-4d0c-b265-362e85fd4f12\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"isTitleKey\": 1,\n" +
            "      \"isPrimaryKey\": 0,\n" +
            "      \"displayName\": \"名称\",\n" +
            "      \"description\": \"这是目标的名字\",\n" +
            "      \"propertyType\": \"String\",\n" +
            "      \"apiName\": \"mbmc\",\n" +
            "      \"value\": \"WMEC615信任号\",\n" +
            "      \"uniqueIdentifier\": \"545649a4-8bba-4d0c-b265-362e85fd4fe2\"\n" +
            "    },\n" +
            "    {\n" +
            "      \"isTitleKey\": 0,\n" +
            "      \"isPrimaryKey\": 0,\n" +
            "      \"displayName\": \"装备型号\",\n" +
            "      \"description\": \"装备型号\",\n" +
            "      \"propertyType\": \"String\",\n" +
            "      \"apiName\": \"zbxh\",\n" +
            "      \"value\": \"“信任”级\",\n" +
            "      \"uniqueIdentifier\": \"545649a4-8bba-4d0c-b265-362e85fd4f11\"\n" +
            "    }\n" +
            "  ]\n" +
            "}")
    private ObjectOneInfoVO obj;
}
