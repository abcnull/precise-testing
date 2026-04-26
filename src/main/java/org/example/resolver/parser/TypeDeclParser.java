package org.example.resolver.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.example.constant.WordConstant;
import org.example.node.field.ClassDeclaration;

import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

/**
 * 类型解析器，用于解析Java类型声明节点（如类、接口、枚举等）
 */
public class TypeDeclParser {
    private ExpressionParser expressionParser;

    private ExpressionParser getExpressionParser() {
        if (expressionParser == null) {
            expressionParser = new ExpressionParser();
        }
        return expressionParser;
    }

    /**
     * 从 TypeDeclaration 中提取类修饰符（使用JavaParser）
     * 
     * @param typeDecl 类声明节点
     * @return 类修饰符列表
     */
    public List<Keyword> parseOutClassModifiers(TypeDeclaration<?> typeDecl) {
        if (typeDecl == null) {
            return new ArrayList<>();
        }
        return typeDecl.getModifiers().stream().map(mod -> mod.getKeyword()).collect(Collectors.toList());
    }

    /**
     * 从 TypeDeclaration 中提取类注解（使用JavaParser）
     * 
     * @param typeDecl 类声明节点
     * @return 类注解参数映射，key: 注解名，value: 多个注解参数的 kv，k 是键名，v 是具体的值
     */
    public Map<String, Map<String, Object>> parseOutClassAnnotations(TypeDeclaration<?> typeDecl) {
        if (typeDecl == null) {
            return new HashMap<>();
        }

        Map<String, Map<String, Object>> annotations = new HashMap<>();
        NodeList<AnnotationExpr> annotationExprs = typeDecl.getAnnotations();

        // 遍历类的注解
        for (AnnotationExpr annotation : annotationExprs) {
            String annotationName = annotation.getNameAsString(); // 注解名
            Map<String, Object> params = new HashMap<>();

            if (annotation.isNormalAnnotationExpr()) {
                // 多参数注解，如 @RequestMapping(value = "/api", method = GET)
                NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
                for (MemberValuePair pair : normal.getPairs()) {
                    String paramName = pair.getNameAsString(); // k 名
                    Object paramValue = getExpressionParser().extractAnnotationValue(pair.getValue()); // v 类型
                    params.put(paramName, paramValue);
                }
            } else if (annotation.isSingleMemberAnnotationExpr()) {
                // 单参数注解，如 @SuppressWarnings("unchecked")
                SingleMemberAnnotationExpr single = annotation.asSingleMemberAnnotationExpr();
                Object paramValue = getExpressionParser().extractAnnotationValue(single.getMemberValue()); // v 类型
                params.put(WordConstant.VALUE_KEY, paramValue);
            }
            // 标记注解（无参数）如 @Override，params保持为空

            annotations.put(annotationName, params);
        }

        return annotations;
    }

    /**
     * 从TypeDeclaration中提取类注释（使用JavaParser）
     * 
     * @param typeDecl 类声明节点
     * @return 类注释内容
     */
    public String parseOutClassComment(TypeDeclaration<?> typeDecl) {
        if (typeDecl == null) {
            return "";
        }
        Optional<String> classCommentOpt = typeDecl.getJavadocComment().map(javadoc -> javadoc.getContent());
        if (classCommentOpt.isPresent()) {
            return classCommentOpt.get(); // 优先使用Javadoc注释
        } else {
            return typeDecl.getComment().map(comment -> comment.getContent()).orElse(""); // 其次使用普通注释
        }
    }

    /**
     * 从TypeDeclaration中提取类声明类型
     *
     * @param typeDecl 类声明
     * @return 类声明类型
     */
    public ClassDeclaration parseOutClassDeclaration(TypeDeclaration<?> typeDecl) {
        if (typeDecl == null) {
            return null;
        }
        if (typeDecl instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) typeDecl;
            if (classDecl.isInterface()) {
                return ClassDeclaration.INTERFACE;
            } else {
                return ClassDeclaration.CLASS;
            }
        } else if (typeDecl instanceof EnumDeclaration) {
            return ClassDeclaration.ENUM;
        } else if (typeDecl instanceof AnnotationDeclaration) {
            return ClassDeclaration.ANNOTATION;
        } else if (typeDecl instanceof RecordDeclaration) {
            return ClassDeclaration.RECORD;
        } else {
            return null;
        }
    }

}
