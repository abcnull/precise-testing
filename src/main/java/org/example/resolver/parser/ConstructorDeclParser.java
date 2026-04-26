package org.example.resolver.parser;

import org.apache.commons.lang3.StringUtils;

import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.UnknownType;

/**
 * 构造函数解析器
 */
public class ConstructorDeclParser {

    /**
     * 判断构造函数名
     * 
     * @param constructorDecl 构造函数声明
     * @param constructorName 构造函数名称
     * @return 是否相等
     */
    public boolean isConstructorEqual(ConstructorDeclaration constructorDecl, String constructorName) {
        if (constructorDecl == null || StringUtils.isBlank(constructorName)) {
            return false;
        }
        return constructorDecl.getNameAsString().equals(constructorName);
    }

    /**
     * 将构造器声明转换为方法声明
     * 
     * @param constructorDeclaration 构造器声明
     * @return 方法声明
     */
    public MethodDeclaration convertConstructor2Method(ConstructorDeclaration constructorDeclaration) {
        if (constructorDeclaration == null) {
            return null;
        }
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
}
