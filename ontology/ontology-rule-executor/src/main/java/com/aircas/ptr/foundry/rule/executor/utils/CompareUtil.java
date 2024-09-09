package com.aircas.ptr.foundry.rule.executor.utils;

import java.util.Map;

public class CompareUtil {

    public static boolean compare(String key,String condition,Object value1, Object value2,Map<String,String> data){
        if (condition.equals("GT")){
            return gt(value1,value2,data);
        }else if (condition.equals("LT")){
            return lt(value1,value2,data);
        }else if (condition.equals("EQ")){
            return eq(value1,value2,data);
        }else if (condition.equals("CH")){
            return change(key,value1,value2,data);
        }
        return false;
    }

    public static boolean gt(Object value1, Object value2, Map<String,String> data){
        if (value1==null || value2==null){
            return false;
        }
        String tmp = value2+"";
        try{
            if (tmp.indexOf("#{")==0 && tmp.indexOf("}")==tmp.length()){
                String key = tmp.substring(2,tmp.length()-1);
                value2 = data.get(key);
            }
            if (Double.parseDouble(value1+"")>Double.parseDouble(value2+"")){
                return true;
            }
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
        return false;
    }

    public static boolean lt(Object value1, Object value2, Map<String,String> data){
        if (value1==null || value2==null){
            return false;
        }
        String tmp = value2+"";
        try{
            if (tmp.indexOf("#{")==0 && tmp.indexOf("}")==tmp.length()){
                String key = tmp.substring(2,tmp.length()-1);
                value2 = data.get(key);
            }
            if (Double.parseDouble(value1+"")<Double.parseDouble(value2+"")){
                return true;
            }
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
        return false;
    }

    public static boolean eq(Object value1, Object value2, Map<String,String> data){
        if (value1==null || value2==null){
            return false;
        }
        String tmp = value2+"";
        try{
            if (tmp.indexOf("#{")==0 && tmp.indexOf("}")==tmp.length()){
                String key = tmp.substring(2,tmp.length()-1);
                value2 = data.get(key);
            }
            String tmp1 = value1+"";
            String tmp2 = value2+"";
            if (tmp1.equals(tmp2)){
                return true;
            }
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
        return false;
    }


    public static boolean change(String key,Object value1, Object value2,Map<String,String> data){
        try{
            String tmp1 = value1+"";
            String tmp2 = data.get(key)+"";
            if (!tmp1.equals(tmp2)){
                return true;
            }
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
        return false;
    }
}
