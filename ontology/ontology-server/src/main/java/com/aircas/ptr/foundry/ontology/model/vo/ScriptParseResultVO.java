package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.ScriptParseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 脚本参数解析结果：按输入/输出分组的参数定义。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "脚本参数解析结果")
public class ScriptParseResultVO {

    @Schema(description = "解析状态：OK-成功；SYNTAX_ERROR-语法错误；NO_HANDLE-缺 handle 入口；RUNNER_NOT_CONFIGURED-runner 未部署；RUNNER_ERROR-runner 报错；PARSE_EXCEPTION-解析异常")
    private ScriptParseStatus status;

    @Schema(description = "解析是否成功定位到参数（脚本无 handle 入口、完全动态写法或解析失败时为 false，前端据此回退手动声明）")
    private Boolean parsed;

    @Schema(description = "解析出的参数列表（与函数创建/更新接口的 parameters 结构完全一致：入参在前、出参在后，可直接提交保存）")
    private List<ScriptParsedParamVO> parameters;

    @Schema(description = "解析提示：失败时为具体原因（语法错误/runner 不可用等）；成功且无参数时为动态写法提示；成功解析出参数时为空")
    private String message;
}
