package org.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.example.resolver.CallChainResolver;
import org.example.treenode.AstNode;
import org.example.treenode.ClassInfo;
import org.example.treenode.FuncInfo;
import org.example.treenode.PackageInfo;

import com.github.javaparser.ast.Modifier.Keyword;

public class Main {

    public static void main(String[] args) {
        // 获取项目根目录
        String projectRoot = System.getProperty("user.dir");
        String sourceRoot = projectRoot + "/src/main/java";

        System.out.println("=======================================");
        System.out.println("       精准测试 - 方法调用链路解析器");
        System.out.println("=======================================\n");

        // 创建解析器
        CallChainResolver resolver = new CallChainResolver(sourceRoot);

        // 从 Level1.level1Func 开始解析调用链路
        String startClass = "org.example.callchain.Level1";
        String startMethod = "level1Func";

        System.out.println("开始解析调用链路...");
        System.out.println("起始方法: " + startClass + "." + startMethod + "(String, int)\n");

        // 解析调用链
        AstNode<PackageInfo, ClassInfo, FuncInfo> rootNode = resolver.resolveCallChain(
                startClass,
                startMethod,
                Arrays.asList("String", "int")
        );

        // 打印AST树形结构
        System.out.println("=======================================");
        System.out.println("           调用链路AST树形结构");
        System.out.println("=======================================\n");
        printAstTree(rootNode, 0, true, "");

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
    private static void printAstTree(AstNode<PackageInfo, ClassInfo, FuncInfo> node, int depth, boolean isLast, String prefix) {
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
    private static String buildMethodSignature(AstNode<PackageInfo, ClassInfo, FuncInfo> node) {
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
}
