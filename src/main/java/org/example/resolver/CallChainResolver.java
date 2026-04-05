package org.example.resolver;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.treenode.AstNode;
import org.example.treenode.ClassInfo;
import org.example.treenode.FuncCate;
import org.example.treenode.FuncInfo;
import org.example.treenode.PackageInfo;
import org.example.treenode.ClassDeclaration;
import org.example.treenode.ClassOrigin;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

public class CallChainResolver {

    private final String sourceRootPath; // 项目根目录
    private final JavaParser javaParser; // 用于解析Java源文件
    private final Map<String, CompilationUnit> parsedFiles; // key: 类全限定名, value: 编译单元
    private final Set<String> visitedMethods; // 已访问的方法签名集合

    public CallChainResolver(String sourceRootPath) {
        this.sourceRootPath = sourceRootPath;
        this.parsedFiles = new HashMap<>();
        this.visitedMethods = new HashSet<>();

        // 配置类型解析器
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        
        // 添加JavaParserTypeSolver来解析项目中的自定义类
        try {
            File sourceRoot = new File(sourceRootPath);
            if (!sourceRoot.exists()) {
                // 尝试使用src/main/java作为默认源码目录
                sourceRoot = new File(sourceRootPath, "src/main/java");
            }
            if (sourceRoot.exists()) {
                combinedTypeSolver.add(new JavaParserTypeSolver(sourceRoot));
            }
        } catch (Exception e) {
            // 忽略异常，继续使用ReflectionTypeSolver
        }

        // 配置JavaParser
        ParserConfiguration config = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(combinedTypeSolver));
        this.javaParser = new JavaParser(config);
    }

    /**
     * 从指定方法开始解析调用链路
     *
     * @param className  类全限定名
     * @param methodName 方法名
     * @param paramTypes 参数类型列表
     * @return AstNode 调用链树根节点
     */
    public AstNode<PackageInfo, ClassInfo, FuncInfo> resolveCallChain(String className, String methodName, List<String> paramTypes) {
        visitedMethods.clear();

        // 获取或解析源文件，获取完整的参数类型
        CompilationUnit cu = getOrParseCompilationUnit(className);
        if (cu != null) {
            // 查找目标方法
            MethodDeclaration targetMethod = findMethodDeclaration(cu, methodName, paramTypes);
            if (targetMethod != null) {
                // 使用完整参数类型重新构建方法签名
                List<String> fullParamTypes = extractFullParamTypes(targetMethod);
                // 递归解析方法调用
                return resolveMethodCall(className, className, methodName, fullParamTypes);
            }
        }

        return resolveMethodCall(className, className, methodName, paramTypes);
    }

    /**
     * 递归解析方法调用
     */
    private AstNode<PackageInfo, ClassInfo, FuncInfo> resolveMethodCall(String className, String realClassName, String methodName, List<String> paramTypes) {
        // 生成唯一标识符用于循环检测
        String methodSignature = buildMethodSignature(className, methodName, paramTypes);

        // 检测循环调用
        if (visitedMethods.contains(methodSignature)) {
            return createCycleNode(className, realClassName, methodName, paramTypes);
        }

        visitedMethods.add(methodSignature);

        // 尝试首先解析实际类型（多态情况）
        CompilationUnit cu = getOrParseCompilationUnit(realClassName);
        MethodDeclaration targetMethod = null;
        
        // 如果实际类型不存在或找不到方法，回退到声明类型
        if (cu == null || (targetMethod = findMethodDeclaration(cu, methodName, paramTypes)) == null) {
            cu = getOrParseCompilationUnit(className);
            if (cu == null) {
                // 无法解析的文件（如JDK类库），创建叶节点
                visitedMethods.remove(methodSignature); // 回溯：移除当前方法
                return createLeafNode(className, realClassName, methodName, paramTypes);
            }
            targetMethod = findMethodDeclaration(cu, methodName, paramTypes);
            if (targetMethod == null) {
                visitedMethods.remove(methodSignature); // 回溯：移除当前方法，如果不移除会导致 A -> C, A -> B -> C，第二个 C 会被判定为循环调用
                return createLeafNode(className, realClassName, methodName, paramTypes);
            }
        }

        // 创建当前节点
        AstNode<PackageInfo, ClassInfo, FuncInfo> currentNode = new AstNode<>();
        
        // 设置包信息
        String packageName = getPackageName(className);
        String realPackageName = getPackageName(realClassName);
        PackageInfo packageInfo = new PackageInfo(packageName, realPackageName);
        currentNode.setPackageInfo(packageInfo);
        
        // 创建并设置ClassInfo
        ClassInfo classInfo = extractClassInfo(cu, className, realClassName);
        currentNode.setClassInfo(classInfo);
        
        // 创建并设置FuncInfo
        FuncInfo funcInfo = extractFuncInfo(cu, targetMethod);
        currentNode.setFuncInfo(funcInfo);
        
        // 设置循环调用标志
        currentNode.setLoopCall(false);
        
        // 初始化子节点列表
        currentNode.setChildren(new ArrayList<>());

        // 查找方法体内的所有方法调用
        List<MethodCallInfo> methodCalls = extractMethodCalls(targetMethod);

        // 递归解析每个方法调用，深度优先搜索
        for (MethodCallInfo callInfo : methodCalls) {
            AstNode<PackageInfo, ClassInfo, FuncInfo> childNode = resolveMethodCall(
                    callInfo.getClassName(),
                    callInfo.getRealClassName(),
                    callInfo.getMethodName(),
                    callInfo.getParamTypes());
            currentNode.getChildren().add(childNode);
        }

        visitedMethods.remove(methodSignature); // 回溯：移除当前方法
        return currentNode;
    }

    /**
     * 提取方法的完整参数类型
     */
    private List<String> extractFullParamTypes(MethodDeclaration method) {
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
     * 创建循环调用节点
     */
    private AstNode<PackageInfo, ClassInfo, FuncInfo> createCycleNode(String className, String realClassName, String methodName, List<String> paramTypes) {
        AstNode<PackageInfo, ClassInfo, FuncInfo> cycleNode = new AstNode<>();
        
        // 设置包信息
        String packageName = getPackageName(className);
        String realPackageName = getPackageName(realClassName);
        PackageInfo packageInfo = new PackageInfo(packageName, realPackageName);
        cycleNode.setPackageInfo(packageInfo);
        
        // 创建并设置ClassInfo
        ClassInfo classInfo = new ClassInfo();
        classInfo.setClassName(getSimpleClassName(className));
        classInfo.setRealClassName(getSimpleClassName(realClassName));
        classInfo.setAnnotations(new HashMap<>()); // 循环调用节点没有类注解信息
        classInfo.setClassComment(""); // 循环调用节点没有类注释信息
        classInfo.setClassModifiers(new ArrayList<>()); // 循环调用节点没有类修饰符信息
        classInfo.setClassDeclaration(ClassDeclaration.CLASS); // 循环调用节点默认为普通类
        cycleNode.setClassInfo(classInfo);
        
        // 创建并设置FuncInfo
        FuncInfo cycleInfo = new FuncInfo();
        cycleInfo.setFuncCate(FuncCate.DEFAULT); // 循环调用节点默认为普通方法
        cycleInfo.setFuncName(methodName);
        cycleInfo.setMethodModifiers(new ArrayList<>()); // 循环调用节点没有修饰符信息
        cycleInfo.setAnnotations(new HashMap<>()); // 循环调用节点没有注解信息

        // 提取简单参数类型名和包名
        ParameterInfo paramInfo = extractParameterInfo(paramTypes);
        cycleInfo.setFuncParams(paramInfo.getSimpleTypes());
        cycleInfo.setFuncParamsPackageName(paramInfo.getPackageNames());
        cycleInfo.setFuncReturnPackageName("");
        cycleInfo.setFuncReturnType("void");
        cycleInfo.setFuncComment(""); // 循环调用节点没有注释信息
        cycleNode.setFuncInfo(cycleInfo);
        
        // 设置循环调用标志
        cycleNode.setLoopCall(true);
        
        // 初始化子节点列表
        cycleNode.setChildren(new ArrayList<>());
        return cycleNode;
    }

    /**
     * 提取参数信息（简单类型名和包名）
     */
    private ParameterInfo extractParameterInfo(List<String> paramTypes) {
        List<String> simpleTypes = new ArrayList<>();
        List<String> packageNames = new ArrayList<>();

        if (paramTypes != null) {
            for (String paramType : paramTypes) {
                simpleTypes.add(getSimpleClassName(paramType));
                packageNames.add(getPackageName(paramType));
            }
        }

        return new ParameterInfo(simpleTypes, packageNames);
    }

    /**
     * 获取或解析CompilationUnit
     */
    private CompilationUnit getOrParseCompilationUnit(String className) {
        if (parsedFiles.containsKey(className)) {
            return parsedFiles.get(className);
        }

        // 将类名转换为文件路径
        String relativePath = className.replace(".", "/") + ".java";
        Path filePath = Paths.get(sourceRootPath, relativePath);
        File file = filePath.toFile();

        if (!file.exists()) {
            // 尝试在项目源码目录中查找
            Path altPath = Paths.get(sourceRootPath, "src/main/java", relativePath);
            file = altPath.toFile();
        }

        if (!file.exists()) {
            return null;
        }

        try {
            CompilationUnit cu = javaParser.parse(file).getResult().orElse(null);
            if (cu != null) {
                parsedFiles.put(className, cu);
            }
            return cu;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    /**
     * 查找方法声明
     */
    private MethodDeclaration findMethodDeclaration(CompilationUnit cu, String methodName, List<String> paramTypes) {
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals(methodName))
                .filter(method -> paramTypes == null || method.getParameters().size() == paramTypes.size())
                .findFirst()
                .orElse(null);
    }

    /**
     * 提取方法体内的所有方法调用和构造方法调用（按代码出现顺序）
     */
    private List<MethodCallInfo> extractMethodCalls(MethodDeclaration method) {
        List<MethodCallInfo> calls = new ArrayList<>();
        final MethodDeclaration finalMethod = method;

        // 按代码中出现的顺序遍历所有表达式节点，只处理 MethodCallExpr 和 ObjectCreationExpr
        method.findAll(Expression.class).forEach(expr -> {
            if (expr.isMethodCallExpr()) {
                // 处理普通方法调用
                MethodCallExpr callExpr = expr.asMethodCallExpr();
                try {
                    // 尝试解析方法调用
                    ResolvedMethodDeclaration resolvedMethod = callExpr.resolve();
                    String declaringClass = resolvedMethod.getClassName();
                    String methodName = resolvedMethod.getName();

                    // 构建完整的类名（包含包名）
                    String fullDeclaringClass = resolvedMethod.getClassName();
                    String packageName = resolvedMethod.getPackageName();
                    if (!packageName.isEmpty()) {
                        fullDeclaringClass = packageName + "." + fullDeclaringClass;
                    }
                    String[] realFullDeclaringClass = {fullDeclaringClass}; // 默认为相同的类，使用数组包装
                    
                    // 尝试解析作用域的实际类型（多态情况）
                    try {
                        if (callExpr.getScope().isPresent()) {
                            Expression scopeExpr = callExpr.getScope().get();
                            // 尝试解析变量声明，查找初始化表达式
                            if (scopeExpr.isNameExpr()) {
                                NameExpr nameExpr = scopeExpr.asNameExpr();
                                String variableName = nameExpr.getNameAsString();
                                
                                // 在当前方法中查找变量声明
                                Optional<VariableDeclarationExpr> varDeclOpt = finalMethod.findAll(VariableDeclarationExpr.class)
                                        .stream()
                                        .filter(varDecl -> varDecl.getVariables().stream()
                                                .anyMatch(var -> var.getNameAsString().equals(variableName)))
                                        .findFirst();
                                
                                if (varDeclOpt.isPresent()) {
                                    VariableDeclarationExpr varDecl = varDeclOpt.get();
                                    // 查找变量初始化表达式
                                    varDecl.getVariables().stream()
                                            .filter(var -> var.getNameAsString().equals(variableName))
                                            .findFirst()
                                            .ifPresent(var -> {
                                                if (var.getInitializer().isPresent()) {
                                                    Expression initializer = var.getInitializer().get();
                                                    // 检查是否是对象创建表达式
                                                    if (initializer.isObjectCreationExpr()) {
                                                        ObjectCreationExpr creationExpr = initializer.asObjectCreationExpr();
                                                        // 获取实际实例化的类型
                                                        String actualType = creationExpr.getType().toString();
                                                        // 构建完整的类名
                                                        String fullActualType;
                                                        if (actualType.contains(".")) {
                                                            // 已经是完整类名
                                                            fullActualType = actualType;
                                                        } else {
                                                            // 尝试从import语句中获取完整类名
                                                            String fullClassName = findFullClassNameFromImports(actualType, finalMethod);
                                                            if (fullClassName != null) {
                                                                fullActualType = fullClassName;
                                                            } else {
                                                                // 如果找不到import，使用当前包名
                                                                String initPackageName = finalMethod.findCompilationUnit()
                                                                        .flatMap(CompilationUnit::getPackageDeclaration)
                                                                        .map(pd -> pd.getNameAsString())
                                                                        .orElse("");
                                                                fullActualType = initPackageName.isEmpty() ? actualType : initPackageName + "." + actualType;
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
                        // 解析失败时保持默认值
                    }

                    // 获取参数类型
                    List<String> paramTypes = new ArrayList<>();
                    for (int i = 0; i < resolvedMethod.getNumberOfParams(); i++) {
                        String paramType = resolvedMethod.getParam(i).getType().describe();
                        paramTypes.add(paramType);
                    }

                    calls.add(new MethodCallInfo(fullDeclaringClass, realFullDeclaringClass[0], methodName, paramTypes));
                } catch (Exception e) {
                    // 解析失败时，使用简单推断
                    String methodName = callExpr.getNameAsString();
                    String[] inferredClass = {inferClassNameFromCall(callExpr, finalMethod)}; // 使用数组包装
                    String[] realInferredClass = {inferredClass[0]}; // 默认为相同的类，使用数组包装
                    
                    // 尝试解析作用域的实际类型（多态情况）
                    try {
                        if (callExpr.getScope().isPresent()) {
                            Expression scopeExpr = callExpr.getScope().get();
                            // 尝试解析变量声明，查找初始化表达式
                            if (scopeExpr.isNameExpr()) {
                                NameExpr nameExpr = scopeExpr.asNameExpr();
                                String variableName = nameExpr.getNameAsString();
                                
                                // 在当前方法中查找变量声明
                                Optional<VariableDeclarationExpr> varDeclOpt = finalMethod.findAll(VariableDeclarationExpr.class)
                                        .stream()
                                        .filter(varDecl -> varDecl.getVariables().stream()
                                                .anyMatch(var -> var.getNameAsString().equals(variableName)))
                                        .findFirst();
                                
                                if (varDeclOpt.isPresent()) {
                                    VariableDeclarationExpr varDecl = varDeclOpt.get();
                                    // 查找变量初始化表达式
                                    varDecl.getVariables().stream()
                                            .filter(var -> var.getNameAsString().equals(variableName))
                                            .findFirst()
                                            .ifPresent(var -> {
                                                // 尝试获取变量的类型（声明类型）
                                                String variableType = var.getType().toString();
                                                // 构建完整的类名（包含包名）
                                                String packageName = finalMethod.findCompilationUnit()
                                                        .flatMap(CompilationUnit::getPackageDeclaration)
                                                        .map(pd -> pd.getNameAsString())
                                                        .orElse("");
                                                
                                                // 尝试解析变量类型的完整类名
                                                try {
                                                    String fullVariableType = var.getType().resolve().describe();
                                                    inferredClass[0] = fullVariableType;
                                                } catch (Exception ex) {
                                                    // 解析失败时，使用简单推断
                                                    if (!variableType.contains(".")) {
                                                        // 如果类型名不包含包名，尝试从import语句中获取
                                                        String fullClassName = findFullClassNameFromImports(variableType, finalMethod);
                                                        if (fullClassName != null) {
                                                            inferredClass[0] = fullClassName;
                                                        } else {
                                                            // 如果找不到import，使用当前包名
                                                            inferredClass[0] = packageName.isEmpty() ? variableType : packageName + "." + variableType;
                                                        }
                                                    } else {
                                                        // 已经是完整类名
                                                        inferredClass[0] = variableType;
                                                    }
                                                }
                                                
                                                if (var.getInitializer().isPresent()) {
                                                    Expression initializer = var.getInitializer().get();
                                                    // 检查是否是对象创建表达式
                                                    if (initializer.isObjectCreationExpr()) {
                                                        ObjectCreationExpr creationExpr = initializer.asObjectCreationExpr();
                                                        // 获取实际实例化的类型
                                                        String actualType = creationExpr.getType().toString();
                                                        // 构建完整的类名
                                                        String initPackageName = finalMethod.findCompilationUnit()
                                                                .flatMap(CompilationUnit::getPackageDeclaration)
                                                                .map(pd -> pd.getNameAsString())
                                                                .orElse("");
                                                        realInferredClass[0] = initPackageName.isEmpty() ? actualType : initPackageName + "." + actualType;
                                                    }
                                                }
                                            });
                                }
                            }
                        }
                    } catch (Exception ex) {
                        // 解析失败时保持默认值
                    }

                    // 推断参数类型
                    List<String> paramTypes = new ArrayList<>();
                    callExpr.getArguments().forEach(arg -> {
                        try {
                            paramTypes.add(arg.calculateResolvedType().describe());
                        } catch (Exception ex) {
                            // 如果无法解析，使用简单类型名
                            paramTypes.add(arg.toString());
                        }
                    });

                    calls.add(new MethodCallInfo(inferredClass[0], realInferredClass[0], methodName, paramTypes));
                }
            } else if (expr.isObjectCreationExpr()) {
                // 处理构造方法调用
                ObjectCreationExpr creationExpr = expr.asObjectCreationExpr();
                try {
                    // 尝试解析构造方法调用
                    com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration resolvedConstructor = creationExpr.resolve();
                    String declaringClass = resolvedConstructor.getClassName();
                    // 使用类名作为构造方法的 funcName
                    String methodName = declaringClass;

                    // 构建完整的类名（包含包名）
                    String fullDeclaringClass = resolvedConstructor.getClassName();
                    String packageName = resolvedConstructor.getPackageName();
                    if (!packageName.isEmpty()) {
                        fullDeclaringClass = packageName + "." + fullDeclaringClass;
                    }

                    // 获取参数类型
                    List<String> paramTypes = new ArrayList<>();
                    for (int i = 0; i < resolvedConstructor.getNumberOfParams(); i++) {
                        String paramType = resolvedConstructor.getParam(i).getType().describe();
                        paramTypes.add(paramType);
                    }

                    calls.add(new MethodCallInfo(fullDeclaringClass, fullDeclaringClass, methodName, paramTypes));
                } catch (Exception e) {
                    // 解析失败时，使用简单推断
                    String className = creationExpr.getType().toString();
                    // 使用类名作为构造方法的 funcName
                    String methodName = getSimpleClassName(className);
                    // 构建完整的类名
                    String fullClassName;
                    if (className.contains(".")) {
                        // 已经是完整类名
                        fullClassName = className;
                    } else {
                        // 尝试从import语句中获取完整类名
                        String fullClassNameFromImports = findFullClassNameFromImports(className, finalMethod);
                        if (fullClassNameFromImports != null) {
                            fullClassName = fullClassNameFromImports;
                        } else {
                            // 如果找不到import，使用当前包名
                            String initPackageName = finalMethod.findCompilationUnit()
                                    .flatMap(CompilationUnit::getPackageDeclaration)
                                    .map(pd -> pd.getNameAsString())
                                    .orElse("");
                            fullClassName = initPackageName.isEmpty() ? className : initPackageName + "." + className;
                        }
                    }

                    // 推断参数类型
                    List<String> paramTypes = new ArrayList<>();
                    creationExpr.getArguments().forEach(arg -> {
                        try {
                            paramTypes.add(arg.calculateResolvedType().describe());
                        } catch (Exception ex) {
                            // 如果无法解析，使用简单类型名
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
        // 获取包含该方法的类的包名
        String packageName = containingMethod.findCompilationUnit()
                .flatMap(CompilationUnit::getPackageDeclaration)
                .map(pd -> pd.getNameAsString())
                .orElse("");

        // 简单推断：如果是简单名称调用，假设是同包下的类
        if (callExpr.getScope().isPresent()) {
            Expression scopeExpr = callExpr.getScope().get();
            // 对于链式调用，尝试获取最内层的作用域
            while (scopeExpr.isMethodCallExpr()) {
                scopeExpr = scopeExpr.asMethodCallExpr().getScope().orElse(scopeExpr);
            }
            
            final String[] scopeArray = {scopeExpr.toString()}; // 使用数组包装，以便在lambda中修改
            // 如果是变量名（如 level2），尝试推断类型
            // 如果是类名（如 StringUtils），尝试从import语句中获取完整类名
            if (Character.isLowerCase(scopeArray[0].charAt(0))) {
                // 尝试在当前方法中查找变量声明
                Optional<VariableDeclarationExpr> varDeclOpt = containingMethod.findAll(VariableDeclarationExpr.class)
                        .stream()
                        .filter(varDecl -> varDecl.getVariables().stream()
                                .anyMatch(var -> var.getNameAsString().equals(scopeArray[0])))
                        .findFirst();
                
                if (varDeclOpt.isPresent()) {
                    VariableDeclarationExpr varDecl = varDeclOpt.get();
                    // 查找变量初始化表达式
                    varDecl.getVariables().stream()
                            .filter(var -> var.getNameAsString().equals(scopeArray[0]))
                            .findFirst()
                            .ifPresent(var -> {
                                // 尝试获取变量的类型（声明类型）
                                String variableType = var.getType().toString();
                                // 移除泛型参数
                                int genericStart = variableType.indexOf('<');
                                if (genericStart > 0) {
                                    variableType = variableType.substring(0, genericStart);
                                }
                                // 构建完整的类名（包含包名）
                                if (!variableType.contains(".")) {
                                    // 如果类型名不包含包名，尝试从import语句中获取
                                    String fullClassName = findFullClassNameFromImports(variableType, containingMethod);
                                    if (fullClassName != null) {
                                        scopeArray[0] = fullClassName;
                                    } else {
                                        // 如果找不到import，使用当前包名
                                        scopeArray[0] = packageName.isEmpty() ? variableType : packageName + "." + variableType;
                                    }
                                } else {
                                    // 已经是完整类名
                                    scopeArray[0] = variableType;
                                }
                            });
                } else {
                    // 如果找不到变量声明，使用默认处理
                    String className = Character.toUpperCase(scopeArray[0].charAt(0)) + scopeArray[0].substring(1);
                    // 确保返回完整的类名（包含包名）
                    return packageName.isEmpty() ? className : packageName + "." + className;
                }
            } else {
                // 大写开头，可能是类名（如 StringUtils）
                String scopeName = scopeArray[0];
                // 尝试从import语句中获取完整类名
                String fullClassName = findFullClassNameFromImports(scopeName, containingMethod);
                if (fullClassName != null) {
                    return fullClassName;
                }
            }
            return scopeArray[0];
        }

        // 没有scope，可能是本类方法或父类方法
        return containingMethod.findCompilationUnit()
                .flatMap(cu -> cu.getTypes().stream().findFirst())
                .map(type -> {
                    String typeName = type.getNameAsString();
                    return packageName.isEmpty() ? typeName : packageName + "." + typeName;
                })
                .orElse("");
    }

    /**
     * 核心：提取方法信息
     */
    private FuncInfo extractFuncInfo(CompilationUnit cu, MethodDeclaration method) {
        FuncInfo info = new FuncInfo();

        // 设置方法分类
        if (isMainMethod(method)) {
            info.setFuncCate(FuncCate.MAIN);
        } else {
            info.setFuncCate(FuncCate.DEFAULT);
        }

        // 方法修饰符 - 从 Modifier 列表转换为 Keyword 列表
        List<Keyword> keywords = method.getModifiers().stream()
                .map(modifier -> modifier.getKeyword())
                .collect(Collectors.toList());
        info.setMethodModifiers(keywords);

        // 方法注解
        info.setAnnotations(extractAnnotations(method));

        // 方法名
        info.setFuncName(method.getNameAsString());

        // 参数类型（简单类型名）和参数包名
        ParameterInfo paramInfo = extractParameterInfo(extractFullParamTypes(method));
        info.setFuncParams(paramInfo.getSimpleTypes());
        info.setFuncParamsPackageName(paramInfo.getPackageNames());

        // 返回值类型和返回值包名
        ReturnTypeInfo returnTypeInfo = extractReturnTypeInfo(method);
        info.setFuncReturnType(returnTypeInfo.getSimpleType());
        info.setFuncReturnPackageName(returnTypeInfo.getPackageName());

        // 方法注释
        info.setFuncComment(extractMethodComment(method));

        return info;
    }
    
    /**
     * 判断是否是 main 方法
     */
    private boolean isMainMethod(MethodDeclaration method) {
        return method.getNameAsString().equals("main") &&
               method.getParameters().size() == 1 &&
               method.getParameters().get(0).getType().toString().equals("String[]") &&
               method.getType().toString().equals("void") &&
               method.getModifiers().stream().anyMatch(m -> m.getKeyword() == Keyword.PUBLIC) &&
               method.getModifiers().stream().anyMatch(m -> m.getKeyword() == Keyword.STATIC);
    }
    
    /**
     * 提取方法注释
     */
    private String extractMethodComment(MethodDeclaration method) {
        Optional<String> commentOpt = method.getJavadocComment().map(javadoc -> javadoc.getContent());
        if (commentOpt.isPresent()) {
            return commentOpt.get();
        }
        
        // 尝试获取普通注释
        return method.getComment().map(comment -> comment.getContent()).orElse("");
    }

    /**
     * 提取方法注解信息
     * 返回格式: Map<注解名, Map<参数名, 参数值>>
     * 对于标记注解（无参数），参数Map为空
     * 对于单参数注解，参数名为 "value"
     */
    private Map<String, Map<String, Object>> extractAnnotations(MethodDeclaration method) {
        Map<String, Map<String, Object>> annotations = new HashMap<>();
        NodeList<AnnotationExpr> annotationExprs = method.getAnnotations();

        for (AnnotationExpr annotation : annotationExprs) {
            String annotationName = annotation.getNameAsString();
            Map<String, Object> params = new HashMap<>();

            if (annotation.isNormalAnnotationExpr()) {
                // 多参数注解，如 @RequestMapping(value = "/api", method = GET)
                NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
                for (MemberValuePair pair : normal.getPairs()) {
                    String paramName = pair.getNameAsString();
                    Object paramValue = extractAnnotationValue(pair.getValue());
                    params.put(paramName, paramValue);
                }
            } else if (annotation.isSingleMemberAnnotationExpr()) {
                // 单参数注解，如 @SuppressWarnings("unchecked")
                SingleMemberAnnotationExpr single = annotation.asSingleMemberAnnotationExpr();
                Object paramValue = extractAnnotationValue(single.getMemberValue());
                params.put("value", paramValue);
            }
            // 标记注解（无参数）如 @Override，params保持为空

            annotations.put(annotationName, params);
        }

        return annotations;
    }

    /**
     * 提取类结构信息
     */
    private ClassInfo extractClassInfo(CompilationUnit cu, String className, String realClassName) {
        ClassInfo classInfo = new ClassInfo();
        String simpleClassName = getSimpleClassName(className);
        classInfo.setClassName(simpleClassName);
        classInfo.setRealClassName(getSimpleClassName(realClassName)); // 设置真实类名
        
        // 设置类来源
        if (cu != null) {
            // 如果有CompilationUnit，说明是项目内的类
            classInfo.setClassOrigin(ClassOrigin.PROJECT);
        } else {
            // 否则根据包名判断是JDK类还是第三方依赖类
            String packageName = getPackageName(className);
            if (packageName.startsWith("java.") || packageName.startsWith("javax.")) {
                classInfo.setClassOrigin(ClassOrigin.JDK);
            } else {
                classInfo.setClassOrigin(ClassOrigin.DEPENDENCY);
            }
        }
        
        // 查找类声明
        if (cu != null) {
            // 遍历所有类型声明
            for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
                // 提取类注解
                classInfo.setAnnotations(extractClassAnnotations(typeDecl));
                
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
                
                // 找到第一个类型声明后就退出循环
                break;
            }
        } else {
            // 未找到类声明，使用默认值
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
        
        // 尝试获取普通注释
        return typeDecl.getComment().map(comment -> comment.getContent()).orElse("");
    }
    
    /**
     * 提取类注释（兼容旧方法）
     */
    private String extractClassComment(ClassOrInterfaceDeclaration classDecl) {
        return extractClassComment((TypeDeclaration<?>) classDecl);
    }
    
    /**
     * 提取类注解信息
     */
    private Map<String, Map<String, Object>> extractClassAnnotations(TypeDeclaration<?> typeDecl) {
        Map<String, Map<String, Object>> annotations = new HashMap<>();
        NodeList<AnnotationExpr> annotationExprs = typeDecl.getAnnotations();

        for (AnnotationExpr annotation : annotationExprs) {
            String annotationName = annotation.getNameAsString();
            Map<String, Object> params = new HashMap<>();

            if (annotation.isNormalAnnotationExpr()) {
                // 多参数注解，如 @RequestMapping(value = "/api", method = GET)
                NormalAnnotationExpr normal = annotation.asNormalAnnotationExpr();
                for (MemberValuePair pair : normal.getPairs()) {
                    String paramName = pair.getNameAsString();
                    Object paramValue = extractAnnotationValue(pair.getValue());
                    params.put(paramName, paramValue);
                }
            } else if (annotation.isSingleMemberAnnotationExpr()) {
                // 单参数注解，如 @SuppressWarnings("unchecked")
                SingleMemberAnnotationExpr single = annotation.asSingleMemberAnnotationExpr();
                Object paramValue = extractAnnotationValue(single.getMemberValue());
                params.put("value", paramValue);
            }
            // 标记注解（无参数）如 @Override，params保持为空

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
            return valueExpr.asIntegerLiteralExpr().asInt();
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
            // 类类型，如 String.class
            return valueExpr.asClassExpr().getType().asString() + ".class";
        } else {
            // 其他类型，转为字符串
            return valueExpr.toString();
        }
    }

    /**
     * 提取返回值信息（简单类型名和包名）
     */
    private ReturnTypeInfo extractReturnTypeInfo(MethodDeclaration method) {
        try {
            String fullReturnType = method.getType().resolve().describe();
            String simpleReturnType = getSimpleClassName(fullReturnType);
            String returnPackageName = getPackageName(fullReturnType);
            return new ReturnTypeInfo(simpleReturnType, returnPackageName);
        } catch (Exception e) {
            String returnType = method.getType().asString();
            String simpleReturnType = getSimpleClassName(returnType);
            String returnPackageName = getPackageName(returnType);
            return new ReturnTypeInfo(simpleReturnType, returnPackageName);
        }
    }

    /**
     * 创建叶节点（用于无法解析的方法）
     */
    private AstNode<PackageInfo, ClassInfo, FuncInfo> createLeafNode(String className, String realClassName, String methodName, List<String> paramTypes) {
        AstNode<PackageInfo, ClassInfo, FuncInfo> node = new AstNode<>();
        
        // 设置包信息
        String packageName = getPackageName(className);
        String realPackageName = getPackageName(realClassName);
        PackageInfo packageInfo = new PackageInfo(packageName, realPackageName);
        node.setPackageInfo(packageInfo);
        
        // 创建并设置ClassInfo
        ClassInfo classInfo = new ClassInfo();
        String simpleClassName = getSimpleClassName(className);
        classInfo.setClassName(simpleClassName);
        classInfo.setRealClassName(getSimpleClassName(realClassName));
        classInfo.setAnnotations(new HashMap<>()); // 叶节点没有类注解信息
        classInfo.setClassComment(""); // 叶节点没有类注释信息（无法通过反射获取）
        classInfo.setClassModifiers(new ArrayList<>()); // 初始化为空列表
        classInfo.setClassDeclaration(ClassDeclaration.CLASS); // 叶节点默认为普通类
        
        // 设置类来源
        if (packageName.startsWith("java.") || packageName.startsWith("javax.")) {
            classInfo.setClassOrigin(ClassOrigin.JDK);
        } else {
            classInfo.setClassOrigin(ClassOrigin.DEPENDENCY);
        }
        
        node.setClassInfo(classInfo);
        
        // 创建并设置FuncInfo
        FuncInfo info = new FuncInfo();
        // 设置方法分类
        if (methodName.equals(getSimpleClassName(className))) {
            // 构造方法：方法名与类名相同
            info.setFuncCate(FuncCate.CONSTRUCTOR);
        } else if (methodName.equals("main")) {
            // 判断是否为真正的main方法
            boolean isMain = false;
            try {
                // 尝试通过反射获取方法信息
                Class<?> clazz = Class.forName(className);
                java.lang.reflect.Method method = findMethodByReflection(clazz, methodName, paramTypes);
                if (method != null) {
                    // 检查方法签名是否符合main方法的要求
                    isMain = method.getParameterCount() == 1 &&
                             method.getParameterTypes()[0].isArray() &&
                             method.getParameterTypes()[0].getComponentType().equals(String.class) &&
                             method.getReturnType().equals(void.class) &&
                             java.lang.reflect.Modifier.isPublic(method.getModifiers()) &&
                             java.lang.reflect.Modifier.isStatic(method.getModifiers());
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
        info.setMethodModifiers(new ArrayList<>()); // 初始化为空列表
        info.setAnnotations(new HashMap<>()); // 叶节点没有注解信息

        // 尝试使用JavaParser的类型解析能力获取返回类型
        String returnType = "void";
        String returnPackageName = packageName;
        
        try {
            // 尝试通过反射获取方法信息
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
            
            // 构建方法签名
            java.lang.reflect.Method method = findMethodByReflection(clazz, methodName, paramTypes);
            if (method != null) {
                // 获取返回类型
                Class<?> returnClass = method.getReturnType();
                String fullReturnType = returnClass.getTypeName();
                returnType = getSimpleClassName(fullReturnType);
                returnPackageName = getPackageName(fullReturnType);
                
                // 获取方法修饰符
                int methodModifiers = method.getModifiers();
                List<Keyword> methodModifierList = new ArrayList<>();
                if (java.lang.reflect.Modifier.isPublic(methodModifiers)) {
                    methodModifierList.add(Keyword.PUBLIC);
                }
                if (java.lang.reflect.Modifier.isPrivate(methodModifiers)) {
                    methodModifierList.add(Keyword.PRIVATE);
                }
                if (java.lang.reflect.Modifier.isProtected(methodModifiers)) {
                    methodModifierList.add(Keyword.PROTECTED);
                }
                if (java.lang.reflect.Modifier.isStatic(methodModifiers)) {
                    methodModifierList.add(Keyword.STATIC);
                }
                if (java.lang.reflect.Modifier.isFinal(methodModifiers)) {
                    methodModifierList.add(Keyword.FINAL);
                }
                if (java.lang.reflect.Modifier.isAbstract(methodModifiers)) {
                    methodModifierList.add(Keyword.ABSTRACT);
                }
                if (java.lang.reflect.Modifier.isNative(methodModifiers)) {
                    methodModifierList.add(Keyword.NATIVE);
                }
                if (java.lang.reflect.Modifier.isSynchronized(methodModifiers)) {
                    methodModifierList.add(Keyword.SYNCHRONIZED);
                }
                if (java.lang.reflect.Modifier.isTransient(methodModifiers)) {
                    methodModifierList.add(Keyword.TRANSIENT);
                }
                if (java.lang.reflect.Modifier.isVolatile(methodModifiers)) {
                    methodModifierList.add(Keyword.VOLATILE);
                }
                info.setMethodModifiers(methodModifierList);
            }
        } catch (Exception e) {
            // 反射失败时，使用默认值
        }
        
        // 提取参数类型信息
        if (paramTypes != null && !paramTypes.isEmpty()) {
            ParameterInfo paramInfo = extractParameterInfo(paramTypes);
            info.setFuncParams(paramInfo.getSimpleTypes());
            info.setFuncParamsPackageName(paramInfo.getPackageNames());
        }
        
        info.setFuncReturnPackageName(returnPackageName);
        info.setFuncReturnType(returnType);
        
        info.setFuncComment(""); // 叶节点没有注释信息（无法通过反射获取）
        node.setFuncInfo(info);
        
        // 设置循环调用标志
        node.setLoopCall(false);
        
        // 初始化子节点列表
        node.setChildren(new ArrayList<>());
        return node;
    }
    
    /**
     * 通过反射查找方法
     */
    private java.lang.reflect.Method findMethodByReflection(Class<?> clazz, String methodName, List<String> paramTypes) {
        try {
            if (paramTypes == null || paramTypes.isEmpty()) {
                return clazz.getMethod(methodName);
            }
            
            // 尝试匹配参数类型
            java.lang.reflect.Method[] methods = clazz.getMethods();
            for (java.lang.reflect.Method method : methods) {
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
     * 构建方法签名
     */
    private String buildMethodSignature(String className, String methodName, List<String> paramTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append(className).append("#").append(methodName);
        if (paramTypes != null && !paramTypes.isEmpty()) {
            sb.append("(").append(String.join(",", paramTypes)).append(")");
        } else {
            sb.append("()");
        }
        return sb.toString();
    }

    /**
     * 获取包名
     */
    private String getPackageName(String className) {
        if (className == null) {
            return "";
        }
        // 处理泛型类型，找到泛型参数的开始位置
        int genericStart = className.indexOf('<');
        // 找到最后一个点的位置，在泛型参数之前
        int lastDot = genericStart > 0 ? className.lastIndexOf('.', genericStart) : className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot) : "";
    }

    /**
     * 获取简单类名
     */
    private String getSimpleClassName(String className) {
        if (className == null) {
            return "";
        }
        // 找到最后一个点的位置
        int lastDot = className.lastIndexOf('.');
        // 提取类名部分
        String classNamePart = lastDot > 0 ? className.substring(lastDot + 1) : className;
        // 移除泛型参数
        int genericStart = classNamePart.indexOf('<');
        if (genericStart > 0) {
            classNamePart = classNamePart.substring(0, genericStart);
        }
        return classNamePart;
    }

    /**
     * 查找匹配的右括号位置
     */
    private int findMatchingBracket(String str, int startPos) {
        int count = 1;
        for (int i = startPos + 1; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '<') {
                count++;
            } else if (c == '>') {
                count--;
                if (count == 0) {
                    return i;
                }
            }
        }
        return str.length();
    }
    
    /**
     * 从导入语句中查找类的完整包名
     */
    private String findFullClassNameFromImports(String simpleClassName, MethodDeclaration method) {
        Optional<CompilationUnit> cuOpt = method.findCompilationUnit();
        if (cuOpt.isPresent()) {
            CompilationUnit cu = cuOpt.get();
            // 遍历所有导入语句
            for (ImportDeclaration importDecl : cu.getImports()) {
                String importName = importDecl.getNameAsString();
                if (importName.endsWith("." + simpleClassName)) {
                    return importName;
                }
            }
        }
        return null;
    }

    /**
     * 内部类：方法调用信息
     */
    private static class MethodCallInfo {
        private final String className;
        private final String realClassName;
        private final String methodName;
        private final List<String> paramTypes;

        public MethodCallInfo(String className, String realClassName, String methodName, List<String> paramTypes) {
            this.className = className;
            this.realClassName = realClassName;
            this.methodName = methodName;
            this.paramTypes = paramTypes;
        }

        public MethodCallInfo(String className, String methodName, List<String> paramTypes) {
            this(className, className, methodName, paramTypes);
        }

        public String getClassName() {
            return className;
        }

        public String getRealClassName() {
            return realClassName;
        }

        public String getMethodName() {
            return methodName;
        }

        public List<String> getParamTypes() {
            return paramTypes;
        }
    }

    /**
     * 内部类：参数信息
     */
    private static class ParameterInfo {
        private final List<String> simpleTypes;
        private final List<String> packageNames;

        public ParameterInfo(List<String> simpleTypes, List<String> packageNames) {
            this.simpleTypes = simpleTypes;
            this.packageNames = packageNames;
        }

        public List<String> getSimpleTypes() {
            return simpleTypes;
        }

        public List<String> getPackageNames() {
            return packageNames;
        }
    }

    /**
     * 内部类：返回值信息
     */
    private static class ReturnTypeInfo {
        private final String simpleType;
        private final String packageName;

        public ReturnTypeInfo(String simpleType, String packageName) {
            this.simpleType = simpleType;
            this.packageName = packageName;
        }

        public String getSimpleType() {
            return simpleType;
        }

        public String getPackageName() {
            return packageName;
        }
    }
}
