package org.example.resolver.extractor;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.node.field.FuncCate;
import org.example.node.field.FuncInfo;
import org.example.resolver.model.ParameterInfo;
import org.example.resolver.model.ReturnTypeInfo;
import org.example.resolver.parser.MethodParser;
import org.example.resolver.util.StringUtil;

import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.google.common.base.Objects;

/**
 * 专门用于提取 MethodInfo 和 MethodCallInfo 中的信息
 */
public class MethodInfoExtractor implements InfoExtractor {

    private final MethodParser methodParser = new MethodParser();

    /**
     * 提取方法信息 DONE
     * 
     * @param method     方法声明节点
     * @param methodName 方法名
     * @param paramTypes 参数类型列表
     * @return 方法信息
     */
    public FuncInfo extract(MethodDeclaration method, String realClassName, String methodName,
            List<String> paramTypes) {

        FuncInfo funcInfo = new FuncInfo();

        // 设置方法分类
        FuncCate funcCate = extractFuncCate(method, realClassName, methodName, paramTypes);
        funcInfo.setFuncCate(funcCate);

        // 方法修饰符 - 从 Modifier 列表转换为 Keyword 列表
        List<Keyword> keywords = extractMethodModifiers(method, realClassName, methodName, paramTypes);
        funcInfo.setMethodModifiers(keywords);

        // 方法注解
        Map<String, Map<String, Object>> annotations = extractMethodAnnotations(method, realClassName, methodName,
                paramTypes);
        funcInfo.setAnnotations(annotations);

        // 方法名
        funcInfo.setFuncName(methodName);

        // 参数类型（简单类型名）和参数包名
        ParameterInfo paramInfo = extractParameterInfo(paramTypes);
        funcInfo.setFuncParams(paramInfo.getSimpleTypes());
        funcInfo.setFuncParamsPackageName(paramInfo.getPackageNames());

        // 返回值类型和返回值包名
        ReturnTypeInfo returnTypeInfo = extractReturnTypeInfo(method, realClassName, methodName, paramTypes);
        funcInfo.setFuncReturnType(returnTypeInfo.getSimpleType());
        funcInfo.setFuncReturnPackageName(returnTypeInfo.getPackageName());

        // 方法注释
        String comment = extractMethodComment(method);
        funcInfo.setFuncComment(comment);

        return funcInfo;
    }

    /**
     * 提取参数信息（简单类型名和包名）DONE
     *
     * @param paramTypes 参数类型列表
     * @return 参数信息
     */
    public static ParameterInfo extractParameterInfo(List<String> paramTypes) {
        List<String> simpleTypes = new ArrayList<>();
        List<String> packageNames = new ArrayList<>();

        if (paramTypes != null) {
            for (String paramType : paramTypes) {
                simpleTypes.add(StringUtil.getSimpleClassName(paramType));
                packageNames.add(StringUtil.getPackageName(paramType));
            }
        }

        return new ParameterInfo(simpleTypes, packageNames);
    }

