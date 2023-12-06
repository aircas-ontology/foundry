package com.aircas.ptr.foundry.common.util;

import java.nio.charset.Charset;

/**
 * 字符串简单加解密工具
 */
public class EncryptUtil {

    public static String encryptAndDencrypt(String msg) {
        int defaultSecret = 8;
        return encryptAndDencrypt(msg, defaultSecret);
    }

    public static String encryptAndDencrypt(String msg, int secret) {
        String defaultCharset = "gbk";
        return encryptAndDencrypt(msg, secret, defaultCharset);
    }

    public static String encryptAndDencrypt(String msg, int secret, String charsetName) {
        byte[] bt = msg.getBytes(Charset.forName(charsetName));
        for (int i = 0; i < bt.length; i++) {
            bt[i] = (byte) (bt[i] ^ secret);
        }
        String result = new String(bt, 0, bt.length, Charset.forName(charsetName));
        byte[] bs = result.getBytes(Charset.forName(charsetName));
        // System.out.println("加密后内容result：" + result);
        return result;
    }

    public static void main(String[] args) {
        String msg = "你好啊， hello world, 中国";
        System.out.println("原始内容：" + msg);
        String encryptMsg = encryptAndDencrypt(msg);
        System.out.println("加密后内容：" + encryptMsg);
        String dencryptMsg = encryptAndDencrypt(encryptMsg);
        System.out.println("解密后内容：" + dencryptMsg);

        // byte[] bs1 = msg.getBytes(Charset.forName(charsetName));
        // bs1[3] = 104;
        // String msg2 = new String(bs1, Charset.forName(charsetName));
        // byte[] bs2 = msg2.getBytes(Charset.forName(charsetName));
        // String msg3 = new String(bs2, Charset.forName(charsetName));
        System.out.println();
    }

}
