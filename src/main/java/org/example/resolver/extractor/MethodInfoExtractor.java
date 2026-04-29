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

import org.apache.commons.lang3.StringUtils;
import org.example.constant.PathConstant;
import org.example.constant.WordConstant;
import org.example.node.field.FuncCate;
import org.example.node.field.FuncInfo;
import org.example.resolver.model.MethodBelongs2Class;
import org.example.resolver.model.ParameterInfo;
import org.example.resolver.model.ReturnTypeInfo;
import org.example.resolver.parser.MethodDeclParser;
import org.example.util.ClassStrUtil;

import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.body.MethodDeclaration;

/**
 * 专门用于提取 MethodInfo 和 MethodCallInfo 中的信息
 */
public class MethodInfoExtractor implements InfoExtractor {

    private MethodDeclParser methodParser;

    private MethodDeclParser getMethodParser() {
        if (methodParser == null) {
            methodParser = new MethodDeclParser();
        }
        return methodParser;
    }

    /**
     * 提取方法信息
     * 
     * @param method          方法声明节点
     * @param targetClassName 目标类名
     * @param fromClass       方法来源的类
     * @param methodName      方法名
     * @param paramTypes      参数类型列表
     * @return 方法信息
     */
    public FuncInfo extract(MethodDeclaration method, String targetClassName, MethodBelongs2Class fromClass,
            String methodName, List<String> paramTypes) {

        FuncInfo funcInfo = new FuncInfo();

        // 设置方法分类
        FuncCate funcCate = extractFuncCate(method, targetClassName, fromClass, methodName, paramTypes);
        funcInfo.setFuncCate(funcCate);

        // 方法修饰符 - 从 Modifier 列表转换为 Keyword 列表
        List<Keyword> keywords = extractMethodModifiers(method, targetClassName, fromClass, methodName, paramTypes);
        funcInfo.setMethodModifiers(keywords);

        // 方法注解
        Map<String, Map<String, Object>> annotations = extractMethodAnnotations(method, targetClassName, fromClass,
                methodName, paramTypes);
        funcInfo.setAnnotations(annotations);

        // 方法名
        funcInfo.setFuncName(methodName);

        // 参数类型（简单类型名）和参数包名
        ParameterInfo paramInfo = extractParameterInfo(paramTypes);
        funcInfo.setFuncParams(paramInfo.getSimpleTypes());
        funcInfo.setFuncParamsPackageName(paramInfo.getPackageNames());

        // 返回值类型和返回值包名
        ReturnTypeInfo returnTypeInfo = extractReturnTypeInfo(method, targetClassName, fromClass, methodName,
                paramTypes);
        funcInfo.setFuncReturnType(returnTypeInfo.getSimpleType());
        funcInfo.setFuncReturnPackageName(returnTypeInfo.getPackageName());

        // 方法注释
        String comment = extractMethodComment(method, fromClass);
        funcInfo.setFuncComment(comment);

        return funcInfo;
    }

    /**
     * 提取参数信息（简单类型名和包名）
     *
     * @param paramTypes 参数类型列表
     * @return 参数信息
     */
    public static ParameterInfo extractParameterInfo(List<String> paramTypes) {
        List<String> simpleTypes = new ArrayList<>();
        List<String> packageNames = new ArrayList<>();

        if (paramTypes != null) {
            for (String paramType : paramTypes) {
                simpleTypes.add(ClassStrUtil.getSimpleClassName(paramType));
                packageNames.add(ClassStrUtil.getPackageName(paramType));
            }
        }

        return new ParameterInfo(simpleTypes, packageNames);
    }

