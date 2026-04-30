package org.example.demo;

import java.util.Arrays;
import java.util.List;

import org.example.node.DagNode;
import org.example.resolver.CallChainResolver;
import org.example.resolver.print.PrintDag;
import org.example.rule.IPreciseRule;

public class Main {
    public static void main(String[] args) throws Exception {
        // 创建解析器所需要的参数
        String sourceRoot = System.getProperty("user.dir") + "/src/main/java"; // 项目路径下 /src/main/java 中
        List<String> symbolSolverPaths = Arrays.asList(sourceRoot); // 符号解析路径，javaparser 的 CombinedTypeSolver 所需 add 的路径
        IPreciseRule preciseRule = new MyCustomRule(); // 自定义的过滤规则
        boolean isConnected = true; // 建立多个 Dag 时是否要求连通
        // 开始解析所需要的入口方法参数
        String startClass = "org.example.demo.callchain.Level1"; // 类包路径
        // String startMethod = "level1_func8"; // 入口方法
        String startMethod = "level1_func6"; // 入口方法
        // List<String> methodParams = Arrays.asList("java.lang.String", "int"); // 方法参数
        List<String> methodParams = Arrays.asList(); // 方法参数

        System.out.println("=======================================");
        System.out.println("精准测试 - 方法调用链路解析：\n");
        System.out.println("......\n");

        // 关键：创建解析器
        CallChainResolver resolver = new CallChainResolver(sourceRoot, symbolSolverPaths, preciseRule, isConnected);

        // 关键：从指定入口开始解析
        DagNode rootNode = resolver.resolveCallChain(startClass, startMethod, methodParams);

        System.out.println("精准测试 - 解析完成，开始打印：\n");

        // 从上到下打印
        PrintDag printDag = new PrintDag();
        printDag.printSimpleCallChains(rootNode);

        // 打印第一个调用链的树形结构
        System.out.println("\n打印输出完成！");
        System.out.println("=======================================");
    }
}