    /**
     * 反射判断是否是 main 方法 DONE
     * 
     * @param realClassName 全限定的类名，带有包的类名，比如 java.lang.String
     * @param methodName    方法名
     * @param paramTypes    参数类型列表，每个元素是参数类型的全限定名
     * @return 是否是 main 方法
     */
    private boolean isMainMethod(MethodDeclaration method, String realClassName, String methodName,
            List<String> paramTypes) {
        // 基础判定
        if (!methodName.equals("main") || methodName.equals(StringUtil.getSimpleClassName(methodName))) {
            return false;
        }

        // 方法是项目中的方法
        if (method != null) {
            return method.getNameAsString().equals("main") &&
                    method.getParameters().size() == 1 &&
                    method.getParameters().get(0).getType().toString().equals("String[]") &&
                    method.getType().toString().equals("void") &&
                    method.getModifiers().stream().anyMatch(m -> m.getKeyword() == Keyword.PUBLIC) &&
                    method.getModifiers().stream().anyMatch(m -> m.getKeyword() == Keyword.STATIC);
        } else {
            // 方法是 jdk/第三方依赖中的方法
            try {
                Class<?> clazz = Class.forName(realClassName);
                Class<?>[] paramClasses = new Class<?>[paramTypes.size()];
                for (int i = 0; i < paramTypes.size(); i++) {
                    paramClasses[i] = toClass(paramTypes.get(i));
                }
                Method reflectionMethod = clazz.getMethod(methodName, paramClasses);
                int modifiers = reflectionMethod.getModifiers();
                return Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) &&
                        reflectionMethod.getReturnType().equals(void.class) &&
                        reflectionMethod.getParameterCount() == 1 &&
                        reflectionMethod.getParameterTypes()[0].equals(String[].class);
            } catch (Exception e) {
                return false;
            }
        }
    }

    /**
     * 提取返回值信息（简单类型名和包名）DONE
     * 
     * @param method        方法声明
     * @param realClassName 全限定的类名，带有包的类名，比如 java.lang.String
     * @param methodName    方法名
     * @param paramTypes    参数类型列表，每个元素是参数类型的全限定名，比如 java.lang.String，int
     * @return 返回值信息
     */
    private ReturnTypeInfo extractReturnTypeInfo(MethodDeclaration method, String realClassName, String methodName,
            List<String> paramTypes) {
        if (method != null) {
            try {
                // 能解析含有包名的返回类型
                String fullReturnType = method.getType().resolve().describe();
                String simpleReturnType = StringUtil.getSimpleClassName(fullReturnType);
                String returnPackageName = StringUtil.getPackageName(fullReturnType);
                return new ReturnTypeInfo(simpleReturnType, returnPackageName);
            } catch (Exception e) {
                String returnType = method.getType().asString();
                String simpleReturnType = StringUtil.getSimpleClassName(returnType);
                String returnPackageName = StringUtil.getPackageName(returnType);
                return new ReturnTypeInfo(simpleReturnType, returnPackageName);
            }
        } else {
            try {
                Class<?> clazz = Class.forName(realClassName);
                Class<?>[] paramClasses = new Class<?>[paramTypes.size()];
                for (int i = 0; i < paramTypes.size(); i++) {
                    paramClasses[i] = toClass(paramTypes.get(i));
                }
                Method m = clazz.getMethod(methodName, paramClasses);
                String fullReturnType = m.getReturnType().getName();
                String simpleReturnType = StringUtil.getSimpleClassName(fullReturnType);
                String returnPackageName = StringUtil.getPackageName(fullReturnType);
                return new ReturnTypeInfo(simpleReturnType, returnPackageName);
            } catch (Exception e) {
                return new ReturnTypeInfo("", "");
            }
        }

    }

    /**
     * 提取方法分类 DONE
     * 
     * @param method        方法声明
     * @param realClassName 全限定的类名，带有包的类名，比如 java.lang.String
     * @param methodName    方法名
     * @param paramTypes    参数类型列表，每个元素是参数类型的全限定名
     * @return 方法分类
     */
    private FuncCate extractFuncCate(MethodDeclaration method, String realClassName, String methodName,
            List<String> paramTypes) {
        if (isMainMethod(method, realClassName, methodName, paramTypes)) {
            return FuncCate.MAIN;
        } else if (methodName.equals(StringUtil.getSimpleClassName(realClassName))) {
            return FuncCate.CONSTRUCTOR;
        } else {
            return FuncCate.DEFAULT;
        }
    }

    /**
     * 提取方法修饰符 DONE
     * 
     * @param method        方法声明
     * @param realClassName 全限定的类名，带有包的类名，比如 java.lang.String
     * @param methodName    方法名
     * @param paramTypes    参数类型列表，每个元素是参数类型的全限定名
     * @return 方法修饰符列表
     */
    private List<Keyword> extractMethodModifiers(MethodDeclaration method, String realClassName, String methodName,
            List<String> paramTypes) {
        // 方法声明存在说明是项目中的方法
        if (method != null) {
            return methodParser.parseOutMethodModifiers(method);
        } else {
            // 方法是 jdk/第三方依赖中的方法，用反射
            try {
                Class<?> clazz = Class.forName(realClassName);
                Class<?>[] paramClasses = new Class<?>[paramTypes.size()];
                for (int i = 0; i < paramTypes.size(); i++) {
                    paramClasses[i] = toClass(paramTypes.get(i));
                }
                int modifiers;
                // 构造器方法
                if (methodName.equals(StringUtil.getSimpleClassName(realClassName))) {
                    modifiers = clazz.getConstructor().getModifiers();
                } else {
                    // 普通方法
                    modifiers = clazz.getMethod(methodName, paramClasses).getModifiers();
                }
                List<Keyword> keywords = new ArrayList<>();
                if (Modifier.isPublic(modifiers))
                    keywords.add(Keyword.PUBLIC);
                if (Modifier.isProtected(modifiers))
                    keywords.add(Keyword.PROTECTED);
                if (Modifier.isPrivate(modifiers))
                    keywords.add(Keyword.PRIVATE);
                if (Modifier.isStatic(modifiers))
                    keywords.add(Keyword.STATIC);
                if (Modifier.isFinal(modifiers))
                    keywords.add(Keyword.FINAL);
                if (Modifier.isAbstract(modifiers))
                    keywords.add(Keyword.ABSTRACT);
                if (Modifier.isSynchronized(modifiers))
                    keywords.add(Keyword.SYNCHRONIZED);
                if (Modifier.isNative(modifiers))
                    keywords.add(Keyword.NATIVE);
                if (Modifier.isStrict(modifiers))
                    keywords.add(Keyword.STRICTFP);
                if (Modifier.isTransient(modifiers))
                    keywords.add(Keyword.TRANSIENT);
                if (Modifier.isVolatile(modifiers))
                    keywords.add(Keyword.VOLATILE);
                return keywords;
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }
    }

    /**
     * 提取方法注解 DONE
     * 
     * @param method        方法声明
     * @param realClassName 全限定的类名，带有包的类名，比如 java.lang.String
     * @param methodName    方法名
     * @param paramTypes    参数类型列表，每个元素是参数类型的全限定名
     * @return 方法注解列表
     */
    private Map<String, Map<String, Object>> extractMethodAnnotations(MethodDeclaration method, String realClassName,
            String methodName, List<String> paramTypes) {
        // 项目中的方法
        if (method != null) {
            return methodParser.parseOutMethodAnnotations(method);
        } else {
            // jdk/第三方依赖中的方法
            try {
                Class<?> clazz = Class.forName(realClassName);
                Class<?>[] paramClasses = new Class<?>[paramTypes.size()];
                for (int i = 0; i < paramTypes.size(); i++) {
                    paramClasses[i] = toClass(paramTypes.get(i));
                }
                AccessibleObject m = null;
                // 构造器方法
                if (methodName.equals(StringUtil.getSimpleClassName(realClassName))) {
                    m = clazz.getConstructor(paramClasses);
                } else {
                    // 普通方法
                    m = clazz.getMethod(methodName, paramClasses);
                }
                Map<String, Map<String, Object>> annotations = new HashMap<>();
                for (Annotation ann : m.getAnnotations()) {
                    Map<String, Object> params = new HashMap<>();
                    for (Method annoMethod : ann.annotationType().getDeclaredMethods()) {
                        params.put(annoMethod.getName(), annoMethod.invoke(ann));
                    }
                    annotations.put(ann.annotationType().getSimpleName(), params);
                }
                return annotations;
            } catch (Exception e) {
                return new HashMap<>();
            }
        }
    }

    /**
     * 提取方法注释 DONE
     * 
     * @param method 方法声明
     * @return 方法注释
     */
    private String extractMethodComment(MethodDeclaration method) {
        if (method != null) {
            return methodParser.parseOutMethodComment(method);
        }
        return "";
    }

    /**
     * 将字符串转换为对应的 Class 类型 DONE
     * 支持基本类型、引用类型、一维/多维数组类型
     * 
     * @param type 类型字符串，比如 "int"、"java.lang.String"、"int[]"、"int[][]"
     * @return 对应的 Class 类型
     */
    private Class<?> toClass(String type) {
        // 数组类型：递归剥离 [] 获取元素类型，再通过 Array.newInstance 构建数组 Class
        if (type.endsWith("[]")) {
            Class<?> componentType = toClass(type.substring(0, type.length() - 2));
            return Array.newInstance(componentType, 0).getClass();
        }
        switch (type) {
            case "int":
                return int.class;
            case "long":
                return long.class;
            case "short":
                return short.class;
            case "byte":
                return byte.class;
            case "char":
                return char.class;
            case "boolean":
                return boolean.class;
            case "float":
                return float.class;
            case "double":
                return double.class;
            case "void":
                return void.class;
            default:
                try {
                    return Class.forName(type);
                } catch (ClassNotFoundException e) {
                    return Object.class;
                }
        }
    }
}