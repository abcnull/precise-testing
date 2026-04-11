package org.example.resolver.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ReceiverParameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.UnknownType;
import com.github.javaparser.ast.type.VoidType;

import org.apache.commons.collections.CollectionUtils;

import java.util.List;

/**
 * 类解析器
 * 专门去解析类
 */
public class ClassParser {

    /**
     * 获取CompilationUnit中的第一个类型声明 DONE
     *
     * @param cu 编译单元
     * @return 第一个类型声明，如果不存在则返回null
     */
    public TypeDeclaration<?> getFirstTypeDeclaration(CompilationUnit cu) {
        if (cu != null && !cu.getTypes().isEmpty()) {
            return cu.getTypes().get(0);
        }
        return null;
    }

    /**
     * 查找方法声明 DONE
     *
     * @param cu         编译单元
     * @param methodName 方法名
     * @param paramTypes 参数类型列表，比如：["String", "Object", "int"]
     * @return 方法声明
     */
    public MethodDeclaration parseOutMethodDeclaration(CompilationUnit cu, String methodName, List<String> paramTypes) {
        // 如果该类是 jdk/第三方依赖类
        if (cu == null) {
            return null;
        }

        // 一般的方法
        MethodDeclaration methodDeclaration = parseOutUsualMethodDeclaration(cu, methodName, paramTypes);
        if (methodDeclaration != null) {
            return methodDeclaration;
        }

        // 构造器方法
        ConstructorDeclaration constructorDeclaration = parseOutConstructorDeclaration(cu, methodName, paramTypes);
        if (constructorDeclaration != null) {
            return new MethodDeclaration(
                    constructorDeclaration.getModifiers(),
                    constructorDeclaration.getAnnotations(),
                    constructorDeclaration.getTypeParameters(),
                    new UnknownType(),
                    constructorDeclaration.getName(),
                    constructorDeclaration.getParameters(),
                    constructorDeclaration.getThrownExceptions(),
                    constructorDeclaration.getBody(),
                    constructorDeclaration.getReceiverParameter().orElse(null));
        }

        return null;
    }

    /**
     * 查找一般方法声明 DONE
     *
     * @param cu         编译单元
     * @param methodName 方法名
     * @param paramTypes 参数类型列表，比如：["String", "Object", "int"]
     * @return 方法声明
     */
    private MethodDeclaration parseOutUsualMethodDeclaration(CompilationUnit cu, String methodName,
            List<String> paramTypes) {
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals(methodName))
                // 参数数量和类型都匹配时才通过
                .filter(method -> {
                    if (method.getParameters().isEmpty() && CollectionUtils.isEmpty(paramTypes)) {
                        // 无参
                        return true;
                    }
                    if (method.getParameters().size() != paramTypes.size()) {
                        // 参数数量不匹配
                        return false;
                    }
                    // 逐一比较参数类型（使用简单名称）
                    for (int i = 0; i < paramTypes.size(); i++) {
                        String actual = method.getParameter(i).getType().asString();
                        String expected = paramTypes.get(i);
                        // 进行更严格的匹配：
                        if (!typeMatches(actual, expected)) {
                            return false;
                        }
                    }
                    return true;
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * 查找构造器方法声明 DONE
     * 
     * @param cu         编译单元
     * @param methodName 构造器方法名
     * @param paramTypes 参数类型列表，比如：["String", "Object", "int"]
     * @return 构造器方法声明
     */
    private ConstructorDeclaration parseOutConstructorDeclaration(CompilationUnit cu, String methodName,
            List<String> paramTypes) {
        return cu.findAll(ConstructorDeclaration.class).stream()
                .filter(constructor -> constructor.getNameAsString().equals(methodName))
                // 参数数量和类型都匹配时才通过
                .filter(method -> {
                    if (method.getParameters().isEmpty() && CollectionUtils.isEmpty(paramTypes)) {
                        // 无参
                        return true;
                    }
                    if (method.getParameters().size() != paramTypes.size()) {
                        // 参数数量不匹配
                        return false;
                    }
                    // 逐一比较参数类型（使用简单名称）
                    for (int i = 0; i < paramTypes.size(); i++) {
                        String actual = method.getParameter(i).getType().asString();
                        String expected = paramTypes.get(i);
                        // 进行更严格的匹配：
                        if (!typeMatches(actual, expected)) {
                            return false;
                        }
                    }
                    return true;
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * 判断实际方法参数类型字符串与期望类型字符串是否匹配。DONE
     * 
     * 支持以下情况：
     * 1. 带或不带包名的普通类（如 "java.util.List" 与 "List"）
     * 2. 泛型写法（如 "Map<String, Integer>" 与 "Map"、"Map<String,Integer>"）
     * 3. 完全相同的泛型写法（如 "Map<String,Integer>" 与 "Map<String,Integer>"）
     * 4. 数组类型（如 "int[]" 与 "int[]"）
     */
    private boolean typeMatches(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        // 去掉两端空格
        actual = actual.trim();
        expected = expected.trim();

        // 直接相等（包括完整的泛型、数组等）
        if (actual.equals(expected)) {
            return true;
        }

        // 去掉泛型
        String actualBase = actual.split("<")[0];
        String expectedBase = expected.split("<")[0];

        // 如果都带有包名，则直接比较
        if (actualBase.contains(".") && expectedBase.contains(".")) {
            return actualBase.equals(expectedBase);
        }

        // 保留类名（去掉包路径）
        if (actualBase.contains(".")) {
            actualBase = actualBase.substring(actualBase.lastIndexOf('.') + 1);
        }
        if (expectedBase.contains(".")) {
            expectedBase = expectedBase.substring(expectedBase.lastIndexOf('.') + 1);
        }
        return actualBase.equals(expectedBase);
    }
}