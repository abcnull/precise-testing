package org.example.resolver.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.example.constant.PathConstant;
import org.example.constant.WordConstant;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration;
import com.google.common.base.Objects;

/**
 * 表达式解析器
 */
public class ExpressionParser {
    private NodeParser nodeParser;
    private MethodDeclParser methodParser;
    private VariableDeclParser variableParser;

    private MethodDeclParser getMethodParser() {
        if (methodParser == null) {
            methodParser = new MethodDeclParser();
        }
        return methodParser;
    }

    private NodeParser getNodeParser() {
        if (nodeParser == null) {
            nodeParser = new NodeParser();
        }
        return nodeParser;
    }

    private VariableDeclParser getVariableParser() {
        if (variableParser == null) {
            variableParser = new VariableDeclParser();
        }
        return variableParser;
    }

    /**
     * 检查变量声明中是否匹配某作用域名称
     * 
     * @param varDeclExpr 变量声明表达式
     * @param scopeName   作用域名称
     * @return 如果匹配则返回 true，否则返回 false
     */
    public boolean isVarDeclExprMatchScopeName(VariableDeclarationExpr varDeclExpr, String scopeName) {
        if (varDeclExpr == null || scopeName == null) {
            return false;
        }
        return varDeclExpr.getVariables().stream()
                .anyMatch(var -> var.getNameAsString().equals(scopeName));
    }

    /**
     * 检查方法调用表达式是否包含作用域
     * 
     * @param callExpr 方法调用表达式
     * @return 如果包含作用域则返回 true，否则返回 false
     */
    public boolean isMethodCallExprHasScope(MethodCallExpr callExpr) {
        if (callExpr == null) {
            return false;
        }
        return callExpr.getScope().isPresent();
    }

    /**
     * 从方法调用表达式中解析出作用域表达式
     * 
     * @param callExpr 方法调用表达式
     * @return 作用域表达式
     */
    public Expression parseOutScopeExprFromMethodCallExpr(MethodCallExpr callExpr) {
        if (callExpr == null || !isMethodCallExprHasScope(callExpr)) {
            return null;
        }
        return callExpr.getScope().get();
    }

    /**
     * 从方法调用表达式中解析出作用域表达式，如果没有作用域则返回默认表达式
     * 
     * @param callExpr 方法调用表达式
     * @param expr     默认表达式
     * @return 作用域表达式
     */
    public Expression parseOutScopeExprFromMethodCallExprOrElse(MethodCallExpr callExpr, Expression expr) {
        if (callExpr == null || !isMethodCallExprHasScope(callExpr)) {
            return expr;
        }
        return callExpr.getScope().get();
    }

    /**
     * 从对象创建表达式中解析出基础类名，一般不带有包名，带有泛型
     * 比如 new ArrayList<>
     * 
     * @param creationExpr 对象创建表达式
     * @return 基础类名
     */
    public String parseOutBaseClassNameFromObjCreationExpr(ObjectCreationExpr creationExpr) {
        if (creationExpr == null) {
            return null;
        }
        // 不能使用 resolve，所以直接获取类名了，不带有包名，带有泛型
        return creationExpr.getType().toString();
    }

    /**
     * 从对象创建表达式中解析出基础类名，一般不带有包名，不带有泛型
     * 比如 new ArrayList<>() 解析出 ArrayList
     * 
     * @param creationExpr 对象创建表达式
     * @return 基础类名
     */
    public String parseOutBaseClassNameFromObjCreationExprWithNoGeneric(ObjectCreationExpr creationExpr) {
        if (creationExpr == null) {
            return null;
        }
        // 不能使用 resolve，所以直接获取类名了，无包名，无泛型，内部类返回类似 User$Person
        return creationExpr.getType().getNameAsString();
    }

    /**
     * 从对象创建表达式中解析出类名，带有包名，不带有泛型
     * 比如 new ArrayList<>() 解析出 ArrayList
     * 
     * @param creationExpr 对象创建表达式
     * @return 类名
     */
    public String inferFullClassNameFromObjCreationExprWithNoGeneric(ObjectCreationExpr creationExpr) {
        if (creationExpr == null) {
            return null;
        }

        // 简单类名，无包名，无泛型
        String className = parseOutBaseClassNameFromObjCreationExprWithNoGeneric(creationExpr);
        if (StringUtils.isBlank(className)) {
            return null;
        } else if (className.contains(PathConstant.DOT)) {
            return className;
        }

        // 不能使用 resolve，所以直接获取类名了，无包名，无泛型，内部类返回类似 User$Person
        return getNodeParser().parseOutPackStrFromCallDecl(creationExpr) + PathConstant.DOT + className;
    }

