package org.example.resolver.extractor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.JavadocComment;
import org.example.node.field.FuncCate;
import org.example.node.field.FuncInfo;
import org.example.resolver.model.ParameterInfo;
import org.example.resolver.util.StringUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class MethodReflectionExtractor {

    private final AnnotationExtractor annotationExtractor = new AnnotationExtractor();

    /**
     * 提取方法信息（包含反射逻辑）
     */
    public FuncInfo extractMethodInfo(String className, String methodName, List<String> paramTypes, CompilationUnit cu) {
        FuncInfo info = new FuncInfo();
        
        // 设置方法分类
        if (methodName.equals(StringUtil.getSimpleClassName(className))) {
            // 构造方法：方法名与类名相同
            info.setFuncCate(FuncCate.CONSTRUCTOR);
        } else if (methodName.equals("main")) {
            // 判断是否为真正的main方法
            boolean isMain = false;
            try {
                // 尝试通过反射获取方法信息
                Class<?> clazz = Class.forName(className);
                Method method = findMethodByReflection(clazz, methodName, paramTypes);
                if (method != null) {
                    // 检查方法签名是否符合main方法的要求
                    isMain = method.getParameterCount() == 1 &&
                            method.getParameterTypes()[0].isArray() &&
                            method.getParameterTypes()[0].getComponentType().equals(String.class) &&
                            method.getReturnType().equals(void.class) &&
                            Modifier.isPublic(method.getModifiers()) &&
                            Modifier.isStatic(method.getModifiers());
                }
            } catch (Exception e) {
                // 反射失败时，使用默认值
            }
            if (isMain) {
                info.setFuncCate(FuncCate.MAIN);
            } else {
                info.setFuncCate(FuncCate.DEFAULT);
            }
        } else {
            // 普通方法
            info.setFuncCate(FuncCate.DEFAULT);
        }
        
        info.setFuncName(methodName);

        // 尝试从CompilationUnit中提取方法信息
        if (cu != null) {
            for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
                for (BodyDeclaration<?> bodyDecl : typeDecl.getMembers()) {
                    if (bodyDecl instanceof MethodDeclaration) {
                        MethodDeclaration methodDecl = (MethodDeclaration) bodyDecl;
                        if (methodDecl.getNameAsString().equals(methodName)) {
                            // 提取方法注解
                            info.setAnnotations(annotationExtractor.extract(methodDecl));

                            // 提取方法修饰符
                            List<Keyword> methodModifiers = methodDecl.getModifiers().stream()
                                    .map(modifier -> modifier.getKeyword())
                                    .collect(Collectors.toList());
                            info.setMethodModifiers(methodModifiers);

                            // 提取方法注释
                            Optional<String> methodCommentOpt = methodDecl.getJavadocComment().map(JavadocComment::getContent);
                            if (methodCommentOpt.isPresent()) {
                                info.setFuncComment(methodCommentOpt.get());
                            } else {
                                info.setFuncComment(methodDecl.getComment().map(Comment::getContent).orElse(""));
                            }

                            break;
                        }
                    } else if (bodyDecl instanceof ConstructorDeclaration) {
                        ConstructorDeclaration constructorDecl = (ConstructorDeclaration) bodyDecl;
                        if (constructorDecl.getNameAsString().equals(methodName)) {
                            // 提取构造器注解
                            info.setAnnotations(annotationExtractor.extract(constructorDecl));

                            // 提取构造器修饰符
                            List<Keyword> constructorModifiers = constructorDecl.getModifiers().stream()
                                    .map(modifier -> modifier.getKeyword())
                                    .collect(Collectors.toList());
                            info.setMethodModifiers(constructorModifiers);

                            // 提取构造器注释
                            Optional<String> constructorCommentOpt = constructorDecl.getJavadocComment().map(JavadocComment::getContent);
                            if (constructorCommentOpt.isPresent()) {
                                info.setFuncComment(constructorCommentOpt.get());
                            } else {
                                info.setFuncComment(constructorDecl.getComment().map(Comment::getContent).orElse(""));
                            }

                            break;
                        }
                    }
                }
            }
        } else {
            info.setAnnotations(new HashMap<>());
            info.setMethodModifiers(new ArrayList<>());
            info.setFuncComment("");
        }

        // 提取简单参数类型名和包名
        ParameterInfo paramInfo = ParameterInfoExtractor.extractParameterInfo(paramTypes);
        info.setFuncParams(paramInfo.getSimpleTypes());
        info.setFuncParamsPackageName(paramInfo.getPackageNames());
        
        // 尝试通过反射获取方法的返回类型
        String returnType = "void";
        String returnPackageName = StringUtil.getPackageName(className);
        
        try {
            // 尝试通过反射获取方法信息
            Class<?> clazz = Class.forName(className);
            Method method = findMethodByReflection(clazz, methodName, paramTypes);
            if (method != null) {
                // 获取返回类型
                Class<?> returnClass = method.getReturnType();
                String fullReturnType = returnClass.getTypeName();
                returnType = StringUtil.getSimpleClassName(fullReturnType);
                returnPackageName = StringUtil.getPackageName(fullReturnType);
                
                // 如果还没有设置方法修饰符，从反射中获取
                if (info.getMethodModifiers() == null || info.getMethodModifiers().isEmpty()) {
                    // 获取方法修饰符
                    int methodModifiers = method.getModifiers();
                    List<Keyword> methodModifierList = new ArrayList<>();
                    if (Modifier.isPublic(methodModifiers)) {
                        methodModifierList.add(Keyword.PUBLIC);
                    }
                    if (Modifier.isPrivate(methodModifiers)) {
                        methodModifierList.add(Keyword.PRIVATE);
                    }
                    if (Modifier.isProtected(methodModifiers)) {
                        methodModifierList.add(Keyword.PROTECTED);
                    }
                    if (Modifier.isStatic(methodModifiers)) {
                        methodModifierList.add(Keyword.STATIC);
                    }
                    if (Modifier.isFinal(methodModifiers)) {
                        methodModifierList.add(Keyword.FINAL);
                    }
                    if (Modifier.isAbstract(methodModifiers)) {
                        methodModifierList.add(Keyword.ABSTRACT);
                    }
                    if (Modifier.isNative(methodModifiers)) {
                        methodModifierList.add(Keyword.NATIVE);
                    }
                    if (Modifier.isSynchronized(methodModifiers)) {
                        methodModifierList.add(Keyword.SYNCHRONIZED);
                    }
                    info.setMethodModifiers(methodModifierList);
                }
            }
        } catch (Exception e) {
            // 反射失败时，使用默认值
        }
        
        info.setFuncReturnPackageName(returnPackageName);
        info.setFuncReturnType(returnType);
        
        return info;
    }

    /**
     * 通过反射查找方法
     */
    private Method findMethodByReflection(Class<?> clazz, String methodName, List<String> paramTypes) {
        try {
            if (paramTypes == null || paramTypes.isEmpty()) {
                return clazz.getMethod(methodName);
            }

            // 尝试匹配参数类型
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (method.getName().equals(methodName) && method.getParameterCount() == paramTypes.size()) {
                    return method;
                }
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return null;
    }

    /**
     * 通过反射查找构造器
     */
    public Constructor<?> findConstructorByReflection(Class<?> clazz, List<String> paramTypes) {
        try {
            if (paramTypes == null || paramTypes.isEmpty()) {
                return clazz.getConstructor();
            }

            // 尝试匹配参数类型
            Constructor<?>[] constructors = clazz.getConstructors();
            for (Constructor<?> constructor : constructors) {
                if (constructor.getParameterCount() == paramTypes.size()) {
                    return constructor;
                }
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return null;
    }
}