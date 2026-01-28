package com.aircas.ptr.foundry.common.util;

import java.util.UUID;

public class IdGenerator {

    public static String generateLogId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase();
    }

    public static String generateUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String generateTaskId() {
        return "task_" + System.currentTimeMillis();
    }

}