    /**
     * 从表达式（参数表达式）中解析出类型字符串，带有包名，带有泛型
     * 比如 a = ""，解析出 "" 对应 java.lang.String
     * 
     * @param expr 表达式
     * @return 类型字符串
     */
    public String parseOutTypeStrFromExpr(Expression expr) {
        if (expr == null) {
            return null;
        }
        return expr.calculateResolvedType().describe();
    }

    /**
     * 从表达式（参数表达式）中解析出类型字符串，带有包名，去掉泛型
     * 比如 a = ""，解析出 "" 对应 java.lang.String
     * 
     * @param expr 表达式
     * @return 类型字符串
     */
    public String parseOutTypeStrFromExprWithNoGeneric(Expression expr) {
        String paramStr = parseOutTypeStrFromExpr(expr);

        // 去泛型
        if (paramStr != null && paramStr.contains(PathConstant.LEFT_ANGLE_BRACKET)) {
            paramStr = paramStr.substring(0, paramStr.indexOf(PathConstant.LEFT_ANGLE_BRACKET));
        }
        return paramStr;
    }

    /**
     * 从类表达式中解析出基础类名，不带有包名，带有泛型
     * 比如 ArrayList<>
     * 
     * @param classExpr 类表达式
     * @return 基础类名
     */
    public String parseOutBaseClassFromClassExpr(ClassExpr classExpr) {
        if (classExpr == null) {
            return null;
        }
        return classExpr.getTypeAsString();
    }

    /**
     * 从类表达式中解析出基础类名，不带有包名，不带有泛型
     * 比如 ArrayList<> 解析出 ArrayList
     * 
     * @param classExpr 类表达式
     * @return 基础类名
     */
    public String parseOutBaseClassFromClassExprWithNoGeneric(ClassExpr classExpr) {
        if (classExpr == null) {
            return null;
        }
        String className = parseOutBaseClassFromClassExpr(classExpr);

        // 去掉泛型
        if (className != null && className.contains(PathConstant.LEFT_ANGLE_BRACKET)) {
            className = className.substring(0, className.indexOf(PathConstant.LEFT_ANGLE_BRACKET));
        }

        return className;
    }

    /**
     * 从变量声明表达式中解析出变量声明
     * 
     * @param varDeclExpr  变量声明表达式
     * @param variableName 变量名称
     * @return 变量声明
     */
    public Optional<VariableDeclarator> parseOutVarDeclFromVarDeclExpr(VariableDeclarationExpr varDeclExpr,
            String variableName) {
        if (varDeclExpr == null || StringUtils.isBlank(variableName)) {
            return Optional.empty();
        }
        return varDeclExpr.getVariables().stream()
                .filter(var -> getVariableParser().isVarEqualsName(var, variableName))
                .reduce((first, second) -> second);
    }

    /**
     * 从表达式中解析出 ResolvedMethodLikeDeclaration
     * 适用于该表达式不包含外部依赖，否则 resolve() 时候会抛出异常
     * 
     * @param expr 表达式
     * @return ResolvedMethodLikeDeclaration
     */
    public ResolvedMethodLikeDeclaration parseOutResolvedMethodFromExprWithNoOuterDep(Expression expr) {
        if (expr == null) {
            return null;
        }
        ResolvedMethodLikeDeclaration resolvedMethod = null;
        if (expr.isMethodCallExpr()) {
            // 普通方法调用
            MethodCallExpr callExpr = expr.asMethodCallExpr();
            resolvedMethod = callExpr.resolve();
        } else if (expr.isObjectCreationExpr()) {
            // 构造函数
            ObjectCreationExpr callExpr = expr.asObjectCreationExpr();
            resolvedMethod = callExpr.resolve();
        } else if (expr.isMethodReferenceExpr()) {
            // java8+，如 StringUtils::isEmpty
            MethodReferenceExpr callExpr = expr.asMethodReferenceExpr();
            resolvedMethod = callExpr.resolve();
        }
        return resolvedMethod;
    }

    /**
     * 从方法调用表达式中解析出参数类型字符串列表，带有包名，去掉泛型
     * 适用于该表达式包含外部依赖，不能 resolve() 来进一步解析方法入参的场景
     * 
     * @param callExpr 方法调用表达式
     * @return 参数类型字符串列表
     */
    public List<String> parseOutParamsTypeFromMethodCallExprWithOuterDep(MethodCallExpr callExpr) {
        if (callExpr == null) {
            return null;
        }
        List<String> paramTypes = new ArrayList<>();
        // 获取参数类型
        callExpr.getArguments().forEach(arg -> {
            try {
                // 带有包名，去掉泛型
                paramTypes.add(parseOutTypeStrFromExprWithNoGeneric(arg));
            } catch (Exception ex) {
                // 参数是第三方依赖，此种情况。类型未知
                // arg.toString() 只能返回变量名本身，比如 JSONObject obj 返回的 obj
                paramTypes.add(WordConstant.PARAM_TYPE_UNKNOWN);
            }
        });
        return paramTypes;
    }

