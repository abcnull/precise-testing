package org.example.resolver.extractor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.example.constant.PathConstant;
import org.example.node.field.ClassDeclaration;
import org.example.node.field.ClassInfo;
import org.example.node.field.ClassOrigin;
import org.example.resolver.model.MethodBelongs2Class;
import org.example.resolver.parser.CompilationUnitParser;
import org.example.resolver.parser.TypeDeclParser;
import org.example.util.ClassStrUtil;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.body.TypeDeclaration;

/**
 * 专门用于提取 ClassInfo 和 ClassInfo 中的信息
 */
public class ClassInfoExtractor implements InfoExtractor {
    private CompilationUnitParser classParser;
    private TypeDeclParser typeParser;

    private CompilationUnitParser getClassParser() {
        if (classParser == null) {
            classParser = new CompilationUnitParser();
        }
        return classParser;
    }

    private TypeDeclParser getTypeParser() {
        if (typeParser == null) {
            typeParser = new TypeDeclParser();
        }
        return typeParser;
    }

    /**
     * 提取类信息（包含反射逻辑）
     *
     * @param targetCu      真实类的编译单元
     * @param fromClass     方法所属的类
     * @param className     全限定类
     * @param realClassName 全限定类名（多态场景下的真实类）
     * @return 类信息
     */
    public ClassInfo extract(CompilationUnit targetCu, MethodBelongs2Class fromClass,
            String className, String realClassName) {
        // 目标类名
        String targetClassName = realClassName;
        if (fromClass == MethodBelongs2Class.REAL_CLASS) {
            targetClassName = realClassName;
        } else if (fromClass == MethodBelongs2Class.DECL_CLASS) {
            targetClassName = className;
        }

        ClassInfo classInfo = new ClassInfo();

        // 设置类名
        classInfo.setDeclClassName(ClassStrUtil.getSimpleClassName(className));
        classInfo.setRealClassName(ClassStrUtil.getSimpleClassName(realClassName));

        // 设置类来源
        ClassOrigin classOrigin = extractClassOrigin(targetCu, fromClass, targetClassName);
        classInfo.setClassOrigin(classOrigin);

        // 设置类修饰符
        List<Keyword> classModifiers = extractClassModifiers(targetCu, fromClass, targetClassName);
        classInfo.setClassModifiers(classModifiers);

        // 设置类注解
        Map<String, Map<String, Object>> annotations = extractClassAnnotations(targetCu, fromClass, targetClassName);
        classInfo.setAnnotations(annotations);

        // 设置类注释
        String classComment = extractClassComments(targetCu, fromClass, targetClassName);
        classInfo.setClassComment(classComment);

        // 设置类声明
        ClassDeclaration classDeclaration = extractClassDeclaration(targetCu, fromClass, targetClassName);
        classInfo.setClassDeclaration(classDeclaration);

        // 方法所属的类
        classInfo.setFromClass(fromClass);

        return classInfo;
    }

    /**
     * 从CompilationUnit和类名中提取类来源
     *
     * @param cu            真实类的编译单元
     * @param fromClass     方法所属的类
     * @param realClassName 全限定真实类名（多态场景下真实的类）
     * @return 类来源
     */
    private ClassOrigin extractClassOrigin(CompilationUnit cu, MethodBelongs2Class fromClass, String realClassName) {
        if (fromClass == MethodBelongs2Class.ANCESTOR_CLASS || fromClass == MethodBelongs2Class.UNKNOWN) {
            return null;
        }

        if (cu != null) {
            return ClassOrigin.PROJECT; // 项目类
        }
        String realPackageName = ClassStrUtil.getPackageName(realClassName);
        if (realPackageName.startsWith(PathConstant.JAVA_DOT_PREFIX)
                || realPackageName.startsWith(PathConstant.JAVAX_DOT_PREFIX)) {
            return ClassOrigin.JDK; // JDK类
        } else {
            return ClassOrigin.DEPENDENCY; // 依赖类
        }
    }

