package org.example.resolver.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.example.resolver.model.MethodCallInfo;
import org.example.resolver.util.ParserUtil;
import org.example.resolver.util.StringUtil;

/**
 * 方法解析器
 * 专门去解析方法
 */
public class MethodParser {

    /**
     * 提取方法的完整参数类型。DONE
     */
    public List<String> parseOutFullParamTypes(MethodDeclaration method) {
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
     * 从MethodDeclaration中提取方法修饰符（使用JavaParser）DONE
     *
     * @param method 方法声明
     * @return 方法修饰符列表
     */
    public List<Keyword> parseOutMethodModifiers(MethodDeclaration method) {
        return method.getModifiers().stream()
                .map(modifier -> modifier.getKeyword())
                .collect(Collectors.toList());
    }

    /**
     * 从MethodDeclaration中提取方法注解（使用JavaParser）DONE
     *
     * @param method 方法声明
     * @return 方法注解信息的映射表，键为注解名称，值为参数映射表
     */
    public Map<String, Map<String, Object>> parseOutMethodAnnotations(MethodDeclaration method) {
        Map<String, Map<String, Object>> annotations = new HashMap<>();
        NodeList<AnnotationExpr> annotationExprs = method.getAnnotations();

        for (AnnotationExpr annotation : annotationExprs) {
            String annotationName = annotation.getNameAsString();
            Map<String, Object> params = new HashMap<>();

            if (annotation.isNormalAnnotationExpr()) {
                NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
                for (MemberValuePair pair : normal.getPairs()) {
                    String paramName = pair.getNameAsString();
                    Object paramValue = extractAnnotationValue(pair.getValue());
                    params.put(paramName, paramValue);
                }
            } else if (annotation.isSingleMemberAnnotationExpr()) {
                SingleMemberAnnotationExpr single = annotation.asSingleMemberAnnotationExpr();
                Object paramValue = extractAnnotationValue(single.getMemberValue());
                params.put("value", paramValue);
            }

            annotations.put(annotationName, params);
        }

        return annotations;
    }

    /**
     * 提取注解参数值 DONE
     */
    private Object extractAnnotationValue(Expression valueExpr) {
        if (valueExpr.isStringLiteralExpr()) {
            return valueExpr.asStringLiteralExpr().getValue();
        } else if (valueExpr.isIntegerLiteralExpr()) {
            return valueExpr.asIntegerLiteralExpr().asNumber();
        } else if (valueExpr.isBooleanLiteralExpr()) {
            return valueExpr.asBooleanLiteralExpr().getValue();
        } else if (valueExpr.isFieldAccessExpr()) {
            return valueExpr.asFieldAccessExpr().getNameAsString();
        } else if (valueExpr.isArrayInitializerExpr()) {
            List<Object> arrayValues = new ArrayList<>();
            for (Expression element : valueExpr.asArrayInitializerExpr().getValues()) {
                arrayValues.add(extractAnnotationValue(element));
            }
            return arrayValues;
        } else if (valueExpr.isClassExpr()) {
            return valueExpr.asClassExpr().getType().asString() + ".class";
        } else {
            return valueExpr.toString();
        }
    }

    /**
     * 从MethodDeclaration中提取方法注释（使用JavaParser）DONE
     *
     * @param method 方法声明
     * @return 方法注释内容
     */
    public String parseOutMethodComment(MethodDeclaration method) {
        Optional<String> commentOpt = method.getJavadocComment().map(javadoc -> javadoc.getContent());
        if (commentOpt.isPresent()) {
            return commentOpt.get();
        }
        
        return method.getComment().map(comment -> comment.getContent()).orElse("");
    }

    /**
     * 提取方法体内的所有方法调用和构造方法调用（按代码出现顺序）
     */
    public List<MethodCallInfo> parseOutMethodCalls(MethodDeclaration method) {
        if (method == null) {
            // 方法声明为空，说明是 jdk/第三方依赖，直接返回结束
            return new ArrayList<>();
        }
        List<MethodCallInfo> calls = new ArrayList<>();
        final MethodDeclaration finalMethod = method;

        method.findAll(Expression.class).forEach(expr -> {
            if (expr.isMethodCallExpr()) {
                MethodCallExpr callExpr = expr.asMethodCallExpr();
                try {
                    ResolvedMethodDeclaration resolvedMethod = callExpr.resolve();
                    String methodName = resolvedMethod.getName();

                    String fullDeclaringClass = resolvedMethod.getClassName();
                    String packageName = resolvedMethod.getPackageName();
                    if (!packageName.isEmpty()) {
                        fullDeclaringClass = packageName + "." + fullDeclaringClass;
                    }
                    String[] realFullDeclaringClass = { fullDeclaringClass };

                    try {
                        if (callExpr.getScope().isPresent()) {
                            Expression scopeExpr = callExpr.getScope().get();
                            if (scopeExpr.isNameExpr()) {
                                NameExpr nameExpr = scopeExpr.asNameExpr();
                                String variableName = nameExpr.getNameAsString();

                                Optional<VariableDeclarationExpr> varDeclOpt = finalMethod
                                        .findAll(VariableDeclarationExpr.class)
                                        .stream()
                                        .filter(varDecl -> varDecl.getVariables().stream()
                                                .anyMatch(var -> var.getNameAsString().equals(variableName)))
                                        .findFirst();

                                if (varDeclOpt.isPresent()) {
                                    VariableDeclarationExpr varDecl = varDeclOpt.get();
                                    varDecl.getVariables().stream()
                                            .filter(var -> var.getNameAsString().equals(variableName))
                                            .findFirst()
                                            .ifPresent(var -> {
                                                if (var.getInitializer().isPresent()) {
                                                    Expression initializer = var.getInitializer().get();
                                                    if (initializer.isObjectCreationExpr()) {
                                                        ObjectCreationExpr creationExpr = initializer
                                                                .asObjectCreationExpr();
                                                        String actualType = creationExpr.getType().toString();
                                                        if (actualType.contains("<")) {
                                                            actualType = actualType.substring(0,
                                                                    actualType.indexOf("<"));
                                                        }
                                                        String fullActualType;
                                                        if (actualType.contains(".")) {
                                                            fullActualType = actualType;
                                                        } else {
                                                            String fullClassName = ParserUtil
                                                                    .findFullClassNameFromImports(actualType,
                                                                            finalMethod);
                                                            if (fullClassName != null) {
                                                                fullActualType = fullClassName;
                                                            } else {
                                                                String initPackageName = finalMethod
                                                                        .findCompilationUnit()
                                                                        .flatMap(CompilationUnit::getPackageDeclaration)
                                                                        .map(pd -> pd.getNameAsString())
                                                                        .orElse("");
                                                                fullActualType = initPackageName.isEmpty() ? actualType
                                                                        : initPackageName + "." + actualType;
                                                            }
                                                        }
                                                        realFullDeclaringClass[0] = fullActualType;
                                                    }
                                                }
                                            });
                                }
                            }
                        }
                    } catch (Exception e) {
                    }

                    List<String> paramTypes = new ArrayList<>();
                    for (int i = 0; i < resolvedMethod.getNumberOfParams(); i++) {
                        String paramType = resolvedMethod.getParam(i).getType().describe();
                        paramTypes.add(paramType);
                    }

                    calls.add(
                            new MethodCallInfo(fullDeclaringClass, realFullDeclaringClass[0], methodName, paramTypes));
                } catch (Exception e) {
                    String methodName = callExpr.getNameAsString();
                    String[] inferredClass = { inferClassNameFromCall(callExpr, finalMethod) };
                    String[] realInferredClass = { inferredClass[0] };

                    try {
                        if (callExpr.getScope().isPresent()) {
                            Expression scopeExpr = callExpr.getScope().get();
                            if (scopeExpr.isNameExpr()) {
                                NameExpr nameExpr = scopeExpr.asNameExpr();
                                String variableName = nameExpr.getNameAsString();

                                Optional<VariableDeclarationExpr> varDeclOpt = finalMethod
                                        .findAll(VariableDeclarationExpr.class)
                                        .stream()
                                        .filter(varDecl -> varDecl.getVariables().stream()
                                                .anyMatch(var -> var.getNameAsString().equals(variableName)))
                                        .findFirst();

                                if (varDeclOpt.isPresent()) {
                                    VariableDeclarationExpr varDecl = varDeclOpt.get();
                                    varDecl.getVariables().stream()
                                            .filter(var -> var.getNameAsString().equals(variableName))
                                            .findFirst()
                                            .ifPresent(var -> {
                                                String variableType = var.getType().toString();
                                                String packageName = finalMethod.findCompilationUnit()
                                                        .flatMap(CompilationUnit::getPackageDeclaration)
                                                        .map(pd -> pd.getNameAsString())
                                                        .orElse("");

                                                try {
                                                    String fullVariableType = var.getType().resolve().describe();
                                                    inferredClass[0] = fullVariableType;
                                                } catch (Exception ex) {
                                                    if (!variableType.contains(".")) {
                                                        String fullClassName = ParserUtil.findFullClassNameFromImports(
                                                                variableType, finalMethod);
                                                        if (fullClassName != null) {
                                                            inferredClass[0] = fullClassName;
                                                        } else {
                                                            inferredClass[0] = packageName.isEmpty() ? variableType
                                                                    : packageName + "." + variableType;
                                                        }
                                                    } else {
                                                        inferredClass[0] = variableType;
                                                    }
                                                }

                                                if (var.getInitializer().isPresent()) {
                                                    Expression initializer = var.getInitializer().get();
                                                    if (initializer.isObjectCreationExpr()) {
                                                        ObjectCreationExpr creationExpr = initializer
                                                                .asObjectCreationExpr();
                                                        String actualType = creationExpr.getType().toString();
                                                        if (actualType.contains("<")) {
                                                            actualType = actualType.substring(0,
                                                                    actualType.indexOf("<"));
                                                        }
                                                        String fullActualType;
                                                        if (actualType.contains(".")) {
                                                            fullActualType = actualType;
                                                        } else {
                                                            String fullClassName = ParserUtil
                                                                    .findFullClassNameFromImports(actualType,
                                                                            finalMethod);
                                                            if (fullClassName != null) {
                                                                fullActualType = fullClassName;
                                                            } else {
                                                                String initPackageName = finalMethod
                                                                        .findCompilationUnit()
                                                                        .flatMap(CompilationUnit::getPackageDeclaration)
                                                                        .map(pd -> pd.getNameAsString())
                                                                        .orElse("");
                                                                fullActualType = initPackageName.isEmpty() ? actualType
                                                                        : initPackageName + "." + actualType;
                                                            }
                                                        }
                                                        realInferredClass[0] = fullActualType;
                                                    }
                                                }
                                            });
                                }
                            }
                        }
                    } catch (Exception ex) {
                    }

                    List<String> paramTypes = new ArrayList<>();
                    callExpr.getArguments().forEach(arg -> {
                        try {
                            paramTypes.add(arg.calculateResolvedType().describe());
                        } catch (Exception ex) {
                            paramTypes.add(arg.toString());
                        }
                    });

                    calls.add(new MethodCallInfo(inferredClass[0], realInferredClass[0], methodName, paramTypes));
                }
            } else if (expr.isObjectCreationExpr()) {
                ObjectCreationExpr creationExpr = expr.asObjectCreationExpr();
                try {
                    ResolvedConstructorDeclaration resolvedConstructor = creationExpr.resolve();
                    String declaringClass = resolvedConstructor.getClassName();
                    String methodName = declaringClass;

                    String fullDeclaringClass = resolvedConstructor.getClassName();
                    String packageName = resolvedConstructor.getPackageName();
                    if (!packageName.isEmpty()) {
                        fullDeclaringClass = packageName + "." + fullDeclaringClass;
                    }

                    List<String> paramTypes = new ArrayList<>();
                    for (int i = 0; i < resolvedConstructor.getNumberOfParams(); i++) {
                        String paramType = resolvedConstructor.getParam(i).getType().describe();
                        paramTypes.add(paramType);
                    }

                    calls.add(new MethodCallInfo(fullDeclaringClass, fullDeclaringClass, methodName, paramTypes));
                } catch (Exception e) {
                    String className = creationExpr.getType().toString();
                    String methodName = StringUtil.getSimpleClassName(className);
                    String fullClassName;
                    if (className.contains(".")) {
                        fullClassName = className;
                    } else {
                        String fullClassNameFromImports = ParserUtil.findFullClassNameFromImports(className,
                                finalMethod);
                        if (fullClassNameFromImports != null) {
                            fullClassName = fullClassNameFromImports;
                        } else {
                            String initPackageName = finalMethod.findCompilationUnit()
                                    .flatMap(CompilationUnit::getPackageDeclaration)
                                    .map(pd -> pd.getNameAsString())
                                    .orElse("");
                            fullClassName = initPackageName.isEmpty() ? className : initPackageName + "." + className;
                        }
                    }

                    List<String> paramTypes = new ArrayList<>();
                    creationExpr.getArguments().forEach(arg -> {
                        try {
                            paramTypes.add(arg.calculateResolvedType().describe());
                        } catch (Exception ex) {
                            paramTypes.add(arg.toString());
                        }
                    });

                    calls.add(new MethodCallInfo(fullClassName, fullClassName, methodName, paramTypes));
                }
            }
        });

        return calls;
    }

    /**
     * 从方法调用推断类名（简化版本）
     */
    private String inferClassNameFromCall(MethodCallExpr callExpr, MethodDeclaration containingMethod) {
        String packageName = containingMethod.findCompilationUnit()
                .flatMap(CompilationUnit::getPackageDeclaration)
                .map(pd -> pd.getNameAsString())
                .orElse("");

        if (callExpr.getScope().isPresent()) {
            Expression scopeExpr = callExpr.getScope().get();
            while (scopeExpr.isMethodCallExpr()) {
                scopeExpr = scopeExpr.asMethodCallExpr().getScope().orElse(scopeExpr);
            }

            final String[] scopeArray = { scopeExpr.toString() };
            if (Character.isLowerCase(scopeArray[0].charAt(0))) {
                Optional<VariableDeclarationExpr> varDeclOpt = containingMethod.findAll(VariableDeclarationExpr.class)
                        .stream()
                        .filter(varDecl -> varDecl.getVariables().stream()
                                .anyMatch(var -> var.getNameAsString().equals(scopeArray[0])))
                        .findFirst();

                if (varDeclOpt.isPresent()) {
                    VariableDeclarationExpr varDecl = varDeclOpt.get();
                    varDecl.getVariables().stream()
                            .filter(var -> var.getNameAsString().equals(scopeArray[0]))
                            .findFirst()
                            .ifPresent(var -> {
                                String variableType = var.getType().toString();
                                int genericStart = variableType.indexOf('<');
                                if (genericStart > 0) {
                                    variableType = variableType.substring(0, genericStart);
                                }
                                if (!variableType.contains(".")) {
                                    String fullClassName = ParserUtil.findFullClassNameFromImports(variableType,
                                            containingMethod);
                                    if (fullClassName != null) {
                                        scopeArray[0] = fullClassName;
                                    } else {
                                        scopeArray[0] = packageName.isEmpty() ? variableType
                                                : packageName + "." + variableType;
                                    }
                                } else {
                                    scopeArray[0] = variableType;
                                }
                            });
                } else {
                    String className = Character.toUpperCase(scopeArray[0].charAt(0)) + scopeArray[0].substring(1);
                    return packageName.isEmpty() ? className : packageName + "." + className;
                }
            } else {
                String scopeName = scopeArray[0];
                String fullClassName = ParserUtil.findFullClassNameFromImports(scopeName, containingMethod);
                if (fullClassName != null) {
                    return fullClassName;
                }
            }
            return scopeArray[0];
        }

        return containingMethod.findCompilationUnit()
                .flatMap(cu -> cu.getTypes().stream().findFirst())
                .map(type -> {
                    String typeName = type.getNameAsString();
                    return packageName.isEmpty() ? typeName : packageName + "." + typeName;
                })
                .orElse("");
    }
}