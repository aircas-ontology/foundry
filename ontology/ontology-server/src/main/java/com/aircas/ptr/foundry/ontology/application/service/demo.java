package com.aircas.ptr.foundry.ontology.application.service;
import java.util.LinkedList;
import java.util.Queue;

class Node {
    int orgId;
    LinkedList<Node> childrenList;
}


public class demo {

    Node buildOrgTree(int orgId) {
        Node ret = new Node();  //返回值
        Queue<Node> queue = new LinkedList<>();
        Node root = new Node();
        root.orgId = orgId;
        queue.add(root);
        while (!queue.isEmpty()) {
            Node visitNode = queue.poll();
            int id = visitNode.orgId;
            LinkedList<Node> childrenList = new LinkedList<>();
            for (int childId: getChildrenOrgIds(id)) {
                Node childNode = new Node();
                childNode.orgId = childId;
                queue.add(childNode);
                childrenList.push(childNode);
            }
            visitNode.childrenList = childrenList;
        }
        return ret;
    }

    //实现获取孩子orgs
    LinkedList<Integer> getChildrenOrgIds(int parentId) {
        ///////实现
        LinkedList list = new LinkedList();
        // sql查询
        return list;
    }
}
