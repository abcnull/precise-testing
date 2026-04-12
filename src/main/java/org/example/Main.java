package org.example;

import java.util.Arrays;

import org.example.callchain2.MyCustomRule;
import org.example.node.DagNode;
import org.example.resolver.CallChainResolver;

public class Main {
    public static void main(String[] args) {
        // 获取项目根目录
        String projectRoot = System.getProperty("user.dir");
        String sourceRoot = projectRoot + "/src/main/java";
        // String sourceRoot = "/Users/abcnull/IdeaProjects/appuitest4j/src/test/java";
        
        

        System.out.println("=======================================");
        System.out.println("       精准测试 - 方法调用链路解析器");
        System.out.println("=======================================\n");

        // 创建解析器
        CallChainResolver resolver = new CallChainResolver(sourceRoot, new MyCustomRule());

        // 从 Level1.level1Func 开始解析调用链路
        String startClass = "org.example.callchain.Level1";
        String startMethod = "level1Func";
        // String startClass = "com.abcnull.pageobject.page.CSDN_HomePage";
        // String startMethod = "clickFocus";

        System.out.println("开始解析第一个调用链路...");
        System.out.println("起始方法: " + startClass + "." + startMethod + "(String, int)\n");
        // System.out.println("起始方法: " + startClass + "." + startMethod + "()\n");

        // 解析第一个调用链（org.example.callchain.Level1）
        DagNode rootNode1 = resolver.resolveCallChain(
                startClass,
                startMethod,
                Arrays.asList());
                // Arrays.asList("String", "int", "int"));

        // 打印第一个调用链的树形结构
        System.out.println("=======================================");
        System.out.println("        第一个调用链路的树形结构");
        System.out.println("=======================================");
        resolver.printTree(rootNode1);

        // // 解析第二个调用链（org.example.callchain2.Level1）
        // String startClass2 = "org.example.callchain2.Level1";
        // String startMethod2 = "level1Func";
        // System.out.println("\n开始解析第二个调用链路...");
        // System.out.println("起始方法: " + startClass2 + "." + startMethod2 + "()");
        // DagNode rootNode2 = resolver.resolveCallChain(
        //         startClass2,
        //         startMethod2,
        //         Arrays.asList());

        // // 打印第二个调用链的树形结构
        // System.out.println("\n=======================================");
        // System.out.println("        第二个调用链路的树形结构");
        // System.out.println("=======================================");
        // resolver.printTree(rootNode2);

        // // 合并两个 DagNode
        // System.out.println("\n=======================================");
        // System.out.println("           合并两个调用链");
        // System.out.println("=======================================");
        // resolver.mergeCallChain(rootNode1, rootNode2);
        // System.out.println("两个调用链合并完成！\n");

        // // 找到rootNode1的某一个叶子节点
        // System.out.println("=======================================");
        // System.out.println("      从rootNode1的叶子节点向上遍历");
        // System.out.println("=======================================");
        // DagNode leafNode = findLeafNode(rootNode1);
        // if (leafNode != null) {
        //     System.out.println("找到叶子节点: " + leafNode.getFuncInfo().getFuncName() + "()");
        //     System.out.println("\n从该叶子节点向上遍历:");
        //     resolver.printReverseTree(leafNode);
        // } else {
        //     System.out.println("未找到叶子节点");
        // }

        // System.out.println("\n=======================================");
        // System.out.println("              解析完成");
        // System.out.println("=======================================");
    }

    /**
     * 查找第一个叶子节点
     */
    private static DagNode findLeafNode(DagNode node) {
        if (node == null) {
            return null;
        }

        // 如果是叶子节点（没有子节点或子节点都是循环调用）
        boolean isLeaf = true;
        if (node.getChildren() != null) {
            for (DagNode child : node.getChildren()) {
                if (!child.isLoopCall()) {
                    isLeaf = false;
                    break;
                }
            }
        }

        if (isLeaf) {
            return node;
        }

        // 递归查找子节点
        if (node.getChildren() != null) {
            for (DagNode child : node.getChildren()) {
                if (!child.isLoopCall()) {
                    DagNode leafNode = findLeafNode(child);
                    if (leafNode != null) {
                        return leafNode;
                    }
                }
            }
        }

        return null;
    }
}