    /**
     * 反射判断是否是 main 方法
     * 
     * @param method        方法声明节点
     * @param realClassName 全限定的类名，带有包的类名，比如 java.lang.String
     * @param methodName    方法名
     * @param paramTypes    参数类型列表，每个元素是参数类型的全限定名
     * @return 是否是 main 方法
     */
    private boolean isMainMethod(MethodDeclaration method, String realClassName, String methodName,
            List<String> paramTypes) {
        // 基础判定
        if (!methodName.equals(WordConstant.MAIN)
                || methodName.equals(ClassStrUtil.getSimpleClassName(realClassName))) {
            return false;
        }

        // 方法是项目中的方法
        if (method != null) {
            return method.getNameAsString().equals(WordConstant.MAIN) &&
                    method.getParameters().size() == 1 &&
                    method.getParameters().get(0).getType().toString().equals(WordConstant.STRING_ARR_PARAM) &&
                    method.getType().toString().equals(WordConstant.VOID_RETURN) &&
                    method.getModifiers().stream().anyMatch(m -> m.getKeyword() == Keyword.PUBLIC) &&
                    method.getModifiers().stream().anyMatch(m -> m.getKeyword() == Keyword.STATIC);
        } else {
            // 方法是 jdk/第三方依赖中的方法
            // 能力有限，依赖于提前加载到 classpath 中
            Method reflectionMethod = getMethodByReflection(realClassName, methodName, paramTypes);
            if (reflectionMethod == null) {
                return false;
            }
            int modifiers = reflectionMethod.getModifiers();
            return Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) &&
                    reflectionMethod.getReturnType().equals(void.class) &&
                    reflectionMethod.getParameterCount() == 1 &&
                    reflectionMethod.getParameterTypes()[0].equals(String[].class);
        }
    }

    /**
     * 提取返回值信息（简单类型名和包名）
     * 
     * @param method          方法声明
     * @param targetClassName 全限定的类名，带有包的类名，比如 java.lang.String
     * @param fromClass       方法来源的类，比如
     *                        SELF_CLASS，DECL_CLASS，ANCESTOR_CLASS，UNKNOWN
     * @param methodName      方法名
     * @param paramTypes      参数类型列表，每个元素是参数类型的全限定名，比如 java.lang.String，int
     * @return 返回值信息
     */
    private ReturnTypeInfo extractReturnTypeInfo(MethodDeclaration method, String targetClassName,
            MethodBelongs2Class fromClass, String methodName, List<String> paramTypes) {
        // 如果方法来源未知类
        if (fromClass == MethodBelongs2Class.ANCESTOR_CLASS || fromClass == MethodBelongs2Class.UNKNOWN) {
            if (method == null) {
                return new ReturnTypeInfo("", "");
            }
        }

        if (method != null) {
            try {
                // 能解析含有包名的返回类型
                String fullReturnType = method.getType().resolve().describe();
                String simpleReturnType = ClassStrUtil.getSimpleClassName(fullReturnType);
                String returnPackageName = ClassStrUtil.getPackageName(fullReturnType);
                return new ReturnTypeInfo(simpleReturnType, returnPackageName);
            } catch (Exception e) {
                String returnType = method.getType().asString();
                String simpleReturnType = ClassStrUtil.getSimpleClassName(returnType);
                String returnPackageName = ClassStrUtil.getPackageName(returnType);
                return new ReturnTypeInfo(simpleReturnType, returnPackageName);
            }
        } else {
            // jdk/第三方依赖，反射获取返回值类型
            // 能力有限，依赖于提前加载到 classpath 中
            Method reflectMethod = getMethodByReflection(targetClassName, methodName, paramTypes);
            if (reflectMethod == null) {
                return new ReturnTypeInfo("", "");
            }
            String fullReturnType = reflectMethod.getReturnType().getName();
            String simpleReturnType = ClassStrUtil.getSimpleClassName(fullReturnType);
            String returnPackageName = ClassStrUtil.getPackageName(fullReturnType);
            return new ReturnTypeInfo(simpleReturnType, returnPackageName);
        }

    }

    /**
     * 提取方法分类
     * 
     * @param method          方法声明
     * @param targetClassName 全限定的类名，带有包的类名，比如 java.lang.String
     * @param methodName      方法名
     * @param paramTypes      参数类型列表，每个元素是参数类型的全限定名
     * @return 方法分类
     */
    private FuncCate extractFuncCate(MethodDeclaration method, String targetClassName, MethodBelongs2Class fromClass,
            String methodName, List<String> paramTypes) {
        if (methodName != null && Character.isUpperCase(methodName.charAt(0))) {
            return FuncCate.CONSTRUCTOR;
        }
        // 如果方法无法确定来源类，返回 null
        if (fromClass == MethodBelongs2Class.ANCESTOR_CLASS || fromClass == MethodBelongs2Class.UNKNOWN) {
            if (method == null) {
                return null;
            }
        }
        if (isMainMethod(method, targetClassName, methodName, paramTypes)) {
            return FuncCate.MAIN;
        } else if (methodName.equals(ClassStrUtil.getSimpleClassName(targetClassName))) {
            return FuncCate.CONSTRUCTOR;
        } else {
            return FuncCate.DEFAULT;
        }
    }

    /**
     * 提取方法修饰符
     * 
     * @param method        方法声明
     * @param realClassName 全限定的类名，带有包的类名，比如 java.lang.String
     * @param fromClass     方法来源的类，比如 SELF_CLASS，DECL_CLASS，ANCESTOR_CLASS，UNKNOWN
     * @param methodName    方法名
     * @param paramTypes    参数类型列表，每个元素是参数类型的全限定名
     * @return 方法修饰符列表
     */
    private List<Keyword> extractMethodModifiers(MethodDeclaration method, String realClassName,
            MethodBelongs2Class fromClass, String methodName, List<String> paramTypes) {
        // 如果方法无法确定来源类，返回空列表
        if (fromClass == MethodBelongs2Class.ANCESTOR_CLASS || fromClass == MethodBelongs2Class.UNKNOWN) {
            if (method == null) {
                return new ArrayList<>();
            }
        }

        // 方法声明存在说明是项目中的方法
        if (method != null) {
            return getMethodParser().parseOutMethodModifiers(method);
        } else {
            // 方法是 jdk/第三方依赖中的方法，用反射
            // 能力有限，依赖于提前加载到 classpath 中
            int modifiers;
            String simpleClassStr = ClassStrUtil.getSimpleClassName(realClassName);
            // 防止内部类
            simpleClassStr = simpleClassStr.substring(simpleClassStr.lastIndexOf(PathConstant.HYP_DOLLAR) + 1);
            if (methodName.equals(simpleClassStr)) {
                // 构造器方法
                Constructor<?> constructor = getConstructorByReflection(realClassName, methodName, paramTypes);
                if (constructor == null) {
                    return new ArrayList<>();
                }
                modifiers = constructor.getModifiers();
            } else {
                // 普通方法
                Method reflectMethod = getMethodByReflection(realClassName, methodName, paramTypes);
                if (reflectMethod == null) {
                    return new ArrayList<>();
                }
                modifiers = reflectMethod.getModifiers();
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
            return keywords;
        }
    }

    /**
     * 提取方法注解
     * 
     * @param method        方法声明
     * @param realClassName 全限定的类名，带有包的类名，比如 java.lang.String
     * @param fromClass     方法来源的类，比如 SELF_CLASS，DECL_CLASS，ANCESTOR_CLASS，UNKNOWN
     * @param methodName    方法名
     * @param paramTypes    参数类型列表，每个元素是参数类型的全限定名
     * @return 方法注解列表
     */
    private Map<String, Map<String, Object>> extractMethodAnnotations(MethodDeclaration method, String realClassName,
            MethodBelongs2Class fromClass, String methodName, List<String> paramTypes) {
        // 如果方法无法确定来源类，返回 null
        if (fromClass == MethodBelongs2Class.ANCESTOR_CLASS || fromClass == MethodBelongs2Class.UNKNOWN) {
            if (method == null) {
                return new HashMap<>();
            }
        }

        // 项目中的方法
        if (method != null) {
            return getMethodParser().parseOutMethodAnnotations(method);
        } else {
            // jdk/第三方依赖中的方法
            // 能力有限，依赖于提前加载到 classpath 中
            AccessibleObject reflectMethod = null;
            String simpleClassStr = ClassStrUtil.getSimpleClassName(realClassName);
            // 防止内部类
            simpleClassStr = simpleClassStr.substring(simpleClassStr.lastIndexOf(PathConstant.HYP_DOLLAR) + 1);
            // 构造器方法
            if (methodName.equals(simpleClassStr)) {
                reflectMethod = getConstructorByReflection(realClassName, methodName, paramTypes);
                if (reflectMethod == null) {
                    return new HashMap<>();
                }
            } else {
                // 普通方法
                reflectMethod = getMethodByReflection(realClassName, methodName, paramTypes);
                if (reflectMethod == null) {
                    return new HashMap<>();
                }
            }
            Map<String, Map<String, Object>> annotations = new HashMap<>();
            try {
                for (Annotation ann : reflectMethod.getAnnotations()) {
                    Map<String, Object> params = new HashMap<>();
                    for (Method annoMethod : ann.annotationType().getDeclaredMethods()) {
                        params.put(annoMethod.getName(), annoMethod.invoke(ann));
                    }
                    annotations.put(ann.annotationType().getSimpleName(), params);
                }
            } catch (Exception e) {
                return new HashMap<>();
            }
            return annotations;
        }
    }

    /**
     * 提取方法注释
     * 
     * @param method 方法声明
     * @return 方法注释
     */
    private String extractMethodComment(MethodDeclaration method, MethodBelongs2Class fromClass) {
        // 如果方法来源未知类，返回空字符串
        if (fromClass == MethodBelongs2Class.ANCESTOR_CLASS || fromClass == MethodBelongs2Class.UNKNOWN) {
            if (method == null) {
                return "";
            }
        }

        if (method != null) {
            return getMethodParser().parseOutMethodComment(method);
        }

        // jdk/第三方依赖，直接返回空
        return "";
    }

    /**
     * 将字符串转换为对应的 Class 类型
     * 支持基本类型、引用类型、一维/多维数组类型
     * 
     * @param type 类型字符串，比如 "int"、"java.lang.String"、"int[]"、"int[][]"
     * @return 对应的 Class 类型
     */
    private Class<?> toClass(String type) {
        // 数组类型：递归剥离 [] 获取元素类型，再通过 Array.newInstance 构建数组 Class
        if (type.endsWith(PathConstant.ARR_BRACKETS)) {
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

    /**
     * 通过反射获取方法对象
     * 能力有限，依赖于提前加载到 classpath 中，否则反射也获取不到
     * 
     * @param className  全限定类名，比如 java.lang.String
     * @param methodName 方法名，比如 "substring"
     * @param paramTypes 参数类型列表，每个元素是参数类型的全限定名，比如 "int"、"java.lang.String"
     * @return 对应的方法对象
     */
    private Method getMethodByReflection(String className, String methodName, List<String> paramTypes) {
        if (StringUtils.isBlank(className) || StringUtils.isBlank(methodName)) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName(className);
            Class<?>[] paramClasses = new Class<?>[paramTypes.size()];
            for (int i = 0; i < paramTypes.size(); i++) {
                paramClasses[i] = toClass(paramTypes.get(i));
            }
            return clazz.getMethod(methodName, paramClasses);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 通过反射获取构造器对象
     * 能力有限，依赖于提前加载到 classpath 中，否则反射也获取不到
     * 
     * @param className  全限定类名，比如 java.lang.String
     * @param methodName 构造器方法名，比如 "String"
     * @param paramTypes 参数类型列表，每个元素是参数类型的全限定名，比如 "int"、"java.lang.String"
     * @return 对应的构造器对象
     */
    private Constructor<?> getConstructorByReflection(String className, String methodName, List<String> paramTypes) {
        if (StringUtils.isBlank(className) || StringUtils.isBlank(methodName)) {
            return null;
        }
        if (!methodName.equals(ClassStrUtil.getSimpleClassName(className))) {
            return null;
        }

        try {
            Class<?> clazz = Class.forName(className);
            Class<?>[] paramClasses = new Class<?>[paramTypes.size()];
            for (int i = 0; i < paramTypes.size(); i++) {
                paramClasses[i] = toClass(paramTypes.get(i));
            }
            return clazz.getConstructor(paramClasses);
        } catch (Exception e) {
            return null;
        }
    }
}