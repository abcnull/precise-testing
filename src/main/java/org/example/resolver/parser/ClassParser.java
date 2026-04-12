package org.example.resolver.parser;

import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.example.constant.PathConstant;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.UnknownType;

/**
 * 类解析器
 * 专门去解析类
 */
public class ClassParser {

    /**
     * 获取CompilationUnit中的第一个类型声明
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
     * 查找方法声明
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
            return convertConstructor2Method(constructorDeclaration);
        }

        return null;
    }

    /**
     * 将构造器声明转换为方法声明
     * 
     * @param constructorDeclaration 构造器声明
     * @return 方法声明
     */
    private MethodDeclaration convertConstructor2Method(ConstructorDeclaration constructorDeclaration) {
        MethodDeclaration constructorTransMethod = new MethodDeclaration(
                constructorDeclaration.getModifiers(),
                constructorDeclaration.getAnnotations(),
                constructorDeclaration.getTypeParameters(),
                new UnknownType(),
                constructorDeclaration.getName(),
                constructorDeclaration.getParameters(),
                constructorDeclaration.getThrownExceptions(),
                constructorDeclaration.getBody(),
                constructorDeclaration.getReceiverParameter().orElse(null));
        // 由于 constructor => method 没有把方法注释带过去
        constructorTransMethod.setJavadocComment(constructorDeclaration.getJavadocComment().orElse(null))
                .setComment(constructorDeclaration.getComment().orElse(null));
        return constructorTransMethod;
    }

    /**
     * 查找一般方法声明
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
                    return matchesParameters(method, paramTypes);
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * 查找构造器方法声明
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
                    return matchesParameters(method, paramTypes);
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * 检查方法声明或构造器声明的参数类型和提供的参数类型是否逐一匹配
     * 
     * @param <T>         方法声明或构造器声明的类型
     * @param declaration 方法声明或构造器声明
     * @param paramTypes  参数类型列表，比如：["String", "Object", "int"]
     * @return 是否匹配
     */
    private <T extends CallableDeclaration<?>> boolean matchesParameters(T declaration, List<String> paramTypes) {
        if (declaration.getParameters().isEmpty() && CollectionUtils.isEmpty(paramTypes)) {
            // 无参
            return true;
        }
        if (declaration.getParameters().size() != paramTypes.size()) {
            // 参数数量不匹配
            return false;
        }
        // 逐一比较参数类型（使用简单名称）
        for (int i = 0; i < paramTypes.size(); i++) {
            String actual = declaration.getParameter(i).getType().asString();
            String expected = paramTypes.get(i);
            // 进行更严格的匹配：
            if (!typeMatches(actual, expected)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断实际方法参数类型字符串与期望类型字符串是否匹配。
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
        String actualBase = actual.split(PathConstant.LEFT_ANGLE_BRACKET)[0];
        String expectedBase = expected.split(PathConstant.LEFT_ANGLE_BRACKET)[0];

        // 如果都带有包名，则直接比较
        if (actualBase.contains(PathConstant.POINT) && expectedBase.contains(PathConstant.POINT)) {
            return actualBase.equals(expectedBase);
        }

        // 保留类名（去掉包路径）
        if (actualBase.contains(PathConstant.POINT)) {
            actualBase = actualBase.substring(actualBase.lastIndexOf('.') + 1);
        }
        if (expectedBase.contains(PathConstant.POINT)) {
            expectedBase = expectedBase.substring(expectedBase.lastIndexOf('.') + 1);
        }
        return actualBase.equals(expectedBase);
    }
}