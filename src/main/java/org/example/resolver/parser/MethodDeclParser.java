package org.example.resolver.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.example.constant.PathConstant;
import org.example.constant.WordConstant;
import org.example.resolver.model.MethodCallInfo;
import org.example.util.ClassStrUtil;
import org.example.util.PackStrUtil;
import org.example.util.VarStrUtil;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration;
import com.google.common.base.Objects;

/**
 * 方法解析器
 * 专门去解析方法
 */
public class MethodDeclParser {
    private NodeParser nodeParser;
    private ExpressionParser expressionParser;
    private ResolveParser resolveParser;
    private VariableDeclParser variableParser;
    private ParameterParser parameterParser;
    private FieldDeclParser fieldParser;

    private FileParser getFileParser() {
        return FileParser.getInstance();
    }

    private NodeParser getNodeParser() {
        if (nodeParser == null) {
            nodeParser = new NodeParser();
        }
        return nodeParser;
    }

    private ExpressionParser getExpressionParser() {
        if (expressionParser == null) {
            expressionParser = new ExpressionParser();
        }
        return expressionParser;
    }

    private ResolveParser getResolveParser() {
        if (resolveParser == null) {
            resolveParser = new ResolveParser();
        }
        return resolveParser;
    }

    private VariableDeclParser getVariableParser() {
        if (variableParser == null) {
            variableParser = new VariableDeclParser();
        }
        return variableParser;
    }

    private ParameterParser getParameterParser() {
        if (parameterParser == null) {
            parameterParser = new ParameterParser();
        }
        return parameterParser;
    }

    private FieldDeclParser getFieldParser() {
        if (fieldParser == null) {
            fieldParser = new FieldDeclParser();
        }
        return fieldParser;
    }

    /**
     * 判断 import * 是否存在
     * 
     * @param method          方法声明
     * @param simpleClassName 简单类名
     * @return 是否存在，null 表示无法判定，false 表示不存在，true 表示存在
     */
    public Boolean isImportStarExist(MethodDeclaration method, String simpleClassName) {
        if (StringUtils.isBlank(simpleClassName) || method == null) {
            // 无法判定
            return null;
        }

        // 获取所有的类型声明（类、接口、枚举、注解等）
        Optional<CompilationUnit> cuOpt = method.findCompilationUnit();
        if (!cuOpt.isPresent()) {
            return null;
        }

        for (ImportDeclaration importDecl : cuOpt.get().getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName != null && importName.trim().endsWith(PathConstant.DOT + PathConstant.STAR)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从方法所对应的文件的导入语句中 查找类的完整包名
     * 
     * @param method          方法声明
     * @param simpleClassName 简单类名
     * 
     * @return 完整类名
     */
    public String findFullClassNameFromImports(MethodDeclaration method, String simpleClassName) {
        if (StringUtils.isBlank(simpleClassName) || method == null) {
            return null;
        }
        // 方法所属类的编译单元
        Optional<CompilationUnit> cuOpt = method.findCompilationUnit();
        if (!cuOpt.isPresent()) {
            return null;
        }
        CompilationUnit cu = cuOpt.get();
        // 遍历所有导入语句
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName != null && importName.trim().endsWith(PathConstant.DOT + simpleClassName.trim())) {
                return importName.trim();
            }
        }
        return null;
    }

    /**
     * 从方法调用表达式中解析出作用域表达式的声明类，包含包名，去掉泛型
     * 
     * @param method   方法声明
     * @param expr     表达式
     * @param scopeStr 作用域字符串
     * @return 作用域表达式
     */
    public String findScopeClassNameInMethod(MethodDeclaration method, Expression expr, String scopeStr) {
        if (StringUtils.isBlank(scopeStr) || method == null || expr == null) {
            return null;
        }

        // 查找指定表达式之前的方法声明表达式
        Optional<VariableDeclarationExpr> varDeclExprOpt = parseOutVarDeclExprBeforeMethodCallExprInMethod(method, expr,
                scopeStr);
        if (!varDeclExprOpt.isPresent()) {
            return null;
        }
        // 找到方法中的变量声明表达式
        VariableDeclarationExpr varDeclExpr = varDeclExprOpt.get();
        Optional<VariableDeclarator> varDeclOpt = expressionParser
                .parseOutVarDeclFromVarDeclExpr(varDeclExpr, scopeStr);
        if (!varDeclOpt.isPresent()) {
            return null;
        }
        // 变量声明类型 str
        VariableDeclarator varDecl = varDeclOpt.get();
        String variableType = getVariableParser().parseOutBaseTypeStrFromVarWithNoGeneric(varDecl);
        if (variableType.contains(PathConstant.DOT)) {
            // 带有包名，直接返回
            return variableType;
        }
        // 从 import 中找到
        String fullClassName = this.findFullClassNameFromImports(method, variableType);
        if (StringUtils.isNotBlank(fullClassName)) {
            return fullClassName;
        }
        return variableType;
    }

    /**
     * 从方法所对应的文件的类的 field 中查找字段的所属类的完整包类名
     * 
     * @param method    方法声明
     * @param fieldName 字段名
     * @return 字段声明的类的完整包名
     */
    public String findFieldClassNameInClass(MethodDeclaration method, String fieldName) {
        if (StringUtils.isBlank(fieldName) || method == null) {
            return null;
        }

        // 方法所属类的 java 源文件的编译单元
        Optional<CompilationUnit> cuOpt = method.findCompilationUnit();
        if (!cuOpt.isPresent()) {
            return null;
        }
        CompilationUnit cu = cuOpt.get();

        // 查找 method 所属的类的，如果是内部类，一直往上找到最外层类
        Optional<Node> parent = method.getParentNode();
        while (parent.isPresent()) {
            if (!(parent.get() instanceof ClassOrInterfaceDeclaration) && !(parent.get() instanceof EnumDeclaration)) {
                break;
            }
            List<FieldDeclaration> fields = null;
            if (parent.get() instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) parent.get();
                fields = classDecl.getFields();
            }
            if (parent.get() instanceof EnumDeclaration) {
                EnumDeclaration classDecl = (EnumDeclaration) parent.get();
                fields = classDecl.getFields();
            }
            if (CollectionUtils.isEmpty(fields)) {
                parent = parent.get().getParentNode();
                continue;
            }
            // 遍历其中所有字段
            for (FieldDeclaration field : fields) {
                // 字段名不匹配
                if (!field.getVariables().stream().anyMatch(var -> fieldName.equals(var.getNameAsString()))) {
                    continue;
                }
                // 找到这个字段了，需要找到这个字段的类的全限定名返回
                try {
                    // 该 field 的类不是第三方依赖类，可以解析
                    return field.getCommonType().resolve().asReferenceType().getQualifiedName();
                } catch (Exception e) {
                    // field 是第三方依赖类，复杂处理
                    Type fieldType = field.getCommonType();
                    if (fieldType.isPrimitiveType()) {
                        // 基本数据类型
                        return fieldType.asPrimitiveType().getType().asString();
                    } else if (fieldType.isClassOrInterfaceType()) {
                        // 类类型
                        return parseOutFullClassNameFromMethodAndSimpleClassName(method,
                                fieldType.asClassOrInterfaceType().getNameAsString());
                    } else if (fieldType.isArrayType()) {
                        // 数组类型
                        Type arrType = fieldType;
                        int count = 0; // 统计几维数组
                        String suffix = ""; // "[]"
                        while (arrType.isArrayType()) {
                            arrType = arrType.asArrayType().getComponentType();
                            count++;
                        }
                        for (int i = 0; i < count; i++) {
                            suffix += PathConstant.ARR_BRACKETS;
                        }
                        if (arrType.isPrimitiveType()) {
                            return arrType.asPrimitiveType().getType().asString() + suffix;
                        } else if (arrType.isClassOrInterfaceType()) {
                            return parseOutFullClassNameFromMethodAndSimpleClassName(method,
                                    arrType.asClassOrInterfaceType().getNameAsString()) + suffix;
                        } else {
                            return findFullClassNameFromImports(method, arrType.toString()) + suffix;
                        }
                    }
                    // 去泛型
                    return fieldType.toString().substring(0,
                            fieldType.toString().indexOf(PathConstant.LEFT_ANGLE_BRACKET));
                }
            }
            parent = parent.get().getParentNode();
        }