    /**
     * 从 CompilationUnit 中提取类修饰符
     * 
     * @param cu        类的编译单元
     * @param fromClass 方法所属的类
     * @param className 全限定类名
     * @return 类修饰符列表
     */
    public List<Keyword> extractClassModifiers(CompilationUnit cu, MethodBelongs2Class fromClass, String className) {
        if (fromClass == MethodBelongs2Class.ANCESTOR_CLASS || fromClass == MethodBelongs2Class.UNKNOWN) {
            return new ArrayList<>();
        }

        // cu 是项目中的类
        if (cu != null) {
            List<TypeDeclaration> typeDeclarations = getClassParser().getAllTypeDeclarations(cu, className);
            if (CollectionUtils.isNotEmpty(typeDeclarations)
                    && typeDeclarations.get(0) != null) {
                return getTypeParser().parseOutClassModifiers(typeDeclarations.get(0));
            }
        } else {
            // cu 是 jdk 依赖或者第三方依赖
            // 能力有限，依赖于提前加载到 classpath 中
            return extractClassModifiersByReflection(className);
        }
        return new ArrayList<>();
    }

    /**
     * 通过反射提取类修饰符
     * 能力有限，依赖于提前加载到 classpath 中，否则反射也获取不到
     * 
     * @param className 全限定类名
     * @return 类修饰符列表
     */
    private List<Keyword> extractClassModifiersByReflection(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            int classModifiers = clazz.getModifiers();
            List<Keyword> classModifierList = new ArrayList<>();
            if (Modifier.isPublic(classModifiers)) {
                classModifierList.add(Keyword.PUBLIC);
            }
            if (Modifier.isPrivate(classModifiers)) {
                classModifierList.add(Keyword.PRIVATE);
            }
            if (Modifier.isProtected(classModifiers)) {
                classModifierList.add(Keyword.PROTECTED);
            }
            if (Modifier.isStatic(classModifiers)) {
                classModifierList.add(Keyword.STATIC);
            }
            if (Modifier.isFinal(classModifiers)) {
                classModifierList.add(Keyword.FINAL);
            }
            if (Modifier.isSynchronized(classModifiers)) {
                classModifierList.add(Keyword.SYNCHRONIZED);
            }
            if (Modifier.isVolatile(classModifiers)) {
                classModifierList.add(Keyword.VOLATILE);
            }
            if (Modifier.isTransient(classModifiers)) {
                classModifierList.add(Keyword.TRANSIENT);
            }
            if (Modifier.isNative(classModifiers)) {
                classModifierList.add(Keyword.NATIVE);
            }
            if (Modifier.isStrict(classModifiers)) {
                classModifierList.add(Keyword.STRICTFP);
            }
            return classModifierList;
        } catch (Exception e) {
        }
        return new ArrayList<>();
    }

    /**
     * 从 CompilationUnit 中提取类注解
     * 
     * @param cu        类的编译单元
     * @param fromClass 方法所属的类
     * @param className 全限定类名
     * @return 类注解映射
     */
    private Map<String, Map<String, Object>> extractClassAnnotations(CompilationUnit cu, MethodBelongs2Class fromClass,
            String className) {
        if (fromClass == MethodBelongs2Class.ANCESTOR_CLASS || fromClass == MethodBelongs2Class.UNKNOWN) {
            return new HashMap<>();
        }

        // 从CompilationUnit中提取类注解（适用于项目类）
        if (cu != null) {
            List<TypeDeclaration> typeDeclarations = getClassParser().getAllTypeDeclarations(cu, className);
            if (CollectionUtils.isNotEmpty(typeDeclarations)
                    && typeDeclarations.get(0) != null) {
                return getTypeParser().parseOutClassAnnotations(typeDeclarations.get(0));
            }
        } else {
            // cu 是 jdk 依赖或者第三方依赖
            // 能力有限，依赖于提前加载到 classpath 中
            return extractClassAnnotationsByReflection(className);
        }
        return new HashMap<>();
    }

    /**
     * 通过反射提取类注解
     * 能力有限，依赖于提前加载到 classpath 中，否则反射也获取不到
     * 
     * @param className 全限定类名
     * @return 类注解映射
     */
    private Map<String, Map<String, Object>> extractClassAnnotationsByReflection(String className) {
        if (StringUtils.isBlank(className)) {
            return new HashMap<>();
        }
        Map<String, Map<String, Object>> annotations = new HashMap<>();
        try {
            Class<?> clazz = Class.forName(className);
            for (Annotation annotation : clazz.getAnnotations()) {
                String annotationName = annotation.annotationType().getSimpleName();
                Map<String, Object> params = new HashMap<>(); // kv
                // 获取注解的所有方法（对应注解的参数）
                Method[] methods = annotation.annotationType().getDeclaredMethods();
                for (Method method : methods) {
                    // 跳过默认方法
                    if (!method.isDefault()) {
                        try {
                            // 调用方法获取参数值
                            Object value = method.invoke(annotation);
                            params.put(method.getName(), value);
                        } catch (Exception e) {
                            // 忽略异常，继续处理下一个参数
                        }
                    }
                }
                annotations.put(annotationName, params);
            }
        } catch (Exception e) {
            // 忽略异常，返回空映射
        }
        return annotations;
    }

    /**
     * 从 CompilationUnit 中提取类注释
     * 
     * @param cu        类的编译单元
     * @param fromClass 方法所属的类
     * @param className 全限定类名
     * @return 类注释
     */
    private String extractClassComments(CompilationUnit cu, MethodBelongs2Class fromClass, String className) {
        if (fromClass == MethodBelongs2Class.ANCESTOR_CLASS || fromClass == MethodBelongs2Class.UNKNOWN) {
            return "";
        }
        if (cu != null) {
            List<TypeDeclaration> typeDeclarations = getClassParser().getAllTypeDeclarations(cu, className);
            if (CollectionUtils.isNotEmpty(typeDeclarations)
                    && typeDeclarations.get(0) != null) {
                return getTypeParser().parseOutClassComment(typeDeclarations.get(0));
            }
        }
        return "";
    }

    /**
     * 从 CompilationUnit 中提取类声明
     * 
     * @param cu        类的编译单元
     * @param fromClass 方法所属的类
     * @param className 全限定类名
     * @return 类声明
     */
    public ClassDeclaration extractClassDeclaration(CompilationUnit cu, MethodBelongs2Class fromClass,
            String className) {
        if (fromClass == MethodBelongs2Class.ANCESTOR_CLASS || fromClass == MethodBelongs2Class.UNKNOWN) {
            return null;
        }
        if (cu != null) {
            List<TypeDeclaration> typeDeclarations = getClassParser().getAllTypeDeclarations(cu, className);
            if (CollectionUtils.isNotEmpty(typeDeclarations)
                    && typeDeclarations.get(0) != null) {
                return getTypeParser().parseOutClassDeclaration(typeDeclarations.get(0));
            }
        } else {
            // cu 是 jdk 依赖或者第三方依赖
            // 能力有限，依赖于提前加载到 classpath 中
            return extractClassDeclarationByReflection(className);
        }
        return null;
    }

    /**
     * 通过反射提取类声明
     * 能力有限，依赖于提前加载到 classpath 中，否则反射也获取不到
     * 
     * @param className 全限定类名
     * @return 类声明
     */
    private ClassDeclaration extractClassDeclarationByReflection(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            if (clazz.isInterface()) {
                return ClassDeclaration.INTERFACE;
            } else if (clazz.isEnum()) {
                return ClassDeclaration.ENUM;
            } else if (clazz.isAnnotation()) {
                return ClassDeclaration.ANNOTATION;
            } else {
                return ClassDeclaration.CLASS;
            }
        } catch (Exception e) {
        }
        return null;
    }

}