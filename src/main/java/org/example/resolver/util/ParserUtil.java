package org.example.resolver.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.collections.CollectionUtils;

public class ParserUtil {

    /**
     * 从指定的源码根目录解析类文件，返回对应的CompilationUnit。
     * 支持在源码根目录直接查找或在src/main/java子目录查找。
     * 
     * @param javaParser     JavaParser实例
     * @param sourceRootPath 源码根目录路径
     * @param className      全限定类名
     * @return 解析后的CompilationUnit，若文件不存在或解析失败则返回null
     */
    public static CompilationUnit parseClassFile(JavaParser javaParser, String sourceRootPath, String className) {
        // 将类名转换为文件路径
        String relativePath = className.replace(".", "/") + ".java";

        // 尝试直接在 sourceRootPath 中查找
        Path filePath = Paths.get(sourceRootPath, relativePath);
        File file = filePath.toFile();

        // 如果文件不存在，检查 sourceRootPath 是否已经包含 "src/main/java"
        if (!file.exists() && !sourceRootPath.endsWith("src/main/java")) {
            // 尝试在项目源码目录中查找
            Path altPath = Paths.get(sourceRootPath, "src/main/java", relativePath);
            file = altPath.toFile();
        }

        if (!file.exists()) {
            return null;
        }

        try {
            return javaParser.parse(file).getResult().orElse(null);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    /**
     * 判断实际类型字符串与期望类型字符串是否匹配。
     * 支持以下情况：
     * 1. 带或不带包名的普通类（如 "java.util.List" 与 "List"）
     * 2. 泛型写法（如 "Map<String, Integer>" 与 "Map"、"Map<String,Integer>"）
     * 3. 完全相同的泛型写法（如 "Map<String,Integer>" 与 "Map<String,Integer>"）
     * 4. 数组类型（如 "int[]" 与 "int[]"）
     */
    private static boolean typeMatches(String actual, String expected) {
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

    /**
     * 从导入语句中查找类的完整包名
     */
    public static String findFullClassNameFromImports(String simpleClassName, MethodDeclaration method) {
        Optional<CompilationUnit> cuOpt = method.findCompilationUnit();
        if (cuOpt.isPresent()) {
            CompilationUnit cu = cuOpt.get();
            // 遍历所有导入语句
            for (ImportDeclaration importDecl : cu.getImports()) {
                String importName = importDecl.getNameAsString();
                if (importName.endsWith("." + simpleClassName)) {
                    return importName;
                }
            }
        }
        return null;
    }

    /**
     * 提取方法的完整参数类型
     */
    public static List<String> extractFullParamTypes(MethodDeclaration method) {
        List<String> fullParamTypes = new ArrayList<>();
        method.getParameters().forEach(param -> {
            try {
                fullParamTypes.add(param.getType().resolve().describe());
            } catch (Exception e) {
                fullParamTypes.add(param.getType().asString());
            }
        });
        return fullParamTypes;
    }

    /**
     * 查找方法声明 
     * 
     * @param cu         编译单元
     * @param methodName 方法名
     * @param paramTypes 参数类型列表，比如：["String", "Object", "int"]
     * @return 方法声明
     */
    public static MethodDeclaration findMethodDeclaration(CompilationUnit cu, String methodName,
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
}