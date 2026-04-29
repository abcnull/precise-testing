package org.example.resolver.parser;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.example.constant.PathConstant;
import org.example.constant.WordConstant;
import org.example.node.field.ClassDeclaration;
import org.example.resolver.model.MethodBelongs2Class;
import org.example.util.ClassStrUtil;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

/**
 * 类解析器
 * 专门去解析类
 */
public class CompilationUnitParser {
    private ConstructorDeclParser constructorDeclParser;
    private ParameterParser parameterParser;

    private ConstructorDeclParser getConstructorDeclParser() {
        if (constructorDeclParser == null) {
            constructorDeclParser = new ConstructorDeclParser();
        }
        return constructorDeclParser;
    }

    private ParameterParser getParameterParser() {
        if (parameterParser == null) {
            parameterParser = new ParameterParser();
        }
        return parameterParser;
    }

    /**
     * 获取CompilationUnit中的第一个类型声明
     *
     * @param cu 编译单元
     * @return 第一个类型声明，如果不存在则返回null
     */
    public TypeDeclaration<?> getFirstTypeDeclaration(CompilationUnit cu) {
        if (cu != null && !cu.getTypes().isEmpty()) {
            return cu.getTypes().get(0);
        }
        return null;
    }

    /**
     * 通过 className 获取所有匹配到的类
     * className 可能含有包名，但是不含有泛型
     * 
     * @param cu        编译单元
     * @param className 类名，可能含有包名，但是不含有泛型
     * @return 类声明列表
     */
    public List<ClassOrInterfaceDeclaration> getAllClassDeclarationsByClassName(CompilationUnit cu, String className) {
        if (cu == null || StringUtils.isBlank(className)) {
            return null;
        }
        // 找到类名匹配的 Type，传入的类名可能是包含有包名的
        return cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(clazz -> className.equals(clazz.getNameAsString())
                        || className.endsWith(PathConstant.DOT + clazz.getNameAsString())) // className
                .collect(Collectors.toList());
    }

    /**
     * 通过 className 获取所有匹配到的类型声明
     * 包含类、接口、枚举、注解等
     * className 可能含有包名，但是不含有泛型
     * 
     * @param cu        编译单元
     * @param className 类型名，可能含有包名，但是不含有泛型
     * @return 类型声明列表
     */
    public List<TypeDeclaration> getAllTypeDeclarations(CompilationUnit cu, String className) {
        return cu.findAll(TypeDeclaration.class).stream()
                .filter(clazz -> className.equals(clazz.getNameAsString())
                        || className.endsWith(PathConstant.DOT + clazz.getNameAsString())) // className
                .collect(Collectors.toList());
    }

    /**
     * 通过 className 获取所有匹配到的枚举类
     * className 可能含有包名，但是不含有泛型
     * 
     * @param cu        编译单元
     * @param className 枚举类名，可能含有包名，但是不含有泛型
     * @return 枚举类声明列表
     */
    public List<EnumDeclaration> getAllEnumDeclarationsByClassName(CompilationUnit cu, String className) {
        if (cu == null) {
            return null;
        }
        // 找到类名匹配的 Type，传入的类名可能是包含有包名的
        return cu.findAll(EnumDeclaration.class).stream()
                .filter(clazz -> className
                        .equals(clazz.getNameAsString().replace(PathConstant.HYP_DOLLAR, PathConstant.DOT)) // className
                        // 不带有包名时
                        || className.endsWith(PathConstant.DOT
                                + clazz.getNameAsString().replace(PathConstant.HYP_DOLLAR, PathConstant.DOT))) // className
                                                                                                               // 带有包名时
                .collect(Collectors.toList());
    }

    /**
     * 通过 className 获取所有匹配到的注解类
     * className 可能含有包名，但是不含有泛型
     * 
     * @param cu        编译单元
     * @param className 注解类名，可能含有包名，但是不含有泛型
     * @return 注解类声明列表
     */
    public List<AnnotationDeclaration> getAllAnnotationDeclarationsByClassName(CompilationUnit cu, String className) {
        if (cu == null) {
            return null;
        }
        // 找到类名匹配的 Type，传入的类名可能是包含有包名的
        return cu.findAll(AnnotationDeclaration.class).stream()
                .filter(clazz -> className
                        .equals(clazz.getNameAsString().replace(PathConstant.HYP_DOLLAR, PathConstant.DOT)) // className
                        // 不带有包名时
                        || className.endsWith(PathConstant.DOT
                                + clazz.getNameAsString().replace(PathConstant.HYP_DOLLAR, PathConstant.DOT))) // className
                                                                                                               // 带有包名时
                .collect(Collectors.toList());
    }

