package org.example.resolver.extractor;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnnotationExtractor {

    /**
     * 提取方法注解信息
     */
    public Map<String, Map<String, Object>> extract(MethodDeclaration method) {
        Map<String, Map<String, Object>> annotations = new HashMap<>();
        NodeList<AnnotationExpr> annotationExprs = method.getAnnotations();

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
     * 提取构造器注解信息
     */
    public Map<String, Map<String, Object>> extract(ConstructorDeclaration constructor) {
        Map<String, Map<String, Object>> annotations = new HashMap<>();
        NodeList<AnnotationExpr> annotationExprs = constructor.getAnnotations();

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
     * 提取类注解信息
     */
    public Map<String, Map<String, Object>> extract(TypeDeclaration<?> typeDecl) {
        Map<String, Map<String, Object>> annotations = new HashMap<>();
        NodeList<AnnotationExpr> annotationExprs = typeDecl.getAnnotations();

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
     * 提取注解参数值
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
}