package org.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.example.callchain2.MyCustomRule;
import org.example.resolver.CallChainResolver;
import org.example.node.AstNode;
import org.example.node.field.ClassInfo;
import org.example.node.field.FuncInfo;

import com.github.javaparser.ast.Modifier.Keyword;

public class Main {

    // public static void main(String[] args) {
    //     // 获取项目根目录
    //     String projectRoot = System.getProperty("user.dir");
    //     String sourceRoot = projectRoot + "/src/main/java";

    //     System.out.println("=======================================");
    //     System.out.println("       精准测试 - 方法调用链路解析器");
    //     System.out.println("=======================================\n");

    //     // 创建解析器
    //     CallChainResolver resolver = new CallChainResolver(sourceRoot);

    //     // 从 Level1.level1Func 开始解析调用链路
    //     String startClass = "org.example.callchain2.Level1";
    //     String startMethod = "level1Func";

    //     System.out.println("开始解析调用链路...");
    //     System.out.println("起始方法: " + startClass + "." + startMethod + "(String, int)\n");

    //     // 解析调用链
    //     AstNode rootNode = resolver.resolveCallChain(
    //             startClass,
    //             startMethod,
    //             new ArrayList<>()
    //     );
        
    //     // 从顶到底部的调用关系（访问孩子节点）
    //     System.out.println("=======================================");
    //     System.out.println("         从顶到底部的调用关系");
    //     System.out.println("=======================================");
    //     System.out.println();
    //     printAstTree(rootNode, 0, true, "");

    //     // 从底部到顶部的被调用关系（通过先找到底部，再访问父节点来实现）
    //     System.out.println();
    //     System.out.println("=======================================");
    //     System.out.println("         从底部到顶部的被调用关系");
    //     System.out.println("=======================================");
    //     System.out.println();
    //     printCallHierarchy(rootNode);
    // }


    public static void main(String[] args) {
        // 获取项目根目录
        String projectRoot = System.getProperty("user.dir");
        String sourceRoot = projectRoot + "/src/main/java";

        System.out.println("=======================================");
        System.out.println("       精准测试 - 方法调用链路解析器");
        System.out.println("=======================================\n");

        // 创建解析器
        // 写一个匿名类继承 CustomRule 
        CallChainResolver resolver = new CallChainResolver(sourceRoot, new MyCustomRule());

        // 从 Level1.level1Func 开始解析调用链路
        String startClass = "org.example.callchain.Level1";
        String startMethod = "level1Func";

        System.out.println("开始解析调用链路...");
        System.out.println("起始方法: " + startClass + "." + startMethod + "(String, int)\n");

        // 解析第一个调用链（org.example.callchain.Level1）
        AstNode rootNode1 = resolver.resolveCallChain(
                startClass,
                startMethod,
                Arrays.asList("String", "int")
        );

        // 解析第二个调用链（org.example.callchain2.Level1）
        String startClass2 = "org.example.callchain2.Level1";
        String startMethod2 = "level1Func";
        System.out.println("开始解析第二个调用链路...");
        System.out.println("起始方法: " + startClass2 + "." + startMethod2 + "()");
        AstNode rootNode2 = resolver.resolveCallChain(
                startClass2,
                startMethod2,
                Arrays.asList()
        );

        // 合并两个 AstNode
        System.out.println("开始合并两个调用链...\n");
        resolver.mergeCallChain(rootNode1, rootNode2);
        
        // 测试合并多个 AstNode
        System.out.println("开始测试合并多个调用链...\n");
        List<AstNode> nodesToMerge = new ArrayList<>();
        nodesToMerge.add(rootNode1);
        nodesToMerge.add(rootNode2);
        resolver.mergeCallChain(nodesToMerge);
        System.out.println("多个调用链合并完成！\n");

        // 打印合并后的AST树形结构（从第一个根节点开始）
        System.out.println("=======================================");
        System.out.println("        合并后的调用链路AST树形结构");
        System.out.println("=======================================\n");
        System.out.println("【从第一个根节点遍历】");
        printAstTree(rootNode1, 0, true, "");

        // 打印合并后的AST树形结构（从第二个根节点开始）
        System.out.println("\n=======================================");
        System.out.println("【从第二个根节点遍历】");
        printAstTree(rootNode2, 0, true, "");

        // 从 rootNode2 的叶子节点向上遍历
        System.out.println("\n=======================================");
        System.out.println("【从第二个根节点的叶子节点向上遍历】");
        printCallHierarchy(rootNode2);

        // 测试 reverseCallChainStr 方法
        System.out.println("\n=======================================");
        System.out.println("         测试 reverseCallChainStr 方法");
        System.out.println("=======================================");
        
        // 找到一个叶子节点进行测试
        if (rootNode2.getChildren() != null && !rootNode2.getChildren().isEmpty()) {
            AstNode testNode = rootNode2.getChildren().get(0);
            System.out.println("测试节点: " + testNode.getFuncInfo().getFuncName() + "()");
            System.out.println("\n从该节点到出口节点的所有链路:");
            
            java.util.List<String> chains = resolver.reverseCallChainStr(testNode);
            for (int i = 0; i < chains.size(); i++) {
                System.out.println((i + 1) + ". " + chains.get(i));
            }
            
            // 测试 reverseLeavesName 方法
            System.out.println("\n=======================================");
            System.out.println("         测试 reverseLeavesName 方法");
            System.out.println("=======================================");
            System.out.println("测试节点: " + testNode.getFuncInfo().getFuncName() + "()");
            System.out.println("\n从该节点到出口节点的所有出口节点:");
            
            java.util.List<String> exitNodes = resolver.reverseLeavesName(testNode);
            for (int i = 0; i < exitNodes.size(); i++) {
                System.out.println((i + 1) + ". " + exitNodes.get(i));
            }
        }

        System.out.println("\n=======================================");
        System.out.println("              解析完成");
        System.out.println("=======================================");


        
    }