    /**
     * 通过 className 获取所有匹配到的记录类
     * className 可能含有包名，但是不含有泛型
     * 
     * @param cu        编译单元
     * @param className 记录类名，可能含有包名，但是不含有泛型
     * @return 记录类声明列表
     */
    public List<RecordDeclaration> getAllRecordDeclarationsByClassName(CompilationUnit cu, String className) {
        if (cu == null) {
            return null;
        }
        // 找到类名匹配的 Type，传入的类名可能是包含有包名的
        return cu.findAll(RecordDeclaration.class).stream()
                .filter(clazz -> className
                        .equals(clazz.getNameAsString().replace(PathConstant.HYP_DOLLAR, PathConstant.DOT)) // className
                        // 不带有包名时
                        || className.endsWith(PathConstant.DOT
                                + clazz.getNameAsString().replace(PathConstant.HYP_DOLLAR, PathConstant.DOT))) // className
                                                                                                               // 带有包名时
                .collect(Collectors.toList());
    }

    /**
     * 从编译单元中找到查找方法声明
     *
     * @param targetCu   编译单元
     * @param fromClass  方法所属的类
     * @param methodName 方法名
     * @param paramTypes 参数类型列表，比如：["String", "Object", "int"]
     * @return 方法声明
     */
    public MethodDeclaration parseOutMethodDeclaration(CompilationUnit targetCu, MethodBelongs2Class fromClass,
            String methodName, List<String> paramTypes) {
        if (fromClass == null || fromClass == MethodBelongs2Class.ANCESTOR_CLASS
                || fromClass == MethodBelongs2Class.UNKNOWN) {
            return null;
        }
        // 如果该类是 jdk/第三方依赖类，或者实现类就是解析失败
        if (targetCu == null) {
            return null;
        }

        // 一般的方法（这里 paramTypes 的 UNKNOWN 一定会被修复）
        MethodDeclaration methodDeclaration = parseOutUsualMethodDeclaration(targetCu, fromClass, methodName,
                paramTypes);
        if (methodDeclaration != null) {
            return methodDeclaration;
        }

        // 构造器方法（这里 paramTypes 的 UNKNOWN 一定会被修复）
        ConstructorDeclaration constructorDeclaration = parseOutConstructorDeclaration(targetCu, fromClass, methodName,
                paramTypes);
        if (constructorDeclaration != null) {
            return getConstructorDeclParser().convertConstructor2Method(constructorDeclaration);
        }

        return null;
    }

    /**
     * 查找一般方法声明，其中 params 会被替换成正确的
     *
     * @param cu         编译单元
     * @param fromClass  方法所属的类
     * @param methodName 方法名
     * @param paramTypes 参数类型列表，比如：["包名.String", "包名.Object", "int"]
     * @return 方法声明
     */
    private MethodDeclaration parseOutUsualMethodDeclaration(CompilationUnit cu, MethodBelongs2Class fromClass,
            String methodName, List<String> paramTypes) {
        if (cu == null || StringUtils.isBlank(methodName)) {
            return null;
        }
        List<MethodDeclaration> methodDeclarations = getAllUsualMethodDeclaration(cu, methodName, paramTypes);

        if (CollectionUtils.isEmpty(methodDeclarations) || methodDeclarations.size() > 1) {
            return null;
        }

        MethodDeclaration methodDeclaration = methodDeclarations.get(0);

        // 给 paramTypes 参数为 UNKNOWN 被赋成正确的值
        if (paramTypes != null && paramTypes.contains(WordConstant.PARAM_TYPE_UNKNOWN)) {
            for (int i = 0; i < paramTypes.size(); i++) {
                // 获取 param
                String paramType = paramTypes.get(i);
                try {
                    // 带有包名，去掉泛型
                    paramType = getParameterParser().parseOutTypeStrWithNoGeneric(methodDeclaration.getParameter(i));
                } catch (Exception ignore) {
                    // 不带有包名，去掉泛型
                    paramType = getParameterParser()
                            .parseOutBaseTypeStrWithNoGeneric(methodDeclaration.getParameter(i));
                }
                // 替换
                paramTypes.set(i, paramType);
            }
        }

        return methodDeclaration;
    }

