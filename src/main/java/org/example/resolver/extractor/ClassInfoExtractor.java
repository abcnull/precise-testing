package org.example.resolver.extractor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.node.field.ClassDeclaration;
import org.example.node.field.ClassInfo;
import org.example.node.field.ClassOrigin;
import org.example.resolver.parser.ClassParser;
import org.example.resolver.parser.TypeParser;
import org.example.resolver.util.StringUtil;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.body.TypeDeclaration;

/**
 * 专门用于提取 ClassInfo 和 ClassInfo 中的信息
 */
public class ClassInfoExtractor implements InfoExtractor {
    private final ClassParser classParser = new ClassParser();
    private final TypeParser typeParser = new TypeParser();

    /**
     * 提取类信息（包含反射逻辑）DONE
     *
     * @param cu            真实类的编译单元
     * @param className     全限定类
     * @param realClassName 全限定类名（多态场景下的真实类）
     * @return 类信息
     */
    public ClassInfo extract(CompilationUnit cu, String className, String realClassName) {
        ClassInfo classInfo = new ClassInfo();
        
        // 设置类名
        classInfo.setClassName(StringUtil.getSimpleClassName(className));
        classInfo.setRealClassName(StringUtil.getSimpleClassName(realClassName));

        // 设置类来源
        ClassOrigin classOrigin = extractClassOrigin(cu, realClassName);
        classInfo.setClassOrigin(classOrigin);

        // 设置类修饰符
        List<Keyword> classModifiers = extractClassModifiers(cu, className);
        classInfo.setClassModifiers(classModifiers);

        // 设置类注解
        Map<String, Map<String, Object>> annotations = extractClassAnnotations(cu, className);
        classInfo.setAnnotations(annotations);

        // 设置类注释
        String classComment = extractClassComments(cu, className);
        classInfo.setClassComment(classComment);

        // 设置类声明
        ClassDeclaration classDeclaration = extractClassDeclaration(cu, realClassName);
        classInfo.setClassDeclaration(classDeclaration);

        return classInfo;
    }

    /**
     * 从CompilationUnit和类名中提取类来源 DONE
     *
     * @param cu            真实类的编译单元
     * @param realClassName 全限定真实类名（多态场景下真实的类）
     * @return 类来源
     */
    private ClassOrigin extractClassOrigin(CompilationUnit cu, String realClassName) {
        if (cu != null) {
            return ClassOrigin.PROJECT; // 项目类
        }
        String realPackageName = StringUtil.getPackageName(realClassName);
        if (realPackageName.startsWith("java.") || realPackageName.startsWith("javax.")) {
            return ClassOrigin.JDK; // JDK类
        } else {
            return ClassOrigin.DEPENDENCY; // 依赖类
        }
    }

    /**
     * 从CompilationUnit中提取类修饰符DONE
     * 
     * @param cu        类的编译单元
     * @param className 全限定类名
     * @return 类修饰符列表
     */
    private List<Keyword> extractClassModifiers(CompilationUnit cu, String className) {
        // cu 是项目中的类
        if (cu != null) {
            TypeDeclaration<?> typeDecl = classParser.getFirstTypeDeclaration(cu);
            if (typeDecl != null) {
                return typeParser.parseOutClassModifiers(typeDecl);
            }
        } else {
            // cu 是 jdk 依赖或者第三方依赖
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
        }
        return new ArrayList<>();
    }

    /**
     * 从CompilationUnit中提取类注解DONE
     * 
     * @param cu        类的编译单元
     * @param className 全限定类名
     * @return 类注解映射
     */
    private Map<String, Map<String, Object>> extractClassAnnotations(CompilationUnit cu, String className) {
        // 从CompilationUnit中提取类注解（适用于项目类）
        if (cu != null) {
            TypeDeclaration<?> typeDecl = classParser.getFirstTypeDeclaration(cu);
            if (typeDecl != null) {
                return typeParser.parseOutClassAnnotations(typeDecl);
            }
        } else {
            Map<String, Map<String, Object>> annotations = new HashMap<>();
            try {
                Class<?> clazz = Class.forName(className);
                for (Annotation annotation : clazz.getAnnotations()) {
                    String annotationName = annotation.annotationType().getSimpleName();
                    Map<String, Object> params = new HashMap<>();
                    annotations.put(annotationName, params);
                }
            } catch (Exception e) {
            }
        }
        return new HashMap<>();
    }

    /**
     * 从CompilationUnit中提取类注释DONE
     * 
     * @param cu        类的编译单元
     * @param className 全限定类名
     * @return 类注释
     */
    private String extractClassComments(CompilationUnit cu, String className) {
        if (cu != null) {
            TypeDeclaration<?> typeDecl = classParser.getFirstTypeDeclaration(cu);
            if (typeDecl != null) {
                return typeParser.parseOutClassComment(typeDecl);
            }
        }
        return "";
    }

    /**
     * 从CompilationUnit中提取类声明DONE
     * 
     * @param cu        类的编译单元
     * @param className 全限定类名
     * @return 类声明
     */
    private ClassDeclaration extractClassDeclaration(CompilationUnit cu, String className) {
        if (cu != null) {
            TypeDeclaration<?> typeDecl = classParser.getFirstTypeDeclaration(cu);
            if (typeDecl != null) {
                return typeParser.parseOutClassDeclaration(typeDecl);
            }
        } else {
            try {
                // todo: 这里缺少一个 Record 类？
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
        }
        return null;
    }

}