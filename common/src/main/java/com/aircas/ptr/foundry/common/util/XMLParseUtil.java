package com.aircas.ptr.foundry.common.util;

import org.apache.commons.collections4.map.LinkedMap;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XMLParseUtil {

    public static String toLowerCaseFirstOne(String s) {
        String result = s;
        if (s.length() < 2) {
            return s;
        }
        if (Character.isLowerCase(s.charAt(0))) {
            return s;
        } else if (Character.isLowerCase(s.charAt(1))) {
            result = new StringBuilder().append(Character.toLowerCase(s.charAt(0)))
                    .append(s.substring(1)).toString();
        }
        if (result.endsWith("ID")) {
            result = result.substring(0, result.length() - 2) + "Id";
        }
        return result;
    }

    public static void parseElement(LinkedMap<String, String> list, String root, String originRoot, Element element, boolean isRoot) {
        NodeList nodeList = element.getChildNodes();
        Node childNode;
        if (isRoot) {
            list.put(root, root);
        }
        if (!isRoot) {
            list.put(root + "." + toLowerCaseFirstOne(element.getTagName()), (originRoot + "." + element.getTagName()));
        }
        for (int temp = 0; temp < nodeList.getLength(); temp++) {
            childNode = nodeList.item(temp);
            // 判断是否属于节点
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                // 判断是否还有子节点
                if (childNode.hasChildNodes()) {
                    if (isRoot) {
                        parseElement(list, root, root, (Element) childNode, false);
                    } else {
                        parseElement(list, root + "." + toLowerCaseFirstOne(element.getTagName()), originRoot + "." + element.getTagName(), (Element) childNode, false);
                    }
                } else if (childNode.getNodeType() != Node.COMMENT_NODE) {
                    System.out.print(childNode.getTextContent());
                }
            }
        }
    }

}