    /**
     * 以树形结构打印AST节点
     *
     * @param node       当前节点
     * @param depth      当前深度
     * @param isLast     是否是最后一个子节点
     * @param prefix     前缀字符串（用于绘制连接线）
     */
    private static void printAstTree(AstNode node, int depth, boolean isLast, String prefix) {
        if (node == null || node.getFuncInfo() == null) {
            return;
        }

        FuncInfo info = node.getFuncInfo();

        // 构建当前节点的连接线
        String connector = depth == 0 ? "" : (isLast ? "└── " : "├── ");
        String currentPrefix = prefix + connector;

        // 打印当前节点信息
        if (depth == 0) {
            System.out.println("【根节点】");
        }

        // 方法签名
        String methodSignature = buildMethodSignature(node);
        System.out.println(currentPrefix + methodSignature);

        // 递归打印子节点
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            int childCount = node.getChildren().size();
            for (int i = 0; i < childCount; i++) {
                boolean childIsLast = (i == childCount - 1);
                String childPrefix = prefix + (isLast ? "    " : "│   ");
                printAstTree(node.getChildren().get(i), depth + 1, childIsLast, childPrefix);
            }
        }
    }

    /**
     * 构建方法签名字符串
     * 格式: [注解列表] 修饰符列表 返回值类型（带包名）#类名（带包名）#方法名(参数类型列表（带包名）)
     */
    private static String buildMethodSignature(AstNode node) {
        FuncInfo info = node.getFuncInfo();
        ClassInfo classInfo = node.getClassInfo();
        StringBuilder sb = new StringBuilder();

        // 方法注解
        if (info.getAnnotations() != null && !info.getAnnotations().isEmpty()) {
            for (java.util.Map.Entry<String, java.util.Map<String, Object>> entry : info.getAnnotations().entrySet()) {
                String annotationName = entry.getKey();
                java.util.Map<String, Object> params = entry.getValue();
                
                sb.append("@").append(annotationName);
                
                // 如果有参数，显示参数
                if (!params.isEmpty()) {
                    sb.append("(");
                    List<String> paramList = new ArrayList<>();
                    for (java.util.Map.Entry<String, Object> param : params.entrySet()) {
                        String paramValue = formatAnnotationValue(param.getValue());
                        if ("value".equals(param.getKey()) && params.size() == 1) {
                            // 单参数且为value时，只显示值
                            paramList.add(paramValue);
                        } else {
                            paramList.add(param.getKey() + "=" + paramValue);
                        }
                    }
                    sb.append(String.join(", ", paramList));
                    sb.append(")");
                }
                sb.append(" ");
            }
        }

        // 方法修饰符 - 使用 Modifier.Keyword
        if (info.getMethodModifiers() != null && !info.getMethodModifiers().isEmpty()) {
            for (com.github.javaparser.ast.Modifier.Keyword keyword : info.getMethodModifiers()) {
                sb.append(keyword.asString());
            }
            sb.append(" ");
        }

        // 返回值类型（带包名）
        String returnType = info.getFuncReturnType();
        String returnPackage = info.getFuncReturnPackageName();
        if (returnPackage != null && !returnPackage.isEmpty()) {
            sb.append(returnPackage).append(".").append(returnType);
        } else {
            sb.append(returnType);
        }
        sb.append("#");

        // 包名.类名
        String packageName = "";
        if (node.getPackageInfo() != null) {
            packageName = node.getPackageInfo().getPackageName();
        }
        if (packageName != null && !packageName.isEmpty()) {
            sb.append(packageName).append(".");
        }
        if (classInfo != null) {
            sb.append(classInfo.getClassName());
        }

        // 方法名
        sb.append("#").append(info.getFuncName());
        
        // 循环调用标记
        if (node.isLoopCall()) {
            sb.append(" [循环调用]");
        }

        // 参数列表（带包名）
        sb.append("(");
        if (info.getFuncParams() != null && !info.getFuncParams().isEmpty()) {
            List<String> fullParams = new ArrayList<>();
            List<String> params = info.getFuncParams();
            List<String> paramPackages = info.getFuncParamsPackageName();
            
            for (int i = 0; i < params.size(); i++) {
                String param = params.get(i);
                String paramPackage = (paramPackages != null && i < paramPackages.size() && paramPackages.get(i) != null) 
                    ? paramPackages.get(i) : "";
                
                if (!paramPackage.isEmpty()) {
                    fullParams.add(paramPackage + "." + param);
                } else {
                    fullParams.add(param);
                }
            }
            sb.append(String.join(", ", fullParams));
        }
        sb.append(")");

        // 方法分类
        if (info.getFuncCate() != null) {
            sb.append(" [分类: " + info.getFuncCate().asString() + "]");
        }

        // 类修饰符和类声明类型
        if (classInfo != null) {
            if (classInfo.getClassModifiers() != null && !classInfo.getClassModifiers().isEmpty()) {
                sb.append(" [类修饰符: [" + classInfo.getClassModifiers().stream().map(Keyword::asString).collect(Collectors.joining(", ")) + "]]");
            }
            if (classInfo.getClassDeclaration() != null) {
                sb.append(" [类类型: " + classInfo.getClassDeclaration().asString() + "]");
            }
            if (classInfo.getClassOrigin() != null) {
                sb.append(" [类来源: " + classInfo.getClassOrigin().getOrigin() + "]");
            }
        }

        return sb.toString();
    }

    /**
     * 格式化注解参数值
     */
    private static String formatAnnotationValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "\"" + value + "\"";
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatAnnotationValue(list.get(i)));
            }
            sb.append("}");
            return sb.toString();
        }
        return value.toString();
    }

    /**
     * 打印从底部到顶部的被调用关系
     * 先找到所有叶节点，然后通过父节点向上遍历
     */
    private static void printCallHierarchy(AstNode rootNode) {
        // 收集所有叶节点（没有子节点的节点）
        List<AstNode> leafNodes = new ArrayList<>();
        collectLeafNodes(rootNode, leafNodes);

        // 打印叶节点数量
        System.out.println("总共有 " + leafNodes.size() + " 个叶子节点：\n");

        // 对每个叶节点，打印其到根节点的路径
        for (int i = 0; i < leafNodes.size(); i++) {
            AstNode leafNode = leafNodes.get(i);
            System.out.println("叶节点 " + (i + 1) + ":");
            System.out.println("  节点信息: " + buildMethodSignature(leafNode));
            
            // 打印父节点数量
            int parentCount = leafNode.getParents() != null ? leafNode.getParents().size() : 0;
            System.out.println("  父节点数量: " + parentCount);
            
            // 打印所有父节点路径
            if (leafNode.getParents() != null && !leafNode.getParents().isEmpty()) {
                for (int j = 0; j < leafNode.getParents().size(); j++) {
                    AstNode parent = leafNode.getParents().get(j);
                    System.out.println("  父节点 " + (j + 1) + " 路径:");
                    printPathToRoot(parent, 2);
                }
            } else {
                System.out.println("  没有父节点");
            }
            System.out.println();
        }
    }

    /**
     * 收集所有叶节点
     */
    private static void collectLeafNodes(AstNode node, List<AstNode> leafNodes) {
        if (node == null) {
            return;
        }

        // 如果是叶节点（没有子节点或子节点都是循环调用）
        boolean isLeaf = true;
        if (node.getChildren() != null) {
            for (AstNode child : node.getChildren()) {
                if (!child.isLoopCall()) {
                    isLeaf = false;
                    break;
                }
            }
        }

        if (isLeaf) {
            leafNodes.add(node);
        }

        // 递归收集子节点
        if (node.getChildren() != null) {
            for (AstNode child : node.getChildren()) {
                if (!child.isLoopCall()) {
                    collectLeafNodes(child, leafNodes);
                }
            }
        }
    }

    /**
     * 打印从当前节点到根节点的路径
     */
    private static void printPathToRoot(AstNode node, int depth) {
        if (node == null) {
            return;
        }

        // 构建缩进
        StringBuilder indentBuilder = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indentBuilder.append("  ");
        }
        String indent = indentBuilder.toString();

        // 打印当前节点
        String methodSignature = buildMethodSignature(node);
        System.out.println(indent + methodSignature);

        // 递归打印父节点
        if (node.getParents() != null && !node.getParents().isEmpty()) {
            // 只打印第一个父节点，避免重复路径
            AstNode parent = node.getParents().get(0);
            printPathToRoot(parent, depth + 1);
        }
    }
}
