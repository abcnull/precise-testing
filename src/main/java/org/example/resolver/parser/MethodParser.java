package org.example.resolver.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
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

import org.apache.commons.lang3.StringUtils;
import org.example.constant.PathConstant;
import org.example.resolver.model.MethodCallInfo;

import org.example.util.StringUtil;

/**
 * 方法解析器
 * 专门去解析方法
 */
public class MethodParser {

    /**
     * 从导入语句中查找类的完整包名
     */
    public String findFullClassNameFromImports(String simpleClassName, MethodDeclaration method) {
        Optional<CompilationUnit> cuOpt = method.findCompilationUnit();
        if (cuOpt.isPresent()) {
            CompilationUnit cu = cuOpt.get();
            // 遍历所有导入语句
            for (ImportDeclaration importDecl : cu.getImports()) {
                String importName = importDecl.getNameAsString();
                if (importName.endsWith(PathConstant.POINT + simpleClassName)) {
                    return importName;
                }
            }
        }
        return null;
    }

    /**
     * 提取方法的完整参数类型。
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
     * 从MethodDeclaration中提取方法修饰符（使用JavaParser）
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
     * 从MethodDeclaration中提取方法注解（使用JavaParser）
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
     * 从MethodDeclaration中提取方法注释（使用JavaParser）
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
     * 
     * @param method 方法声明
     * @return 方法内部的调用信息列表
     */
    public List<MethodCallInfo> parseOutMethodCalls(MethodDeclaration method) {
        if (method == null) {
            // 方法声明为空，说明是 jdk/第三方依赖，直接返回结束，表示里层再无调用关系
            return new ArrayList<>();
        }
        List<MethodCallInfo> calls = new ArrayList<>();
        final MethodDeclaration finalMethod = method;

        // 遍历方法体内的所有表达式，查找方法调用表达式
        method.findAll(Expression.class).forEach(expr -> {
            if (expr.isMethodCallExpr()) {
                // 方法调用表达式
                MethodCallExpr callExpr = expr.asMethodCallExpr();
                try {
                    // 解析方法调用表达式，获取方法声明
                    ResolvedMethodDeclaration resolvedMethod = callExpr.resolve();
                    // A a = new B() 中的 A 的包名
                    String packageName = resolvedMethod.getPackageName();
                    // A a = new B() 中的 A 的类名
                    String fullDeclaringClass = resolvedMethod.getClassName();
                    if (StringUtils.isNotEmpty(packageName)) {
                        // com.xxx.A
                        fullDeclaringClass = packageName + PathConstant.POINT + fullDeclaringClass;
                    }
                    // 被调用方法名
                    String methodName = resolvedMethod.getName();

                    // com.yyy.B
                    String[] realFullDeclaringClass = { fullDeclaringClass };
                    // 处理多态：尝试通过分析作用域表达式，获取真实的类名（考虑多态情况）
                    try {
                        if (callExpr.getScope().isPresent()) { // 检查方法调用是否有作用域表达式（如 `list.add()`)
                            Expression scopeExpr = callExpr.getScope().get(); // 如果 list.add()，则 scopeExpr 为 list
                            if (scopeExpr.isNameExpr()) { // 如果作用域是一个名称表达式（`NameExpr`），则获取变量名 list
                                NameExpr nameExpr = scopeExpr.asNameExpr();
                                String variableName = nameExpr.getNameAsString(); // list

                                // 在当前方法中查找所有变量声明表达式
                                Optional<VariableDeclarationExpr> varDeclOpt = finalMethod
                                        .findAll(VariableDeclarationExpr.class)
                                        .stream()
                                        .filter(varDecl -> varDecl.getVariables().stream()
                                                .anyMatch(var -> var.getNameAsString().equals(variableName)))
                                        .findFirst();

                                // 分析变量初始化表达式
                                if (varDeclOpt.isPresent()) {
                                    VariableDeclarationExpr varDecl = varDeclOpt.get();
                                    varDecl.getVariables().stream()
                                            .filter(var -> var.getNameAsString().equals(variableName))
                                            .findFirst()
                                            .ifPresent(var -> {
                                                if (var.getInitializer().isPresent()) {
                                                    // 变量初始化的表达式
                                                    Expression initializer = var.getInitializer().get();
                                                    if (initializer.isObjectCreationExpr()) {
                                                        ObjectCreationExpr creationExpr = initializer
                                                                .asObjectCreationExpr(); // 类型转换: 将初始化表达式转换为对象创建表达式
                                                        String actualType = creationExpr.getType().toString(); // 获取创建对象的类型，例如 ArrayList<String>
                                                        // 剔除泛型
                                                        if (actualType.contains("<")) {
                                                            actualType = actualType.substring(0,
                                                                    actualType.indexOf("<"));
                                                        }
                                                        String fullActualType;
                                                        if (actualType.contains(".")) {
                                                            fullActualType = actualType;
                                                        } else {
                                                            // 如果类型名中没有包名，尝试从导入语句中查找完整的类名
                                                            String fullClassName = findFullClassNameFromImports(
                                                                    actualType,
                                                                    finalMethod);
                                                            if (fullClassName != null) {
                                                                fullActualType = fullClassName;
                                                            } else {
                                                                // 如果导入语句中也没有找到完整的类名，尝试从当前类所属包名替代
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

                    // 获取参数类型：遍历方法参数，获取每个参数的类型
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
                                                        String fullClassName = this.findFullClassNameFromImports(
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
                                                            String fullClassName = findFullClassNameFromImports(
                                                                    actualType,
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
                // 构造方法调用表达式
                ObjectCreationExpr creationExpr = expr.asObjectCreationExpr();
                try {
                    // 解析构造方法调用表达式
                    ResolvedConstructorDeclaration resolvedConstructor = creationExpr.resolve();
                    String declaringClass = resolvedConstructor.getClassName();
                    String methodName = declaringClass; // 构造方法的类名
                    String packageName = resolvedConstructor.getPackageName(); // 获取构造方法所属的包名
                    String fullDeclaringClass = resolvedConstructor.getClassName();
                    if (StringUtils.isNotEmpty(packageName)) {
                        fullDeclaringClass = packageName + PathConstant.POINT + fullDeclaringClass; // 获取构造方法所属的类名
                    }

                    // 获取构造方法的参数类型
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
                        String fullClassNameFromImports = this.findFullClassNameFromImports(className,
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
                                    String fullClassName = this.findFullClassNameFromImports(variableType,
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
                    return packageName.isEmpty() ? className : packageName + PathConstant.POINT + className;
                }
            } else {
                String scopeName = scopeArray[0];
                String fullClassName = this.findFullClassNameFromImports(scopeName, containingMethod);
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