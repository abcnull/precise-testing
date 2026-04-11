package org.example.resolver.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.example.node.field.ClassDeclaration;

import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import java.util.Optional;

public class TypeParser {
    /**
     * 从TypeDeclaration中提取类修饰符（使用JavaParser）DONE
     * 
     * @param typeDecl 类声明节点
     * @return 类修饰符列表
     */
    public List<Keyword> parseOutClassModifiers(TypeDeclaration<?> typeDecl) {
        return typeDecl.getModifiers().stream()
                .map(mod -> mod.getKeyword())
                .collect(Collectors.toList());
    }

    /**
     * 从TypeDeclaration中提取类注解（使用JavaParser） DONE
     * 
     * @param typeDecl 类声明节点
     * @return 类注解参数映射，key: 注解名，value: 多个注解参数的 kv
     */
    public Map<String, Map<String, Object>> parseOutClassAnnotations(TypeDeclaration<?> typeDecl) {
        Map<String, Map<String, Object>> annotations = new HashMap<>();
        NodeList<AnnotationExpr> annotationExprs = typeDecl.getAnnotations();

        // 遍历类的注解
        for (AnnotationExpr annotation : annotationExprs) {
            String annotationName = annotation.getNameAsString();
            Map<String, Object> params = new HashMap<>();

            if (annotation.isNormalAnnotationExpr()) {
                // 多参数注解，如 @RequestMapping(value = "/api", method = GET)
                NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
                for (MemberValuePair pair : normal.getPairs()) {
                    String paramName = pair.getNameAsString();
                    Object paramValue = extractAnnotationValue(pair.getValue());
                    params.put(paramName, paramValue);
                }
            } else if (annotation.isSingleMemberAnnotationExpr()) {
                // 单参数注解，如 @SuppressWarnings("unchecked")
                SingleMemberAnnotationExpr single = annotation.asSingleMemberAnnotationExpr();
                Object paramValue = extractAnnotationValue(single.getMemberValue());
                params.put("value", paramValue);
            }
            // 标记注解（无参数）如 @Override，params保持为空

            annotations.put(annotationName, params);
        }

        return annotations;
    }

    /**
     * 提取注解参数值（使用JavaParser） 
     * 
     * @param valueExpr 注解参数表达式
     * @return 注解参数值，支持字符串、整数、布尔值、枚举、数组、类类型等
     */
    private Object extractAnnotationValue(Expression valueExpr) {
        if (valueExpr.isStringLiteralExpr()) {
            return valueExpr.asStringLiteralExpr().getValue();
        } else if (valueExpr.isIntegerLiteralExpr()) {
            return valueExpr.asIntegerLiteralExpr().asNumber();
        } else if (valueExpr.isBooleanLiteralExpr()) {
            return valueExpr.asBooleanLiteralExpr().getValue();
        } else if (valueExpr.isFieldAccessExpr()) {
            // 枚举类型，如 RequestMethod.GET
            return valueExpr.asFieldAccessExpr().getNameAsString();
        } else if (valueExpr.isArrayInitializerExpr()) {
            // 数组类型，如 {"value1", "value2"}
            List<Object> arrayValues = new ArrayList<>();
            for (Expression element : valueExpr.asArrayInitializerExpr().getValues()) {
                arrayValues.add(extractAnnotationValue(element));
            }
            return arrayValues;
        } else if (valueExpr.isClassExpr()) {
            // 类类型，如 String.class
            return valueExpr.asClassExpr().getType().asString() + ".class";
        } else {
            // 其他类型，转为字符串
            return valueExpr.toString();
        }
    }

    /**
     * 从TypeDeclaration中提取类注释（使用JavaParser） DONE
     * 
     * @param typeDecl 类声明节点
     * @return 类注释内容
     */
    public String parseOutClassComment(TypeDeclaration<?> typeDecl) {
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
            return ClassDeclaration.CLASS;
        }
    }

}
