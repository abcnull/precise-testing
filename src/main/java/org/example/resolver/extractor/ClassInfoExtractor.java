package org.example.resolver.extractor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.example.node.field.ClassDeclaration;
import org.example.node.field.ClassInfo;
import org.example.node.field.ClassOrigin;
import org.example.resolver.util.StringUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ClassInfoExtractor implements InfoExtractor<ClassInfo, Object[]> {

    private final AnnotationExtractor annotationExtractor = new AnnotationExtractor();

    @Override
    public ClassInfo extract(Object[] source) {
        CompilationUnit cu = (CompilationUnit) source[0];
        String className = (String) source[1];
        String realClassName = (String) source[2];

        ClassInfo classInfo = new ClassInfo();
        String simpleClassName = StringUtil.getSimpleClassName(className);
        classInfo.setClassName(simpleClassName);
        classInfo.setRealClassName(StringUtil.getSimpleClassName(realClassName));

        // 设置类来源
        if (cu != null) {
            classInfo.setClassOrigin(ClassOrigin.PROJECT);
        } else {
            String packageName = StringUtil.getPackageName(className);
            if (packageName.startsWith("java.") || packageName.startsWith("javax.")) {
                classInfo.setClassOrigin(ClassOrigin.JDK);
            } else {
                classInfo.setClassOrigin(ClassOrigin.DEPENDENCY);
            }
        }

        // 查找类声明
        if (cu != null) {
            for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
                // 提取类注解
                classInfo.setAnnotations(annotationExtractor.extract(typeDecl));

                // 提取类注释
                classInfo.setClassComment(extractClassComment(typeDecl));

                // 提取类修饰符
                List<Keyword> classModifiers = typeDecl.getModifiers().stream()
                        .map(modifier -> modifier.getKeyword())
                        .collect(Collectors.toList());
                classInfo.setClassModifiers(classModifiers);

                // 提取类声明类型
                if (typeDecl instanceof ClassOrInterfaceDeclaration) {
                    ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) typeDecl;
                    if (classDecl.isInterface()) {
                        classInfo.setClassDeclaration(ClassDeclaration.INTERFACE);
                    } else {
                        classInfo.setClassDeclaration(ClassDeclaration.CLASS);
                    }
                } else if (typeDecl instanceof EnumDeclaration) {
                    classInfo.setClassDeclaration(ClassDeclaration.ENUM);
                } else if (typeDecl instanceof AnnotationDeclaration) {
                    classInfo.setClassDeclaration(ClassDeclaration.ANNOTATION);
                } else if (typeDecl instanceof RecordDeclaration) {
                    classInfo.setClassDeclaration(ClassDeclaration.RECORD);
                } else {
                    classInfo.setClassDeclaration(ClassDeclaration.CLASS);
                }

                break;
            }
        } else {
            classInfo.setAnnotations(new HashMap<>());
            classInfo.setClassComment("");
            classInfo.setClassModifiers(new ArrayList<>());
            classInfo.setClassDeclaration(ClassDeclaration.CLASS);
        }

        return classInfo;
    }

    /**
     * 提取类注释
     */
    private String extractClassComment(TypeDeclaration<?> typeDecl) {
        Optional<String> commentOpt = typeDecl.getJavadocComment().map(javadoc -> javadoc.getContent());
        if (commentOpt.isPresent()) {
            return commentOpt.get();
        }

        return typeDecl.getComment().map(comment -> comment.getContent()).orElse("");
    }

    /**
     * 提取类信息（包含反射逻辑）
     */
    public ClassInfo extractWithReflection(String className, String realClassName, CompilationUnit cu) {
        ClassInfo classInfo = new ClassInfo();
        classInfo.setClassName(StringUtil.getSimpleClassName(className));
        classInfo.setRealClassName(StringUtil.getSimpleClassName(realClassName));

        // 设置类来源
        String packageName = StringUtil.getPackageName(className);
        if (cu != null) {
            classInfo.setClassOrigin(ClassOrigin.PROJECT);
        } else if (packageName.startsWith("java.") || packageName.startsWith("javax.")) {
            classInfo.setClassOrigin(ClassOrigin.JDK);
        } else {
            classInfo.setClassOrigin(ClassOrigin.DEPENDENCY);
        }

        // 尝试通过反射获取类信息
        try {
            Class<?> clazz = Class.forName(className);
            // 获取类修饰符
            int classModifiers = clazz.getModifiers();
            List<Keyword> classModifierList = new ArrayList<>();
            if (java.lang.reflect.Modifier.isPublic(classModifiers)) {
                classModifierList.add(Keyword.PUBLIC);
            }
            if (java.lang.reflect.Modifier.isFinal(classModifiers)) {
                classModifierList.add(Keyword.FINAL);
            }
            if (java.lang.reflect.Modifier.isAbstract(classModifiers)) {
                classModifierList.add(Keyword.ABSTRACT);
            }
            if (java.lang.reflect.Modifier.isStatic(classModifiers)) {
                classModifierList.add(Keyword.STATIC);
            }
            classInfo.setClassModifiers(classModifierList);
        } catch (Exception e) {
            classInfo.setClassModifiers(new ArrayList<>());
        }

        // 尝试从CompilationUnit中提取类信息
        if (cu != null) {
            for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
                // 提取类注解
                classInfo.setAnnotations(annotationExtractor.extract(typeDecl));

                // 提取类注释
                Optional<String> classCommentOpt = typeDecl.getJavadocComment().map(javadoc -> javadoc.getContent());
                if (classCommentOpt.isPresent()) {
                    classInfo.setClassComment(classCommentOpt.get());
                } else {
                    classInfo.setClassComment(typeDecl.getComment().map(comment -> comment.getContent()).orElse(""));
                }

                // 提取类声明类型
                if (typeDecl instanceof ClassOrInterfaceDeclaration) {
                    ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) typeDecl;
                    if (classDecl.isInterface()) {
                        classInfo.setClassDeclaration(ClassDeclaration.INTERFACE);
                    } else {
                        classInfo.setClassDeclaration(ClassDeclaration.CLASS);
                    }
                } else if (typeDecl instanceof EnumDeclaration) {
                    classInfo.setClassDeclaration(ClassDeclaration.ENUM);
                } else if (typeDecl instanceof AnnotationDeclaration) {
                    classInfo.setClassDeclaration(ClassDeclaration.ANNOTATION);
                } else if (typeDecl instanceof RecordDeclaration) {
                    classInfo.setClassDeclaration(ClassDeclaration.RECORD);
                } else {
                    classInfo.setClassDeclaration(ClassDeclaration.CLASS);
                }

                break;
            }
        } else {
            classInfo.setAnnotations(new HashMap<>());
            classInfo.setClassComment("");
            classInfo.setClassDeclaration(ClassDeclaration.CLASS);
        }

        return classInfo;
    }
}