    /**
     * 查找构造器方法声明
     * 
     * @param cu         编译单元
     * @param fromClass  方法所属的类
     * @param methodName 构造器方法名
     * @param paramTypes 参数类型列表，比如：["String", "Object", "int"]
     * @return 构造器方法声明
     */
    private ConstructorDeclaration parseOutConstructorDeclaration(CompilationUnit cu, MethodBelongs2Class fromClass,
            String methodName, List<String> paramTypes) {
        if (cu == null || StringUtils.isBlank(methodName)) {
            return null;
        }
        List<ConstructorDeclaration> constructorDeclarations = getAllConstructorDeclaration(cu, methodName, paramTypes);

        if (CollectionUtils.isEmpty(constructorDeclarations) || constructorDeclarations.size() > 1) {
            return null;
        }

        ConstructorDeclaration constructorDeclaration = constructorDeclarations.get(0);

        // paramTypes 参数被赋成正确的值
        if (paramTypes.contains(WordConstant.PARAM_TYPE_UNKNOWN) && paramTypes != null) {
            for (int i = 0; i < paramTypes.size(); i++) {
                // 获取真实的 param
                String paramType = paramTypes.get(i);
                try {
                    // 带有包名，去掉泛型
                    paramType = getParameterParser()
                            .parseOutTypeStrWithNoGeneric(constructorDeclaration.getParameter(i));
                } catch (Exception ignore) {
                    // 不带有包名，去掉泛型
                    paramType = parameterParser
                            .parseOutBaseTypeStrWithNoGeneric(constructorDeclaration.getParameter(i));
                }
                // 替换
                paramTypes.set(i, paramType);
            }
        }

        return constructorDeclaration;
    }

    /**
     * 解析类属于接口/抽象类还是什么类
     * 
     * @param cu        编译单元
     * @param className 类名字符串
     * @return 类的类型
     */
    public ClassDeclaration parseOutClassDeclaration(CompilationUnit cu, String className) {
        List<ClassOrInterfaceDeclaration> classDeclarations = getAllClassDeclarationsByClassName(cu, className);
        int classCount = classDeclarations == null ? 0 : classDeclarations.size();
        List<EnumDeclaration> enumDeclarations = getAllEnumDeclarationsByClassName(cu, className);
        int enumCount = enumDeclarations == null ? 0 : enumDeclarations.size();
        List<AnnotationDeclaration> annotationDeclarations = getAllAnnotationDeclarationsByClassName(cu, className);
        int annotationCount = annotationDeclarations == null ? 0 : annotationDeclarations.size();
        List<RecordDeclaration> recordDeclarations = getAllRecordDeclarationsByClassName(cu, className);
        int recordCount = recordDeclarations == null ? 0 : recordDeclarations.size();

        if (classCount + enumCount + annotationCount + recordCount != 1) {
            // 只可能说明类不存在
            return null;
        }

        if (classCount == 1) {
            if (classDeclarations.get(0).isInterface()) {
                return ClassDeclaration.INTERFACE;
            } else {
                return ClassDeclaration.CLASS;
            }
        } else if (enumCount == 1) {
            return ClassDeclaration.ENUM;
        } else if (annotationCount == 1) {
            return ClassDeclaration.ANNOTATION;
        } else if (recordCount == 1) {
            return ClassDeclaration.RECORD;
        } else {
            return null;
        }
    }

    /**
     * 判断是否存在 1 个普通方法声明，返回 null 表示找到多个
     * 
     * @param cu         编译单元
     * @param methodName 方法名字符串
     * @param params     参数类型字符串列表，支持 UNKNOWN
     * @return 是否存在普通方法声明, 找到多个这样的方法返回 null，找到 1 个返回 true，没找到就返回 false
     */
    public Boolean judgeUsualMethodDeclarationExist(CompilationUnit cu, String methodName, List<String> params) {
        if (cu == null || StringUtils.isBlank(methodName)) {
            return false;
        }

        // 存在就返回 true，否则返回 false
        List<MethodDeclaration> methodList = getAllUsualMethodDeclaration(cu, methodName, params);

        if (CollectionUtils.isEmpty(methodList)) {
            return false;
        } else if (methodList.size() == 1) {
            return true;
        } else {
            return null;
        }
    }

