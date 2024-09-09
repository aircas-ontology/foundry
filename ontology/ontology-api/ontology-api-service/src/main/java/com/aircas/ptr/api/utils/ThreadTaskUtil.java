package com.aircas.ptr.api.utils;

import com.aircas.ptr.api.common.entity.Organization;

import java.util.HashMap;
import java.util.Map;

public class ThreadTaskUtil {

    /**
     * 本地临时任务数，应缓存到redis
     */
    private static Map<String,Integer> threadMap = new HashMap<>();

    public static synchronized int useThread(Organization organization){
        if (!threadMap.containsKey(organization.getOrganization())){
            threadMap.put(organization.getOrganization(),1);
        }
        int count = threadMap.get(organization.getOrganization());
        if (organization.getThread()>count){
            change(organization,1);
            return 1;
        }
        return 0;
    }


    public static int remove(Organization organization){
        int count = threadMap.get(organization.getOrganization());
        if (count>0){
            change(organization,-1);
            return 1;
        }
        return 0;
    }

    private static synchronized int change(Organization organization,int step){
        int count = threadMap.get(organization.getOrganization());
        threadMap.put(organization.getOrganization(),count+step);
        return 1;
    }
}
