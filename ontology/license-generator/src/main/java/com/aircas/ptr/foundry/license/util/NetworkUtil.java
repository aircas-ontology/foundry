package com.aircas.ptr.foundry.license.util;


import com.aircas.ptr.foundry.license.param.LicenseCheckModel;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class NetworkUtil {

    public static LicenseCheckModel getNetworkInfo() {
        LicenseCheckModel model = new LicenseCheckModel();
        List<String> ipAddresses = new ArrayList<>();
        List<String> macAddresses = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface ni = networkInterfaces.nextElement();

                // 获取MAC地址
                byte[] mac = ni.getHardwareAddress();
                if (mac != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
                    }
                    macAddresses.add(sb.toString());
                }

                // 获取所有IP地址
                Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress ia = inetAddresses.nextElement();
                    if (ia.isSiteLocalAddress() && !ia.isLoopbackAddress() && !ia.isLinkLocalAddress()) {
                        ipAddresses.add(ia.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }

        model.setIpAddress(ipAddresses);
        model.setMacAddress(macAddresses);

        return model;
    }


}