    /**
     * 依据方法名和参数，获取 cu 中对应的普通方法声明
     * 
     * @param cu         编译单元
     * @param methodName 方法名字符串
     * @param params     参数类型字符串列表，一般是含有包名，不带有泛型的类
     * @return 所有普通方法声明
     */
    public List<MethodDeclaration> getAllUsualMethodDeclaration(CompilationUnit cu, String methodName,
            List<String> params) {
        if (cu == null || StringUtils.isBlank(methodName)) {
            return null;
        }
        int paramCount = params == null ? 0 : params.size();
        List<MethodDeclaration> methodList = cu.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals(methodName))
                .filter(method -> {
                    // 参数数量
                    if (paramCount != method.getParameters().size()) {
                        return false;
                    }
                    // 都空
                    if (paramCount == 0 && method.getParameters().size() == 0) {
                        return true;
                    }
                    // 参数每个值匹配
                    boolean isAllParamMatch = true;
                    for (int i = 0; i < paramCount; i++) {
                        if (params.get(i).equals(WordConstant.PARAM_TYPE_UNKNOWN)) {
                            // 算作匹配上
                            continue;
                        }
                        // 不带有泛型简单类名
                        String actual = getParameterParser().parseOutBaseTypeStrWithNoGeneric(method.getParameter(i));
                        // 带有包名，不带有反省
                        String expected = params.get(i);
                        // 进行更严格的匹配：
                        if (ClassStrUtil.typeMatches(actual, expected)) {
                            // 匹配上了
                            continue;
                        }
                        isAllParamMatch = false;
                        break;
                    }
                    return isAllParamMatch;
                }).collect(Collectors.toList());
        return methodList;
    }

    /**
     * 依据方法名和参数，获取 cu 中对应的构造器声明
     * 
     * @param cu         编译单元
     * @param methodName 方法名字符串
     * @param params     参数类型字符串列表，一般是含有包名，不带有泛型的类
     * @return 所有构造器声明
     */
    public List<ConstructorDeclaration> getAllConstructorDeclaration(CompilationUnit cu, String methodName,
            List<String> params) {
        if (cu == null || StringUtils.isBlank(methodName)) {
            return null;
        }
        int paramCount = params == null ? 0 : params.size();
        List<ConstructorDeclaration> constructorList = cu.findAll(ConstructorDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals(methodName))
                .filter(method -> {
                    // 参数数量
                    if (paramCount != method.getParameters().size()) {
                        return false;
                    }
                    // 都空
                    if (paramCount == 0 && method.getParameters().size() == 0) {
                        return true;
                    }
                    // 参数每个值匹配
                    boolean isAllParamMatch = true;
                    for (int i = 0; i < paramCount; i++) {
                        if (params.get(i).equals(WordConstant.PARAM_TYPE_UNKNOWN)) {
                            // 算作匹配上
                            continue;
                        }
                        // 不带有泛型简单类名
                        String actual = parameterParser.parseOutBaseTypeStrWithNoGeneric(method.getParameter(i));
                        // 带有包名，不带有反省
                        String expected = params.get(i);
                        // 进行更严格的匹配：
                        if (ClassStrUtil.typeMatches(actual, expected)) {
                            // 匹配上了
                            continue;
                        }
                        isAllParamMatch = false;
                        break;
                    }
                    return isAllParamMatch;
                }).collect(Collectors.toList());
        return constructorList;
    }

    /**
     * 从 CompilationUnit 中获取绝对路径
     * 注意：前提 cu 是由 javaparser.parse(File) 文件形式创建的，否则返回 null
     * 
     * @param cu 编译单元
     * @return 绝对路径字符串
     */
    public String parseOutAbsolutePathFromCu(CompilationUnit cu) {
        if (cu == null) {
            return null;
        }
        // 前提 cu 是由 javaparser.parse(File) 文件形式创建的，否则返回 null
        return cu.getStorage()
                .map(storage -> storage.getPath().toAbsolutePath().toString())
                .orElse(null);

    }
}