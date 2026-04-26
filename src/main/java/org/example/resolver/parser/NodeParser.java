package org.example.resolver.parser;

import java.util.Optional;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;

/**
 * javaparser 解析 Node
 */
public class NodeParser {
    /**
     * 从方法声明中解析其所属的包名
     * method 这里解析，如果有第三方依赖，有可能出现异常
     * 
     * @param method 方法声明
     * @return 包名
     */
    public String parseOutPackStrFromCallDecl(Node method) {

        /*
         * return method.findCompilationUnit()
         * .flatMap(CompilationUnit::getPackageDeclaration)
         * .map(pd -> pd.getNameAsString()).orElse("");
         */

        Optional<CompilationUnit> compilationUnit = method.findCompilationUnit();
        if (compilationUnit.isPresent()) {
            Optional<com.github.javaparser.ast.PackageDeclaration> packageDeclaration = compilationUnit.get().getPackageDeclaration();
            if (packageDeclaration.isPresent()) {
                return packageDeclaration.get().getNameAsString();
            }
        }
        return "";

    }
}