        // 查找 cu 中的所有最外层类（因为一个 java 文件可以有写多个类，但是这些类是平行关系，不是内部类关系）
        for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
            if (!(typeDecl instanceof ClassOrInterfaceDeclaration) && !(typeDecl instanceof EnumDeclaration)) {
                continue;
            }
            List<FieldDeclaration> fields = null;
            if (typeDecl instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration classDecl = typeDecl.asClassOrInterfaceDeclaration();
                fields = classDecl.getFields();
            }
            if (typeDecl instanceof EnumDeclaration) {
                EnumDeclaration enumDecl = typeDecl.asEnumDeclaration();
                fields = enumDecl.getFields();
            }
            if (CollectionUtils.isEmpty(fields)) {
                continue;
            }
            // 遍历其中所有字段
            for (FieldDeclaration field : fields) {
                // 字段名不匹配
                if (!field.getVariables().stream().anyMatch(var -> fieldName.equals(var.getNameAsString()))) {
                    continue;
                }
                // 找到这个字段了，需要找到这个字段的类的全限定名返回
                try {
                    // 该 field 的类不是第三方依赖类，可以解析
                    return field.getCommonType().resolve().asReferenceType().getQualifiedName();
                } catch (Exception e) {
                    // field 是第三方依赖类，复杂处理
                    Type fieldType = field.getCommonType();
                    if (fieldType.isPrimitiveType()) {
                        // 基本数据类型
                        return fieldType.asPrimitiveType().getType().asString();
                    } else if (fieldType.isClassOrInterfaceType()) {
                        // 类类型
                        return parseOutFullClassNameFromMethodAndSimpleClassName(method,
                                fieldType.asClassOrInterfaceType().getNameAsString());
                    } else if (fieldType.isArrayType()) {
                        // 数组类型
                        Type arrType = fieldType;
                        int count = 0; // 统计几维数组
                        String suffix = ""; // "[]"
                        while (arrType.isArrayType()) {
                            arrType = arrType.asArrayType().getComponentType();
                            count++;
                        }
                        for (int i = 0; i < count; i++) {
                            suffix += PathConstant.ARR_BRACKETS;
                        }
                        if (arrType.isPrimitiveType()) {
                            return arrType.asPrimitiveType().getType().asString() + suffix;
                        } else if (arrType.isClassOrInterfaceType()) {
                            return parseOutFullClassNameFromMethodAndSimpleClassName(method,
                                    arrType.asClassOrInterfaceType().getNameAsString()) + suffix;
                        } else {
                            return findFullClassNameFromImports(method, arrType.toString()) + suffix;
                        }
                    }
                    // 去泛型
                    return fieldType.toString().substring(0,
                            fieldType.toString().indexOf(PathConstant.LEFT_ANGLE_BRACKET));
                }
            }
        }
        return null;
    }

    /**
     * 查找方法所属类中相应字段的实现类的全限定名，包含包名，无泛型
     * 
     * @param method        方法
     * @param fieldName     字段名
     * @param declClassName 声明类的全限定名
     * @return 字段的全限定名
     */
    public String findFieldRealClassNameInClass(MethodDeclaration method, String fieldName, String declClassName) {
        if (StringUtils.isBlank(fieldName) || method == null) {
            return null;
        }

        Optional<CompilationUnit> cuOpt = method.findCompilationUnit();
        if (!cuOpt.isPresent()) {
            return null;
        }
        // 方法所属类的 java 源文件的编译单元
        CompilationUnit cu = cuOpt.get();

        // 查找 method 所属的类的，如果是内部类，一直往上找到最外层类
        Optional<Node> parent = method.getParentNode();
        while (parent.isPresent()) {
            if (!(parent.get() instanceof ClassOrInterfaceDeclaration) && !(parent.get() instanceof EnumDeclaration)) {
                break;
            }
            List<FieldDeclaration> fields = null;
            if (parent.get() instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) parent.get();
                fields = classDecl.getFields();
            }
            if (parent.get() instanceof EnumDeclaration) {
                EnumDeclaration classDecl = (EnumDeclaration) parent.get();
                fields = classDecl.getFields();
            }
            if (CollectionUtils.isEmpty(fields)) {
                parent = parent.get().getParentNode();
                continue;
            }
            // 遍历其中所有字段
            for (FieldDeclaration field : fields) {
                // 字段名不匹配
                if (!field.getVariables().stream().anyMatch(var -> fieldName.equals(var.getNameAsString()))) {
                    continue;
                }
                // 找到这个字段了
                // 没有赋值的区域
                if (!getFieldParser().isFieldHasInitializer(field)) {
                    if (!getFieldParser().isAutowiredOrResource(field)) {
                        // 该类 field 不含依赖注入的注解，直接返回声明的类名
                        return declClassName;
                    }
                    // 含有依赖注入的注解时，此时处理复杂，需要找到注入的是哪个类
                    return getFieldParser().inferRealClassNameFromAutowiredField(field, declClassName);
                }
                // 有赋值区域
                Expression initializer = field.getVariable(0).getInitializer().get();
                return getExpressionParser().parseOutRealClassNameFromInitializer(initializer);
            }
            parent = parent.get().getParentNode();
        }

        // 查找 cu 中的所有最外层类（因为一个 java 文件可以有写多个类，但是这些类是平行关系，不是内部类关系）
        for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
            if (!(typeDecl instanceof ClassOrInterfaceDeclaration) && !(typeDecl instanceof EnumDeclaration)) {
                continue;
            }
            List<FieldDeclaration> fields = null;
            if (typeDecl instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration classDecl = typeDecl.asClassOrInterfaceDeclaration();
                fields = classDecl.getFields();
            }
            if (typeDecl instanceof EnumDeclaration) {
                EnumDeclaration enumDecl = typeDecl.asEnumDeclaration();
                fields = enumDecl.getFields();
            }
            if (CollectionUtils.isEmpty(fields)) {
                continue;
            }
            // 遍历其中所有字段
            for (FieldDeclaration field : fields) {
                // 字段名不匹配
                if (!field.getVariables().stream().anyMatch(var -> fieldName.equals(var.getNameAsString()))) {
                    continue;
                }
                // 没有赋值的区域
                if (!getFieldParser().isFieldHasInitializer(field)) {
                    if (!getFieldParser().isAutowiredOrResource(field)) {
                        // 该类 field 不含依赖注入的注解，直接返回声明的类名
                        return declClassName;
                    }
                    // 含有依赖注入的注解时，此时处理复杂，需要找到注入的是哪个类
                    return getFieldParser().inferRealClassNameFromAutowiredField(field, declClassName);
                }
                // 有赋值区域
                Expression initializer = field.getVariable(0).getInitializer().get();
                return getExpressionParser().parseOutRealClassNameFromInitializer(initializer);
            }
        }
        return null;
    }

    /**
     * 获取方法所属的编译单元的，并且名字等于 simpleClassName 的类全限定名，带有包名，去掉泛型
     * 
     * @param method          方法声明
     * @param simpleClassName 简单类名
     * @return 完整类名
     */
    public String findInnerClassName(MethodDeclaration method, String simpleClassName) {
        if (StringUtils.isBlank(simpleClassName) || method == null) {
            return null;
        }

        // 方法所属类的 java 源文件的编译单元
        Optional<CompilationUnit> cuOpt = method.findCompilationUnit();
        if (!cuOpt.isPresent()) {
            return null;
        }
        CompilationUnit cu = cuOpt.get();

        // 编译单元就 1 个类
        if (cu.findAll(ClassOrInterfaceDeclaration.class).size() <= 1) {
            return null;
        }

        // 查找 method 所属的类的，如果是内部类，一直往上找到最外层类
        Optional<Node> parent = method.getParentNode();
        while (parent.isPresent()) {
            if (!(parent.get() instanceof ClassOrInterfaceDeclaration)) {
                break;
            }

            ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) parent.get();
            if (!simpleClassName.equals(classDecl.getNameAsString())) {
                parent = parent.get().getParentNode();
            }

            // 包名
            Optional<PackageDeclaration> packageDecl = cu.getPackageDeclaration();
            if (!packageDecl.isPresent()) {
                return simpleClassName;
            }
            return packageDecl.get().getNameAsString() + PathConstant.DOT + simpleClassName;
        }

        // 查找 cu 中的所有最外层类（因为一个 java 文件可以有写多个类，但是这些类是平行关系，不是内部类关系）
        for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
            if (!(typeDecl instanceof ClassOrInterfaceDeclaration) && !(typeDecl instanceof EnumDeclaration)) {
                continue;
            }
            NodeWithSimpleName nodeWithSimpleName = null;
            if (typeDecl.isClassOrInterfaceDeclaration()) {
                ClassOrInterfaceDeclaration classDecl = typeDecl.asClassOrInterfaceDeclaration();
                nodeWithSimpleName = classDecl;
            }
            if (typeDecl.isEnumDeclaration()) {
                EnumDeclaration enumDecl = typeDecl.asEnumDeclaration();
                nodeWithSimpleName = enumDecl;
            }
            // 如果没匹配上
            if (!simpleClassName.equals(nodeWithSimpleName.getNameAsString())) {
                continue;
            }
            // 匹配上
            Optional<PackageDeclaration> packageDecl = cu.getPackageDeclaration();
            if (!packageDecl.isPresent()) {
                // 直接返回简单类
                return simpleClassName;
            }
            // 返回带有包名
            return packageDecl.get().getNameAsString() + PathConstant.DOT + simpleClassName;
        }
        return null;
    }

    /**
     * 提取方法的完整参数类型。
     * 包含包名，不含有泛型
     * 
     * @param method 方法声明
     * @return 完整参数类型列表
     */
    public List<String> parseOutFullParamTypes(MethodDeclaration method) {
        List<String> fullParamTypes = new ArrayList<>();
        method.getParameters().forEach(param -> {
            try {
                fullParamTypes.add(getParameterParser().parseOutTypeStrWithNoGeneric(param));
            } catch (Exception e) {
                try {
                    fullParamTypes.add(getParameterParser().parseOutBaseTypeStrWithNoGeneric(param));
                } catch (Exception ex) {
                    fullParamTypes.add(WordConstant.PARAM_TYPE_UNKNOWN);
                }
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
        if (method == null) {
            return new ArrayList<>();
        }
        return method.getModifiers().stream().map(modifier -> modifier.getKeyword()).collect(Collectors.toList());
    }

    /**
     * 从MethodDeclaration中提取方法注解（使用JavaParser）
     *
     * @param method 方法声明
     * @return 方法注解信息的映射表，键为注解名称，值为参数映射表
     */
    public Map<String, Map<String, Object>> parseOutMethodAnnotations(MethodDeclaration method) {
        if (method == null) {
            return new HashMap<>();
        }
        Map<String, Map<String, Object>> annotations = new HashMap<>();
        NodeList<AnnotationExpr> annotationExprs = method.getAnnotations();

        for (AnnotationExpr annotation : annotationExprs) {
            String annotationName = annotation.getNameAsString();
            Map<String, Object> params = new HashMap<>();

            if (annotation.isNormalAnnotationExpr()) {
                NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
                for (MemberValuePair pair : normal.getPairs()) {
                    String paramName = pair.getNameAsString();
                    Object paramValue = getExpressionParser().extractAnnotationValue(pair.getValue());
                    params.put(paramName, paramValue);
                }
            } else if (annotation.isSingleMemberAnnotationExpr()) {
                SingleMemberAnnotationExpr single = annotation.asSingleMemberAnnotationExpr();
                Object paramValue = getExpressionParser().extractAnnotationValue(single.getMemberValue());
                params.put("value", paramValue);
            }

            annotations.put(annotationName, params);
        }

        return annotations;
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
            // 方法声明为空，说明是 jdk/第三方依赖，或者找不到，直接返回结束，表示里层再无调用关系
            return new ArrayList<>();
        }
        List<MethodCallInfo> calls = new ArrayList<>();
        final MethodDeclaration finalMethod = method;
        // 遍历方法体内的所有表达式，查找方法调用表达式
        for (Expression expr : method.findAll(Expression.class)) {
            if (!expr.isMethodCallExpr() && !expr.isObjectCreationExpr() && !expr.isMethodReferenceExpr()) {
                // 非方法调用表达式，继续
                continue;
            }

            // true 表达式可以解析（对应不含第三方依赖）
            // false 表达式不能解析（对应含有第三方依赖）
            boolean canResolve = true;

            /**
             * 获取关键信息
             * - 整体方法调用表达式，不含第三方依赖和含有第三方依赖的处理是不同的
             */
            String methodName = ""; // 方法名 required
            String fullDeclaringClass = ""; // 声明类名
            String realFullDeclaringClass = ""; // 实现类名（复杂）required
            List<String> paramTypes = new ArrayList<>(); // 方法参数 required

            ResolvedMethodLikeDeclaration resolvedMethod = null;
            try {
                // 获取 resolve，好拿到方法调用中的各种信息
                resolvedMethod = getExpressionParser().parseOutResolvedMethodFromExprWithNoOuterDep(expr);
                // 不含第三方依赖，可以解析
                canResolve = true;
                if (resolvedMethod == null) {
                    continue;
                }
            } catch (Exception e) {
                // 含有第三方依赖，不能被解析
                canResolve = false;
            }

            /* 获取 methodName 方法名 */
            /* 获取 paramTypes 参数类型 */
            if (canResolve) {
                // 方法名
                methodName = resolvedMethod.getName();
                // 获取参数类型：遍历方法参数，获取每个参数的类型
                try {
                    paramTypes = getResolveParser().parseOutParamsTypeFromResolvedMethodWithNoOuterDep(resolvedMethod);
                } catch (Exception e) {
                    // 参数含有第三方依赖，不能被解析
                    paramTypes = getExpressionParser().inferParamsTypeFromExprWithOuterDep(expr);
                }
                canResolve = true; // 不含第三方依赖，可以解析
            } else {
                // 方法名
                methodName = getExpressionParser().parseOutMethodNameFromExprWithOuterDep(expr);
                // 参数类型（其中第三方依赖对应 UNKNOWN）
                paramTypes = getExpressionParser().inferParamsTypeFromExprWithOuterDep(expr);
                canResolve = false; // 含有第三方依赖，不能解析
            }

            /* 获取声明类 fullDeclaringClass */
            if (canResolve) {
                // 声明类，这里加上了包，去掉了泛型
                fullDeclaringClass = getResolveParser().parseOutFullClassNameFromResolveWithNoGeneric(resolvedMethod,
                        resolvedMethod.getPackageName());
            } else {
                if (expr.isObjectCreationExpr()) {
                    // 构造方法的声明类 == 实现类，这里有包名，没有泛型
                    fullDeclaringClass = expressionParser
                            .inferFullClassNameFromObjCreationExprWithNoGeneric(expr.asObjectCreationExpr());
                } else {
                    // 本质就是找方法内这个变量名的声明的表达式
                    fullDeclaringClass = inferFullClassNameFromCall(finalMethod, expr, methodName, paramTypes);
                }
            }

            /* 获取实现类 realFullDeclaringClass */
            if (expr.isObjectCreationExpr()) {
                // 构造器
                // 构造器，构造方法的实现类和声明类一致
                realFullDeclaringClass = fullDeclaringClass;
            } else {
                // 非构造器
                // 分析实现类。实现类的分析方式都是一致的（复杂）
                realFullDeclaringClass = inferRealClassNameFromCall(finalMethod, expr, methodName, paramTypes,
                        fullDeclaringClass);
            }

            if (StringUtils.isNotBlank(methodName)) {
                calls.add(new MethodCallInfo(fullDeclaringClass, realFullDeclaringClass, methodName, paramTypes));
            }
        }

        return calls;
    }

    /**
     * 从方法调用推断实现类名（简化版本），去掉了泛型，含有包名
     * 
     * @param containingMethod  当前方法
     * @param expr              方法内的方法调用表达式
     * @param methodName        被调用的方法名
     * @param paramTypes        被调用的方法的参数类型列表
     * @param declClassWithPack 声明类的完整类名，包含包名，去掉泛型
     * @return 实现类的完整类名，包含包名，去掉泛型
     */
    private String inferRealClassNameFromCall(MethodDeclaration containingMethod, Expression expr, String methodName,
            List<String> paramTypes, String declClassWithPack) {
        if (containingMethod == null || expr == null || StringUtils.isBlank(methodName)) {
            return null;
        }
        if (!expr.isMethodCallExpr() && !expr.isObjectCreationExpr() && !expr.isMethodReferenceExpr()) {
            return null;
        }

        // 当前方法 containingMethod 所对应的包名
        String packageName = getNodeParser().parseOutPackStrFromCallDecl(containingMethod);
        Optional<Expression> scopeExprOpt = null;
        String scopeStr = "";

        // 方法调用表达式中 scope 区域
        scopeExprOpt = getExpressionParser().parseOutScopeExprFromCallExpr(expr);
        if (scopeExprOpt == null) {
            return null;
        }

        // scope 区域存在
        /**
         * - this/super
         * - 字符串："abc"
         * - 含有包名: com.xxx.User/com.xxx.User.name
         * - 简单类: User
         * - 常量: USER_NAME/userName/USERNAME
         * - 变量: userName
         */
        if (scopeExprOpt.isPresent()) {
            scopeStr = scopeExprOpt.get().toString().trim();

            // 注意 this/super 这种
            if (scopeExprOpt.get().isThisExpr()) {
                // 返回当前类的完整包名类
                return packageName + PathConstant.DOT + parseOutSimpleClassNameFromMethodDecl(containingMethod);
            }
            if (scopeExprOpt.get().isSuperExpr()) {
                return declClassWithPack;
            }

            // scopeStr 如果是字符串
            if (scopeStr.startsWith(PathConstant.DOUBLE_QUOTE) && scopeStr.endsWith(PathConstant.DOUBLE_QUOTE)) {
                return PathConstant.STRING_PACK;
            }

            // scopeStr 如果 System.out/err/in
            if (scopeStr.startsWith(PathConstant.SYSTEM_OUT) || scopeStr.startsWith(PathConstant.SYSTEM_ERR)) {
                // 实现类未知，有可能是 PathConstant.SYSTEM_PACK，也有可能是 PathConstant.SYSTEM_PACK 的父类
                // 常见情况处理
                if (methodName.equals(WordConstant.PRINTLN) || methodName.equals(WordConstant.PRINTF)
                        || methodName.equals(WordConstant.PRINT)) {
                    return PathConstant.SYSTEM_OUT_PACK;
                }
                return null;
            }
            if (scopeStr.startsWith(PathConstant.SYSTEM_IN)) {
                return null;
            }

            // 如果是 String/Object 这种类型，他们的包会自动 import，做特别处理
            if (declClassWithPack != null && declClassWithPack.startsWith(PathConstant.LANG_PACK)) {
                return declClassWithPack;
            }

            // scopeStr 含有包的形式，考虑直接返回了
            if (PackStrUtil.isPack(scopeStr)) {
                // scopeStr 本身是带有包名的类了
                if (PackStrUtil.isPackEndWithSimpleClass(scopeStr)) {
                    return scopeStr;
                }
                // scopeStr 本身是带有包名的 field 了，这里无法分析类名，形如 com.xxx.User.name
                if (PackStrUtil.isPackEndWithField(scopeStr)) {
                    // 这里要分析，过于复杂，直接返回 null
                    // NOTICE 比如 com.xxx.User.age，很难分析出 age 类型，需要在 User 中找到 age 的类型
                    return null;
                }
            }

            // scopeStr 是一个简单类名
            if (ClassStrUtil.isSimpleClassName(scopeStr)) {
                return parseOutFullClassNameFromMethodAndSimpleClassName(containingMethod, scopeStr);
            }

            // 如果是不含有包名的简单变量名的形式
            if (VarStrUtil.isVarStr(scopeStr)) {
                // 按照可能性，逐步减小 ⬇
                Optional<VariableDeclarationExpr> varDeclExprOpt = parseOutVarDeclExprBeforeMethodCallExprInMethod(
                        containingMethod, expr, scopeStr); // 变量声明表达式
                // 方法内变量声明存在
                if (varDeclExprOpt.isPresent()) {
                    VariableDeclarationExpr varDeclExpr = varDeclExprOpt.get();
                    // 带有包名，去掉泛型
                    return getExpressionParser().parseOutRealClassNameFromVarDeclExpr(varDeclExpr, scopeStr,
                            declClassWithPack);
                } else {
                    // 方法内变量声明不存在

                    // 也可能是 scopeStr 变量名在类的 field 有实现类
                    String realClassName = findFieldRealClassNameInClass(containingMethod, scopeStr, declClassWithPack);
                    if (StringUtils.isNotBlank(realClassName)) {
                        return realClassName;
                    }

                    // 也可能是 scopeStr 变量名声明为方法参数，但是这种，需要运行时才能知道实现类是啥

                    // 小概率是 scopeStr 变量名声明匹配 import 引入的变量，形如 static com.xxx.User.name
                    // NOTICE: 但是这里太复杂，还需要获取 com.xxx.User 编译单元再去解析，过于麻烦，这里不实现

                    // 其他情况，比如父类/祖先类中的变量等情况，返回 null
                    return null;
                }
            }

            // 如果是一个简单常量名
            if (VarStrUtil.isConstantStr(scopeStr)) {
                // 按照可能性，逐步减小 ⬇

                // 也有可能是当前 cu 中类的常量名
                String fullClassName = findFieldClassNameInClass(containingMethod, scopeStr);
                if (StringUtils.isNotBlank(fullClassName)) {
                    return fullClassName;
                }

                // 也可能是当前方法内的常量名
                Optional<VariableDeclarationExpr> varDeclExprOpt = parseOutVarDeclExprBeforeMethodCallExprInMethod(
                        containingMethod, expr, scopeStr); // 该 scopeStr 常量声明的地方
                if (varDeclExprOpt.isPresent()) {
                    VariableDeclarationExpr varDeclExpr = varDeclExprOpt.get();
                    // 带有包名，去掉泛型
                    return getExpressionParser().parseOutRealClassNameFromVarDeclExpr(varDeclExpr, scopeStr,
                            declClassWithPack);
                }

                // 其他情况，可能是父类/祖先类中的常量，甚至是 import 时的常量
                return null;
            }

            // 如果是嵌套的类，比如 User.Person
            if (ClassStrUtil.isNestedClassName(scopeStr)) {
                // import 能找到 User
                String firstSeg = scopeStr.split(PathConstant.ESCAPE_DOT)[0];
                firstSeg = firstSeg.substring(0, firstSeg.indexOf(PathConstant.LEFT_ANGLE_BRACKET)); // 已经不带有泛型
                // scopeStr 中去掉了 firstSeg 的部分，包含了 $，不带有泛型
                String otherSegStr = ClassStrUtil.transInnerClass2DollarWithNoGeneric(scopeStr)
                        .substring(firstSeg.length());
                String fullClassName = parseOutFullClassNameFromMethodAndSimpleClassName(containingMethod, firstSeg);
                if (StringUtils.isNotBlank(fullClassName)) {
                    return fullClassName + otherSegStr;
                }

                // 其他情况，比如是 父类 的，import * 等各种复杂情况
                return null;
            }

            // 如果是嵌套的 field
            if (ClassStrUtil.isClassConstant(scopeStr)) {
                String firSeg = scopeStr.split(PathConstant.ESCAPE_DOT)[0];
                // 第一个 seg 所表示类的全限定类名
                String firSegFullClassName = parseOutFullClassNameFromMethodAndSimpleClassName(containingMethod,
                        firSeg);
                if (StringUtils.isBlank(firSegFullClassName) || !firSegFullClassName.contains(PathConstant.DOT)) {
                    // 类名不存在 或者 类名没有包的形式，那么这个类最终是找不到，其中的 field 也无法分析类型
                    return null;
                }
                Boolean isEnum = isEnumType(firSegFullClassName);

                // 如果是枚举类型
                if (Objects.equal(isEnum, true)) {
                    return firSegFullClassName;
                }

                // 无法判断是否枚举，或者非枚举类型，则无法判定
                // 如果是其他的嵌套的 field，比如 User.name/User.NAME，过于复杂，这里先不分析
            }

            // 其他情况
            return null;
        }

        // 下面是 scope 区域不存在，比如是 put();
        if (!scopeExprOpt.isPresent()) {
            // 该方法要么是当前类的，要么是父类/祖先类的。这种情况和 declClassWithPack 一致
            if (StringUtils.isNotBlank(declClassWithPack)) {
                return declClassWithPack;
            }

            // 大概率，当前编译单元的方法，如果是，就返回当前类
            String fullClassName = findClassNameByEqualsMethod(containingMethod, methodName, paramTypes, packageName);
            if (StringUtils.isNotBlank(fullClassName)) {
                return fullClassName;
            }

            // 也有可能，如果有父类，且父类有该方法，这里过于复杂，先不分析
            // NOTICE: 涉及要分析父类，和父类的父类，在其中找拥有的方法，是否匹配

            // 其他情况，比如祖先类中的方法，或者其他
            return null;
        }

        return null;
    }

    /**
     * 从方法调用推断声明类名（简化版本），去掉了泛型，含有包名
     * 
     * @param containingMethod 当前方法
     * @param expr             方法内的方法调用表达式
     * @param methodName       方法名
     * @param paramTypes       参数类型列表
     * @return 声明类的完整类名，包含包名，去掉泛型
     */
    private String inferFullClassNameFromCall(MethodDeclaration containingMethod, Expression expr, String methodName,
            List<String> paramTypes) {
        if (containingMethod == null || expr == null || StringUtils.isBlank(methodName)) {
            return null;
        }
        if (!expr.isMethodCallExpr() && !expr.isObjectCreationExpr() && !expr.isMethodReferenceExpr()) {
            return null;
        }

        // 当前方法 containingMethod 所对应的包名
        String packageName = getNodeParser().parseOutPackStrFromCallDecl(containingMethod);
        Optional<Expression> scopeExprOpt = null;
        String scopeStr = "";

        // 方法调用表达式中 scope 区域
        scopeExprOpt = getExpressionParser().parseOutScopeExprFromCallExpr(expr);
        if (scopeExprOpt == null) {
            return null;
        }

        // scope 区域存在
        /**
         * - this/super
         * - 字符串："abc"
         * - 含有包名: com.xxx.User/com.xxx.User.name
         * - 简单类: User
         * - 常量: USER_NAME/userName/USERNAME
         * - 变量: userName
         */
        if (scopeExprOpt.isPresent()) {
            scopeStr = scopeExprOpt.get().toString().trim();

            // 注意 this/super 这种
            if (scopeExprOpt.get().isThisExpr()) {
                // 返回当前类的完整包名类
                return packageName + PathConstant.DOT + parseOutSimpleClassNameFromMethodDecl(containingMethod);
            }
            if (scopeExprOpt.get().isSuperExpr()) {
                // 因为分析继承的类和祖先类太过于复杂，这里直接返回 null
                // NOTICE: 要不断循环找到父类的父类，找到其中的方法匹配到这个方法的类
                return null;
            }

            // scopeStr 如果是字符串
            if (scopeStr.startsWith(PathConstant.DOUBLE_QUOTE) && scopeStr.endsWith(PathConstant.DOUBLE_QUOTE)) {
                return PathConstant.STRING_PACK;
            }

            // scopeStr 如果 System.out/err/in
            if (scopeStr.startsWith(PathConstant.SYSTEM_OUT) || scopeStr.startsWith(PathConstant.SYSTEM_ERR)) {
                return PathConstant.SYSTEM_OUT_PACK;
            }
            if (scopeStr.startsWith(PathConstant.SYSTEM_IN)) {
                return PathConstant.SYSTEM_IN_PACK;
            }

            // scopeStr 含有包的形式，考虑直接返回了
            if (PackStrUtil.isPack(scopeStr)) {
                // scopeStr 本身是带有包名的简单类了
                if (PackStrUtil.isPackEndWithSimpleClass(scopeStr)) {
                    return scopeStr;
                }
                // scopeStr 本身是带有包名的 field 了，这里无法分析类名，形如 com.xxx.User.name
                if (PackStrUtil.isPackEndWithField(scopeStr)) {
                    // 这里要分析，过于复杂，直接返回 null
                    // NOTICE 比如 com.xxx.User.age，很难分析出 age 类型，需要在 User 中找到 age 的类型
                    return null;
                }
            }

            // scopeStr 是一个简单类名
            if (ClassStrUtil.isSimpleClassName(scopeStr)) {
                return parseOutFullClassNameFromMethodAndSimpleClassName(containingMethod, scopeStr);
            }

            // 如果是不含有包名的简单变量名的形式
            if (VarStrUtil.isVarStr(scopeStr)) {
                // 按照可能性，逐步减小 ⬇
                Optional<VariableDeclarationExpr> varDeclExprOpt = parseOutVarDeclExprBeforeMethodCallExprInMethod(
                        containingMethod, expr, scopeStr); // 变量声明表达式
                // 方法内变量声明存在
                if (varDeclExprOpt.isPresent()) {
                    VariableDeclarationExpr varDeclExpr = varDeclExprOpt.get();
                    // 带有包名，去掉泛型
                    return getExpressionParser().parseOutClassNameFromVarDeclExpr(containingMethod, varDeclExpr,
                            scopeStr);
                } else {
                    // 方法内变量声明不存在

                    // 也可能是 scopeStr 变量名声明为方法参数
                    String fullClassName = parseOutClassNameFromMethodParams(containingMethod, scopeStr);
                    if (StringUtils.isNotBlank(fullClassName)) {
                        return fullClassName;
                    }

                    // 也可能是 scopeStr 变量名在类的 field
                    fullClassName = findFieldClassNameInClass(containingMethod, scopeStr);
                    if (StringUtils.isNotBlank(fullClassName)) {
                        return fullClassName;
                    }

                    // 小概率是 scopeStr 变量名声明匹配 import 引入的变量，形如 static com.xxx.User.name
                    // NOTICE: 但是这里太复杂，还需要获取 com.xxx.User 编译单元再去解析，过于麻烦，这里不实现

                    // 其他情况，比如父类/祖先类中的变量等情况，返回 null
                    return null;
                }
            }

            // 如果是一个简单常量名
            if (VarStrUtil.isConstantStr(scopeStr)) {
                // 按照可能性，逐步减小 ⬇

                // 更有可能是当前 cu 中类的常量名
                String fullClassName = findFieldClassNameInClass(containingMethod, scopeStr);
                if (StringUtils.isNotBlank(fullClassName)) {
                    return fullClassName;
                }

                // 也可能是当前方法内的常量名
                Optional<VariableDeclarationExpr> varDeclExprOpt = parseOutVarDeclExprBeforeMethodCallExprInMethod(
                        containingMethod, expr, scopeStr); // 该 scopeStr 常量声明的地方
                if (varDeclExprOpt.isPresent()) {
                    VariableDeclarationExpr varDeclExpr = varDeclExprOpt.get();
                    // 带有包名，去掉泛型
                    return getExpressionParser().parseOutClassNameFromVarDeclExpr(containingMethod, varDeclExpr,
                            scopeStr);
                }

                // 其他情况，可能是父类/祖先类中的常量，甚至是 import 时的常量
                return null;
            }

            // 如果是嵌套的类，比如 User.Person
            // 走到这，scopeStr 肯定不带包名
            if (ClassStrUtil.isNestedClassName(scopeStr)) {
                // import 能找到 User
                String firstSeg = scopeStr.split(PathConstant.ESCAPE_DOT)[0];
                firstSeg = firstSeg.substring(0, firstSeg.indexOf(PathConstant.LEFT_ANGLE_BRACKET)); // 已经不带有泛型
                // scopeStr 中去掉了 firstSeg 的部分，包含了 $，不带有泛型
                String otherSegStr = ClassStrUtil.transInnerClass2DollarWithNoGeneric(scopeStr)
                        .substring(firstSeg.length());
                String fullClassName = parseOutFullClassNameFromMethodAndSimpleClassName(containingMethod, firstSeg);
                if (StringUtils.isNotBlank(fullClassName)) {
                    return fullClassName + otherSegStr;
                }

                // 其他情况，比如是 父类 的，import * 等各种复杂情况
                return null;
            }

            // 如果是嵌套的 field
            if (ClassStrUtil.isClassConstant(scopeStr)) {
                String firSeg = scopeStr.split(PathConstant.ESCAPE_DOT)[0];
                // 第一个 seg 所表示类的全限定类名
                String firSegFullClassName = parseOutFullClassNameFromMethodAndSimpleClassName(containingMethod,
                        firSeg);
                if (StringUtils.isBlank(firSegFullClassName) || !firSegFullClassName.contains(PathConstant.DOT)) {
                    // 类名不存在 或者 类名没有包的形式，那么这个类最终是找不到，其中的 field 也无法分析类型
                    return null;
                }
                Boolean isEnum = isEnumType(firSegFullClassName);

                // 如果是枚举类型
                if (Objects.equal(isEnum, true)) {
                    return firSegFullClassName;
                }

                // 无法判断是否枚举，或者非枚举类型，则无法判定
                // 如果是其他的嵌套的 field，比如 User.name/User.NAME，过于复杂，这里先不分析
            }

            // 其他情况
            // 比如长串的 lambda 表达式的 scopeStr
            return null;
        }

        // 下面是 scope 区域不存在，比如 put();
        /**
         * - 当前编译单元中的方法
         * - extends 父类的方法/祖先类的方法
         */
        if (!scopeExprOpt.isPresent()) {
            // 大概率，当前编译单元的方法，如果是，就返回当前类
            String fullClassName = findClassNameByEqualsMethod(containingMethod, methodName, paramTypes, packageName);
            if (StringUtils.isNotBlank(fullClassName)) {
                return fullClassName;
            }

            // 也有可能，如果有父类，且父类有该方法，这里过于复杂，先不分析
            // NOTICE: 涉及要分析父类，和父类的父类，在其中找拥有的方法，是否匹配

            // 其他情况，比如祖先类中的方法，或者其他
            return null;
        }

        return null;
    }

    /**
     * 方法名是 methodName，它没有 scope，该被调用方法所属 containingMethod 内，这里需要找到 methodName
     * 完整包名的类名
     * 
     * @param containingMethod 被调用方法所属的方法
     * @param methodName       被调用的方法名
     * @param paramTypes       被调用的方法的参数类型列表
     * @return 全限定类名
     */
    public String findClassNameByEqualsMethod(MethodDeclaration containingMethod, String methodName,
            List<String> paramTypes, String packName) {
        // 方法名不能为空
        if (StringUtils.isBlank(methodName) || containingMethod == null) {
            return null;
        }

        // 不断向上找，模拟内部类的场景
        Optional<Node> parent = containingMethod.getParentNode();
        // 遍历每个类（内部类）
        while (parent.isPresent()) {
            if (!(parent.get() instanceof ClassOrInterfaceDeclaration) && !(parent.get() instanceof EnumDeclaration)) {
                break;
            }
            String simpleClassName = ""; // 最终高这里拼接出 User$Person 的形式
            List<MethodDeclaration> methods = null;
            // 如果是类
            if (parent.get() instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) parent.get();
                methods = classDecl.getMethods(); // 该类所有方法
                if (StringUtils.isBlank(simpleClassName)) {
                    simpleClassName = classDecl.getNameAsString(); // 首次
                } else {
                    simpleClassName = (classDecl.getNameAsString() + PathConstant.HYP_DOLLAR + simpleClassName);
                }
            }
            // 如果是枚举
            if (parent.get() instanceof EnumDeclaration) {
                EnumDeclaration classDecl = (EnumDeclaration) parent.get();
                methods = classDecl.getMethods();
                if (StringUtils.isBlank(simpleClassName)) {
                    simpleClassName = classDecl.getNameAsString(); // 首次
                } else {
                    simpleClassName = (classDecl.getNameAsString() + PathConstant.HYP_DOLLAR + simpleClassName);
                }
            }
            if (CollectionUtils.isEmpty(methods)) {
                parent = parent.get().getParentNode();
                continue;
            }
            // 该类中遍历所有方法
            for (MethodDeclaration methodDecl : methods) {
                // 方法名不匹配 continue
                if (!methodName.equals(methodDecl.getNameAsString())) {
                    continue;
                }
                // 方法参数为空
                List<Parameter> params = methodDecl.getParameters();
                if (CollectionUtils.isEmpty(params) && CollectionUtils.isEmpty(paramTypes)) {
                    // 都无参数，匹配
                    return packName + PathConstant.DOT + simpleClassName;
                }
                // 方法参数数量就对不上
                if (params.size() != paramTypes.size()) {
                    continue;
                }
                // 参数数量对上，比较每个参数的类型
                List<String> paramsTypeList = parseOutFullParamTypes(methodDecl);
                boolean isFieldMatch = true; // 判断 field 是否都匹配上
                for (int i = 0; i < params.size(); i++) {
                    if (paramsTypeList.get(i).equals(WordConstant.PARAM_TYPE_UNKNOWN)
                            || paramTypes.get(i).equals(WordConstant.PARAM_TYPE_UNKNOWN)) {
                        continue;
                    }
                    // 这个不匹配
                    if (!ClassStrUtil.typeMatches(paramsTypeList.get(i), paramTypes.get(i))) {
                        isFieldMatch = false;
                        break;
                    }
                }
                if (isFieldMatch) {
                    return packName + PathConstant.DOT + simpleClassName;
                }
            }
            parent = parent.get().getParentNode();
        }

        // 查找 cu 中的所有最外层类（因为一个 java 文件可以有写多个类，但是这些类是平行关系，不是内部类关系）
        Optional<CompilationUnit> cuOpt = containingMethod.findCompilationUnit();
        if (!cuOpt.isPresent()) {
            return null;
        }
        CompilationUnit cu = cuOpt.get();
        String simpleClassName = "";
        List<MethodDeclaration> methods = null;
        // 遍历每个类
        for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
            if (!(typeDecl instanceof ClassOrInterfaceDeclaration) && !(typeDecl instanceof EnumDeclaration)) {
                continue;
            }
            if (typeDecl instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration classDecl = typeDecl.asClassOrInterfaceDeclaration();
                methods = classDecl.getMethods();
                if (StringUtils.isBlank(simpleClassName)) {
                    simpleClassName = classDecl.getNameAsString();
                } else {
                    simpleClassName = (simpleClassName + PathConstant.HYP_DOLLAR + classDecl.getNameAsString()); // 不带泛型
                }
            }
            if (typeDecl instanceof EnumDeclaration) {
                EnumDeclaration classDecl = typeDecl.asEnumDeclaration();
                methods = classDecl.getMethods();
                if (StringUtils.isBlank(simpleClassName)) {
                    simpleClassName = classDecl.getNameAsString();
                } else {
                    simpleClassName = (simpleClassName + PathConstant.HYP_DOLLAR + classDecl.getNameAsString()); // 不带泛型
                }
            }
            if (CollectionUtils.isEmpty(methods)) {
                parent = parent.get().getParentNode();
                continue;
            }
            // 遍历所有方法
            for (MethodDeclaration methodDecl : methods) {
                // 方法名不匹配 continue
                if (!methodName.equals(methodDecl.getNameAsString())) {
                    continue;
                }
                // 方法参数为空
                List<Parameter> params = methodDecl.getParameters();
                if (CollectionUtils.isEmpty(params) && CollectionUtils.isEmpty(paramTypes)) {
                    // 都无参数，匹配
                    return packName + PathConstant.DOT + simpleClassName;
                }
                // 方法参数数量就对不上
                if (params.size() != paramTypes.size()) {
                    continue;
                }
                // 参数数量对上，比较每个参数的类型
                List<String> paramsTypeList = parseOutFullParamTypes(methodDecl);
                boolean isFieldMatch = true; // 判断 field 是否都匹配上
                for (int i = 0; i < params.size(); i++) {
                    if (paramsTypeList.get(i).equals(WordConstant.PARAM_TYPE_UNKNOWN)
                            || paramTypes.get(i).equals(WordConstant.PARAM_TYPE_UNKNOWN)) {
                        continue;
                    }
                    // 这个不匹配
                    if (!ClassStrUtil.typeMatches(paramsTypeList.get(i), paramTypes.get(i))) {
                        isFieldMatch = false;
                        break;
                    }
                }
                if (isFieldMatch) {
                    return packName + PathConstant.DOT + simpleClassName;
                }
            }
        }

        return null;
    }

    /**
     * 在 method 中找到某个方法调用表达式的其 scope 声明时候的变量表达式
     * 
     * @param method   方法声明
     * @param callExpr 方法调用表达式
     * @param varName  调用时候 scope 变量名
     * @return
     */
    public Optional<VariableDeclarationExpr> parseOutVarDeclExprBeforeMethodCallExprInMethod(MethodDeclaration method,
            Expression callExpr, String varName) {
        if (method == null || callExpr == null || StringUtils.isBlank(varName)) {
            return Optional.empty();
        }
        if (!method.findAll(Expression.class).contains(callExpr)) {
            return Optional.empty();
        }

        VariableDeclarationExpr varDeclExpr = null;

        // 遍历方法中的每个表达式，找到距离 callExpr 表达式最近的那个变量声明处
        for (Expression expr : method.findAll(Expression.class)) {
            if (expr.isVariableDeclarationExpr()) {
                // 如果是变量声明表达式，找到变量值
                VariableDeclarationExpr varDeclExprTmp = expr.asVariableDeclarationExpr();
                if (getExpressionParser().isVarDeclExprMatchScopeName(varDeclExprTmp, varName)) {
                    varDeclExpr = varDeclExprTmp;
                }
            }
            if (expr == callExpr) {
                // 遍历到这个方法调用表达式了
                break;
            }
        }
        return Optional.ofNullable(varDeclExpr);
    }

    /**
     * 返回简单类名，不带有包名，无泛型
     * 
     * @param method 方法声明
     * @return 简单类名
     */
    public String parseOutSimpleClassNameFromMethodDecl(MethodDeclaration method) {
        if (method == null) {
            return null;
        }
        List<String> classNames = new ArrayList<>();
        Optional<Node> current = method.getParentNode();
        while (current.isPresent() && current.get() instanceof NodeWithSimpleName<?>) {
            classNames.add(((NodeWithSimpleName<?>) current.get()).getNameAsString());
            current = current.get().getParentNode();
        }
        if (classNames.isEmpty()) {
            return null;
        }
        Collections.reverse(classNames);
        return String.join(PathConstant.HYP_DOLLAR, classNames);
    }

    /**
     * 从方法声明中解析其所属的类名，带有包名，去掉泛型
     * 比如 User
     * 
     * @param method      方法声明
     * @param packageName 包名
     * @return 全限定类名，去掉泛型
     */
    public String parseOutFullClassNameFromMethodDeclWithNoGeneric(MethodDeclaration method, String packageName) {
        return method.findCompilationUnit().flatMap(cu -> cu.getTypes().stream().findFirst())
                .map(type -> {
                    String typeName = type.getNameAsString();
                    // 加上包名
                    String fullClassName = (packageName.isEmpty() || typeName.contains(PathConstant.DOT)) ? typeName
                            : packageName + PathConstant.DOT + typeName;
                    // 去除泛型
                    if (fullClassName != null && fullClassName.contains(PathConstant.LEFT_ANGLE_BRACKET)) {
                        fullClassName = fullClassName.substring(0,
                                fullClassName.indexOf(PathConstant.LEFT_ANGLE_BRACKET));
                    }
                    return fullClassName;
                })
                .orElse("");
    }

    /**
     * 判断方法声明是否与指定方法名匹配
     * 
     * @param method     方法声明
     * @param methodName 方法名
     * @return 是否匹配成功
     */
    public boolean isMethodNameEqual(MethodDeclaration method, String methodName) {
        if (method == null || StringUtils.isBlank(methodName)) {
            return false;
        }
        return method.getNameAsString().equals(methodName);
    }

    /**
     * 从方法声明的入参中找到参数的类名，带有包名，去掉泛型
     * 
     * @param method  方法声明
     * @param varName 变量名
     * @return 参数类型的全限定名，去掉泛型
     */
    public String parseOutClassNameFromMethodParams(MethodDeclaration method, String varName) {
        if (method == null || StringUtils.isBlank(varName)) {
            return null;
        }
        for (Parameter param : method.getParameters()) {
            if (!param.getNameAsString().equals(varName)) {
                // 变量名不匹配
                continue;
            }

            Type paramType = param.getType();

            // 获取参数类型的全限定名
            String fullyQualifiedName;

            if (paramType.isClassOrInterfaceType()) {
                // 类或接口类型
                try {
                    fullyQualifiedName = paramType.asClassOrInterfaceType().resolve().asReferenceType()
                            .getQualifiedName();
                } catch (Exception e) {
                    fullyQualifiedName = paramType.asClassOrInterfaceType().getNameAsString();
                    // NOTICE: 这里后续考虑更复杂查找，比如查找 import
                }
            } else if (paramType.isPrimitiveType()) {
                // 基本类型
                try {
                    fullyQualifiedName = paramType.asPrimitiveType().toString();
                } catch (Exception e) {
                    fullyQualifiedName = paramType.toString();
                }
            } else if (paramType.isArrayType()) {
                // 数组类型 - 获取组件类型的全限定名
                Type componentType = paramType.asArrayType().getComponentType();
                if (componentType.isClassOrInterfaceType()) {
                    try {
                        fullyQualifiedName = componentType.asClassOrInterfaceType().resolve().asReferenceType()
                                .getQualifiedName() + PathConstant.ARR_BRACKETS;
                    } catch (Exception e) {
                        fullyQualifiedName = componentType.asClassOrInterfaceType().getNameAsString()
                                + PathConstant.ARR_BRACKETS;
                        // NOTICE: 这里后续考虑更复杂查找，比如查找 import
                    }
                } else {
                    fullyQualifiedName = componentType.toString() + PathConstant.ARR_BRACKETS;
                }
            } else if (paramType.isWildcardType()) {
                // 通配符类型：? extends Number
                fullyQualifiedName = PathConstant.OBJ_PACK;
            } else {
                // 其他类型
                fullyQualifiedName = paramType.toString();
            }
            return fullyQualifiedName;
        }
        return null;
    }

    /**
     * 从方法声明，和方法内部的一个变量的简单类名（简单类名来自方法内部的一个表达式的 scope 区域），推测其来源的包名
     * 带有包名，不带泛形
     * 
     * @param methodDecl      方法声明
     * @param simpleClassName 简单类名，不带泛型
     * @return 类的全限定名，带有包名，不带泛形
     */
    public String parseOutFullClassNameFromMethodAndSimpleClassName(MethodDeclaration methodDecl,
            String simpleClassName) {
        // 按照可能性，逐步减小 ⬇

        // 大概率是 import 中写明的类名，有包名，不带泛型
        String fullClassName = findFullClassNameFromImports(methodDecl, simpleClassName);
        if (StringUtils.isNotBlank(fullClassName)) {
            return fullClassName;
        }

        // 也有可能是当前 java 源文件中的最外层类，或者是 method 往上层的类（内部类）
        fullClassName = findInnerClassName(methodDecl, simpleClassName);
        if (StringUtils.isNotBlank(fullClassName)) {
            return fullClassName;
        }

        // 可能是同包下的类
        if (Objects.equal(isImportStarExist(methodDecl, simpleClassName), false)) {
            // 若不存在 import *，则说明来源于同包下
            return getNodeParser().parseOutPackStrFromCallDecl(methodDecl) + PathConstant.DOT + simpleClassName;
        }

        // 其他情况不知道是来源于同包下，还是来源于 import *

        // 兜底，分析不出来，返回 null
        // NOTICE 其实还有可能是当前类的父类，但是父类的包还得分析，很复杂，这里先不实现
        return simpleClassName;
    }

    /**
     * 判断是否为枚举类型
     * 
     * @param fullClassName 类的全限定名，带有包名，不带泛形
     * @return 是否为枚举类型, null 表示未知，true 表示是，false 表示否
     */
    private Boolean isEnumType(String fullClassName) {
        // 简单类名
        String simpleName = fullClassName.substring(fullClassName.lastIndexOf(PathConstant.DOT) + 1);

        CompilationUnit cu = getFileParser().parseOutCompilationUnit(fullClassName);
        if (cu == null) {
            return null;
        }

        Optional<EnumDeclaration> enumDecl = cu.findFirst(EnumDeclaration.class,
                ed -> ed.getNameAsString().equals(simpleName));
        if (enumDecl == null) {
            return false;
        }

        if (enumDecl.isPresent()) {
            return true;
        }

        return false;
    }
}