    /**
     * 从表达式中解析出参数类型字符串列表，包含包名，去掉了泛型
     * 适用于该表达式包含外部依赖，不能 resolve() 来进一步解析方法入参的场景
     * 
     * @param expr 表达式
     * @return 参数类型字符串列表
     */
    public List<String> inferParamsTypeFromExprWithOuterDep(Expression expr) {
        if (expr == null) {
            return null;
        }
        if (expr.isMethodCallExpr()) {
            // 普通方法调用
            MethodCallExpr callExpr = expr.asMethodCallExpr();
            return parseOutParamsTypeFromMethodCallExprWithOuterDep(callExpr);
        } else if (expr.isObjectCreationExpr()) {
            // 构造函数
            ObjectCreationExpr callExpr = expr.asObjectCreationExpr();
            return parseOutParamsTypeFromObjCreExprWithOuterDep(callExpr);
        } else if (expr.isMethodReferenceExpr()) {
            // java8+，如 StringUtils::isEmpty
            // UNKNOWN 参数类型
            return Collections.singletonList(WordConstant.PARAM_TYPE_UNKNOWN);
        }
        return null;
    }

    /**
     * 从表达式中解析出方法名,包含包名，去掉泛型
     * 适用于该表达式包含外部依赖，不能 resolve() 来进一步解析方法入参的场景
     * 
     * @param expr 表达式
     * @return 方法名
     */
    public String parseOutMethodNameFromExprWithOuterDep(Expression expr) {
        if (expr == null) {
            return null;
        }
        if (expr.isMethodCallExpr()) {
            // 普通方法调用
            MethodCallExpr callExpr = expr.asMethodCallExpr();
            return callExpr.getNameAsString();
        } else if (expr.isObjectCreationExpr()) {
            // 构造函数
            ObjectCreationExpr callExpr = expr.asObjectCreationExpr();
            return callExpr.getType().getNameAsString();
        } else if (expr.isMethodReferenceExpr()) {
            // java8+，如 StringUtils::isEmpty
            MethodReferenceExpr callExpr = expr.asMethodReferenceExpr();
            return callExpr.getIdentifier();
        }
        return null;
    }

    /**
     * 从对象创建表达式中解析出参数类型字符串列表
     * 适用于该表达式包含外部依赖，不能 resolve() 来进一步解析方法入参的场景
     * 
     * @param callExpr 对象创建表达式
     * @return 参数类型字符串列表
     */
    public List<String> parseOutParamsTypeFromObjCreExprWithOuterDep(ObjectCreationExpr callExpr) {
        if (callExpr == null) {
            return null;
        }
        List<String> paramTypes = new ArrayList<>();
        // 获取参数类型
        callExpr.getArguments().forEach(arg -> {
            try {
                // 带有包名，却掉泛型
                paramTypes.add(parseOutTypeStrFromExprWithNoGeneric(arg));
            } catch (Exception ex) {
                // 参数是第三方依赖，此种情况。类型未知
                paramTypes.add(WordConstant.PARAM_TYPE_UNKNOWN);
            }
        });
        return paramTypes;
    }

    /**
     * 从变量声明表达式中解析出变量声明的类名
     * 包含包名，因为会通过 method 找 import，去掉泛型
     * 
     * @param containingMethod 所处方法
     * @param varDeclExpr      变量声明表达式
     * @param variableName     变量名称
     * 
     * @return 类名字符串
     */
    public String parseOutClassNameFromVarDeclExpr(MethodDeclaration containingMethod,
            VariableDeclarationExpr varDeclExpr, String variableName) {
        if (varDeclExpr == null || StringUtils.isBlank(variableName)) {
            return null;
        }
        Optional<VariableDeclarator> varDeclOpt = parseOutVarDeclFromVarDeclExpr(varDeclExpr, variableName);
        if (!varDeclOpt.isPresent()) {
            return null;
        }
        // 变量声明
        VariableDeclarator varDecl = varDeclOpt.get();
        // 从中获取声明类，不带有包名，但是去掉了泛型
        String variableType = getVariableParser().parseOutBaseTypeStrFromVarWithNoGeneric(varDecl);
        if (StringUtils.isBlank(variableType)) {
            return null;
        }
        if (variableType.contains(PathConstant.DOT)) {
            // variableType 这个声明类带有包名，直接返回
            return variableType;
        }
        // 从 method 方法所处的文件的 import 中尝试找包名
        String fullClassName = getMethodParser().findFullClassNameFromImports(containingMethod, variableType);
        if (StringUtils.isNotBlank(fullClassName) && fullClassName.contains(PathConstant.DOT)) {
            return fullClassName;
        }

        // 如果找不到包名，继续尝试找到包名
        // 声明类包可能是 method 本身对应的类或者内部的类的包，比如 User 类中的某方法中有 User user = new User();
        fullClassName = getMethodParser().findInnerClassName(containingMethod, variableType);
        if (StringUtils.isNotBlank(fullClassName)) {
            return fullClassName;
        }

        // 可能是同包下的类
        if (Objects.equal(getMethodParser().isImportStarExist(containingMethod, variableType), false)) {
            // 若不存在 import *，则说明来源于同包下
            return getNodeParser().parseOutPackStrFromCallDecl(containingMethod) + PathConstant.DOT + variableType;
        }

        // 其他情况不知道是来源于同包下，还是来源于 import *

        // 其他情况，只能返回简单类名
        return variableType;

    }

