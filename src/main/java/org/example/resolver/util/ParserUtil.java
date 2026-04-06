package org.example.resolver.util;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ParserUtil {
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
     */
    public static MethodDeclaration findMethodDeclaration(CompilationUnit cu, String methodName, List<String> paramTypes) {
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals(methodName))
                .filter(method -> paramTypes == null || method.getParameters().size() == paramTypes.size())
                .findFirst()
                .orElse(null);
    }
}