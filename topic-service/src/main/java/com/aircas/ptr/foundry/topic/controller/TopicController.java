package com.aircas.ptr.foundry.topic.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.topic.model.param.TopicCreateParam;
import com.aircas.ptr.foundry.topic.model.param.TopicQueryParam;
import com.aircas.ptr.foundry.topic.model.vo.TopicVO;
import com.aircas.ptr.foundry.topic.service.TopicService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 分层示例：Controller 只收参、调 Service、包 RestResult。
 * 新业务复制此链路：param / vo / po → Mapper → Service → Controller。
 */
@Tag(name = "主题")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/topic")
public class TopicController {

    private final TopicService topicService;

    @PostMapping
    @Operation(summary = "创建主题")
    public RestResult<Integer> create(@RequestBody @Valid TopicCreateParam param) {
        return RestResult.ofData(topicService.create(param));
    }

    @GetMapping("/{id}")
    @Operation(summary = "按主键查询主题")
    public RestResult<TopicVO> get(@PathVariable Integer id) {
        return RestResult.ofData(topicService.get(id));
    }

    @GetMapping
    @Operation(summary = "分页查询主题")
    public RestResult<IPage<TopicVO>> page(@Valid TopicQueryParam param) {
        return RestResult.ofData(topicService.page(param));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除主题")
    public RestResult<Void> delete(@PathVariable Integer id) {
        topicService.delete(id);
        return RestResult.success();
    }
}