    /**
     * 从初始化器表达式中解析出初始化器的类名
     * 包含包名，去掉泛型
     * 
     * @param initializer 初始化器表达式
     * @return 类名字符串
     */
    public String parseOutRealClassNameFromInitializer(Expression initializer) {
        if (initializer == null) {
            return null;
        }
        // 构造方法
        if (initializer.isObjectCreationExpr()) {
            ObjectCreationExpr objCreationExpr = initializer.asObjectCreationExpr();
            try {
                return objCreationExpr.calculateResolvedType().describe();
            } catch (Exception ex) {
                return objCreationExpr.getType().asString();
            }

        } else if (initializer.isMethodCallExpr()) {
            // 调用方法
            MethodCallExpr methodCallExpr = initializer.asMethodCallExpr();
            try {
                return methodCallExpr.calculateResolvedType().describe();
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    /**
     * 从方法调用表达式中获取 scope 区域 expression
     * 
     * @param expr 方法调用表达式
     * @return scope 区域 expression
     */
    public Optional<Expression> parseOutScopeExprFromCallExpr(Expression expr) {
        if (expr == null) {
            return Optional.empty();
        }
        if (expr.isMethodCallExpr()) {
            // 普通方法调用
            return expr.asMethodCallExpr().getScope();
        } else if (expr.isObjectCreationExpr()) {
            // 构造函数
            return expr.asObjectCreationExpr().getScope();
        } else if (expr.isMethodReferenceExpr()) {
            // 方法引用
            return Optional.ofNullable(expr.asMethodReferenceExpr().getScope());
        }
        return Optional.empty();
    }

    /**
     * 从方法中，某个变量声明表达式中获取变量的初始化器，尝试解析出其对应的实现类
     * 带有包名，不带泛型
     * 
     * @param containingMethod  方法声明
     * @param varDeclExpr       变量声明表达式
     * @param scopeStr          变量名
     * @param declClassWithPack 声明类的全限定名，带有包名，不带泛型
     * @return 实现类的全限定名，带有包名，不带泛型
     */
    public String parseOutRealClassNameFromVarDeclExpr(VariableDeclarationExpr varDeclExpr, String scopeStr,
            String declClassWithPack) {
        // 带有包名，去掉泛型
        Optional<VariableDeclarator> varDeclOpt = parseOutVarDeclFromVarDeclExpr(varDeclExpr, scopeStr); // 该 scopeStr
                                                                                                         // 常量声明的地方
        if (!varDeclOpt.isPresent()) {
            return null; // 声明不存在，就无法判定实现类
        }
        VariableDeclarator varDecl = varDeclOpt.get();
        if (!getVariableParser().isVarHasInitializer(varDecl)) {
            // 如果没有初始化器，直接返回声明类
            return declClassWithPack;
        }
        Expression initializer = getVariableParser().parseOutInitializerFromVar(varDecl);
        if (initializer == null) {
            return declClassWithPack;
        }
        String realClassName = parseOutRealClassNameFromInitializer(initializer);
        if (StringUtils.isNotBlank(realClassName)) {
            return realClassName;
        }
        return null;
    }

    /**
     * 提取注解参数值
     * 
     * @param valueExpr 注解参数表达式
     * @return 注解参数值，支持字符串、整数、布尔值、枚举、数组、类类型等
     */
    public Object extractAnnotationValue(Expression valueExpr) {
        if (valueExpr == null) {
            return null;
        }
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
            // 类类型，如 java.lang.String
            return valueExpr.asClassExpr().getTypeAsString();
        } else {
            // 兜底，其他类型，转为字符串
            return valueExpr.toString();
        }
    }

}
