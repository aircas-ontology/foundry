package com.aircas.ptr.foundry.common.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class IpUtil {

	public static Set<String> getLocalIps() {
		Set<String> ips = new HashSet<String>();
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				String name = intf.getName();
				if (!name.contains("docker") && !name.contains("lo")) {
					for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
						InetAddress inetAddress = enumIpAddr.nextElement();
						if (!inetAddress.isLoopbackAddress()) {
							String ipaddress = inetAddress.getHostAddress().toString();
							if (!ipaddress.contains("::") && !ipaddress.contains("fe80")) {
								ips.add(ipaddress);
							}
						}
					}

				}
			}
		} catch (Exception e) {
			// TODO: handle exception
		}

		return ips;
	}

	public static void main(String[] args) {
		System.out.println(getLocalIps());
	}

}
