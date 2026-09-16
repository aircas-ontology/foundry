package com.aircas.ptr.foundry.ontology.service.impl;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TaskProcessor {

    @Resource(name = "taskExecutor")
    private TaskExecutor executor;


    public <T, R> List<R> processTask(List<T> taskList, Function<List<T>, R> function, int batchSize) {
        List<CompletableFuture<R>> futures = Lists.newArrayList();
        List<List<T>> partitions = Lists.partition(taskList, batchSize);
        //submit task
        for (List<T> task : partitions) {
            CompletableFuture<R> future = CompletableFuture.supplyAsync(() -> function.apply(task), executor);
            futures.add(future);
        }
        //wait all tasks done
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return futures.stream().map(future -> {
            try {
                return future.get();
            } catch (Exception e) {
                log.error("任务处理异常：" + e.getMessage());
                return null;
            }
        }).collect(Collectors.toList());
    }
}
