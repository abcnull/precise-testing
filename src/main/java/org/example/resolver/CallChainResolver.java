package org.example.resolver;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.example.node.AstNode;
import org.example.node.field.ClassDeclaration;
import org.example.node.field.ClassInfo;
import org.example.node.field.ClassOrigin;
import org.example.rule.CustomRule;
import org.example.rule.DangerModRule;
import org.example.node.field.FuncCate;
import org.example.node.field.FuncInfo;
import org.example.rule.IPreciseRule;
import org.example.rule.NormalRule;
import org.example.node.field.PackageInfo;
import org.example.rule.PreciseModel;
import org.example.rule.WarnModRule;

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
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
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
    private final Map<String, AstNode> nodeCache; // 节点缓存，key: 方法唯一标识，value: 对应的AstNode
    private final IPreciseRule preciseRule; // 精确规则，用于过滤调用链路

    public CallChainResolver(String sourceRootPath) {
        this(sourceRootPath, new NormalRule());
    }

    public CallChainResolver(String sourceRootPath, IPreciseRule preciseRule) {
        this.sourceRootPath = sourceRootPath;
        this.parsedFiles = new HashMap<>();
        this.visitedMethods = new HashSet<>();
        this.nodeCache = new HashMap<>();
        this.preciseRule = preciseRule;

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
    public AstNode resolveCallChain(String className, String methodName, List<String> paramTypes) {
        visitedMethods.clear();
        // 每次解析新的调用链时，清空节点缓存
        nodeCache.clear();

        // 获取或解析源文件，获取完整的参数类型
        CompilationUnit cu = getOrParseCompilationUnit(className);
        if (cu != null) {
            // 查找目标方法
            MethodDeclaration targetMethod = findMethodDeclaration(cu, methodName, paramTypes);
            if (targetMethod != null) {
                // 使用完整参数类型重新构建方法签名
                List<String> fullParamTypes = extractFullParamTypes(targetMethod);
                // 递归解析方法调用，初始层级为1
                return resolveMethodCall(className, className, methodName, fullParamTypes, 1);
            }
        }

        return resolveMethodCall(className, className, methodName, paramTypes, 1);
    }

    /**
     * 生成节点的唯一标识符
     * 基于方法名、参数类型、realClassName和realPackageName
     */
    private String generateNodeKey(String className, String realClassName, String methodName, List<String> paramTypes) {
        StringBuilder keyBuilder = new StringBuilder();
        // 添加声明类名（多态场景下的接口或父类）
        keyBuilder.append(getPackageName(className)).append(".");
        keyBuilder.append(getSimpleClassName(className)).append("#");
        // 添加真实类名
        keyBuilder.append(getPackageName(realClassName)).append(".");
        keyBuilder.append(getSimpleClassName(realClassName)).append(".");
        keyBuilder.append(methodName).append("(");
        if (paramTypes != null && !paramTypes.isEmpty()) {
            for (int i = 0; i < paramTypes.size(); i++) {
                if (i > 0)
                    keyBuilder.append(",");
                keyBuilder.append(paramTypes.get(i));
            }
        }
        keyBuilder.append(")");
        return keyBuilder.toString();
    }

    /**
     * 递归解析方法调用
     */
    private AstNode resolveMethodCall(String className, String realClassName, String methodName,
            List<String> paramTypes, int currentLayer) {
        // 生成唯一标识符用于循环检测
        String methodSignature = buildMethodSignature(className, methodName, paramTypes);

        // 检测循环调用
        if (visitedMethods.contains(methodSignature)) {
            return createCycleNode(className, realClassName, methodName, paramTypes);
        }

        // 生成节点唯一标识并检查缓存
        String nodeKey = generateNodeKey(className, realClassName, methodName, paramTypes);
        if (nodeCache.containsKey(nodeKey)) {
            AstNode cachedNode = nodeCache.get(nodeKey);
            // 只有非循环调用节点可以被复用
            if (!cachedNode.isLoopCall()) {
                return cachedNode;
            }
        }

        // 检查是否应该构造该节点
        if (!shouldCreateNode(className, realClassName)) {
            // 不应该构造节点，直接返回 null
            return null;
        }

        // 检查是否超过最大层数
        int maxLayer = getMaxLayer();
        if (currentLayer >= maxLayer) {
            // 达到最大层数，不继续递归，创建叶节点
            AstNode leafNode = createLeafNode(className, realClassName, methodName, paramTypes);
            if (!leafNode.isLoopCall()) {
                nodeCache.put(nodeKey, leafNode);
            }
            return leafNode;
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
                AstNode leafNode = createLeafNode(className, realClassName, methodName, paramTypes);
                // 只有非循环调用节点才加入缓存
                if (!leafNode.isLoopCall()) {
                    nodeCache.put(nodeKey, leafNode);
                }
                visitedMethods.remove(methodSignature); // 回溯：移除当前方法
                return leafNode;
            }
            targetMethod = findMethodDeclaration(cu, methodName, paramTypes);
            if (targetMethod == null) {
                // 无法找到方法，创建叶节点
                AstNode leafNode = createLeafNode(className, realClassName, methodName, paramTypes);
                // 只有非循环调用节点才加入缓存
                if (!leafNode.isLoopCall()) {
                    nodeCache.put(nodeKey, leafNode);
                }
                visitedMethods.remove(methodSignature); // 回溯：移除当前方法
                return leafNode;
            }
        }

        // 创建当前节点
        AstNode currentNode = new AstNode();

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

        // 初始化子节点列表和父节点列表
        currentNode.setChildren(new ArrayList<>());
        currentNode.setParents(new ArrayList<>());

        // 将当前节点加入缓存
        nodeCache.put(nodeKey, currentNode);

        // 查找方法体内的所有方法调用
        List<MethodCallInfo> methodCalls = extractMethodCalls(targetMethod);

        // 递归解析每个方法调用，深度优先搜索
        for (MethodCallInfo callInfo : methodCalls) {
            AstNode childNode = resolveMethodCall(
                    callInfo.getClassName(),
                    callInfo.getRealClassName(),
                    callInfo.getMethodName(),
                    callInfo.getParamTypes(),
                    currentLayer + 1);
            // 只有当子节点不为 null 时，才将其添加到父节点的 children 列表中
            if (childNode != null) {
                currentNode.getChildren().add(childNode);
                // 更新子节点的父节点列表
                if (childNode.getParents() == null) {
                    childNode.setParents(new ArrayList<>());
                }
                childNode.getParents().add(currentNode);
            }
        }

        visitedMethods.remove(methodSignature); // 回溯：移除当前方法
        return currentNode;
    }

    /**
     * 检查是否应该构造该节点
     * 
     * @param className     类名
     * @param realClassName 实际类名
     * @return 是否应该构造节点
     */
    private boolean shouldCreateNode(String className, String realClassName) {
        // 首先检查是否在抛出列表中
        if (isInThrownClasses(realClassName)) {
            return false;
        }

        // 然后检查是否在过滤列表中
        if (!isInFilterClasses(realClassName)) {
            return false;
        }

        // 最后根据模式检查类来源
        return shouldIncludeByModel(realClassName);
    }

    /**
     * 检查类是否在抛出列表中
     * 
     * @param className 类名
     * @return 是否在抛出列表中
     */
    private boolean isInThrownClasses(String className) {
        if (preciseRule instanceof NormalRule) {
            NormalRule rule = (NormalRule) preciseRule;
            List<String> thrownClasses = rule.getThrownClasses();
            return isClassInList(className, thrownClasses);
        } else if (preciseRule instanceof WarnModRule) {
            WarnModRule rule = (WarnModRule) preciseRule;
            List<String> thrownClasses = rule.getThrownClasses();
            return isClassInList(className, thrownClasses);
        } else if (preciseRule instanceof DangerModRule) {
            DangerModRule rule = (DangerModRule) preciseRule;
            List<String> thrownClasses = rule.getThrownClasses();
            return isClassInList(className, thrownClasses);
        } else if (preciseRule instanceof CustomRule) {
            CustomRule rule = (CustomRule) preciseRule;
            List<String> thrownClasses = rule.getThrownClasses();
            return isClassInList(className, thrownClasses);
        }
        return false;
    }

    /**
     * 检查类是否在过滤列表中
     * 
     * @param className 类名
     * @return 是否在过滤列表中
     */
    private boolean isInFilterClasses(String className) {
        if (preciseRule instanceof NormalRule) {
            NormalRule rule = (NormalRule) preciseRule;
            List<String> filterClasses = rule.getFilterClasses();
            return isClassInFilterList(className, filterClasses);
        } else if (preciseRule instanceof WarnModRule) {
            WarnModRule rule = (WarnModRule) preciseRule;
            List<String> filterClasses = rule.getFilterClasses();
            return isClassInFilterList(className, filterClasses);
        } else if (preciseRule instanceof DangerModRule) {
            DangerModRule rule = (DangerModRule) preciseRule;
            List<String> filterClasses = rule.getFilterClasses();
            return isClassInFilterList(className, filterClasses);
        } else if (preciseRule instanceof CustomRule) {
            CustomRule rule = (CustomRule) preciseRule;
            List<String> filterClasses = rule.getFilterClasses();
            return isClassInFilterList(className, filterClasses);
        }
        return true;
    }

    /**
     * 根据模式检查是否应该包含该类
     * 
     * @param className 类名
     * @return 是否应该包含
     */
    private boolean shouldIncludeByModel(String className) {
        if (preciseRule instanceof NormalRule) {
            NormalRule rule = (NormalRule) preciseRule;
            if (rule.getPreciseModel() == PreciseModel.NORMAL) {
                // NORMAL模式：只包含项目中的类
                CompilationUnit cu = getOrParseCompilationUnit(className);
                return cu != null;
            }
        } else if (preciseRule instanceof WarnModRule) {
            WarnModRule rule = (WarnModRule) preciseRule;
            if (rule.getPreciseModel() == PreciseModel.WARN_MOD) {
                // WARN_MOD模式：包含项目中的类和第三方依赖，不包含JDK类
                CompilationUnit cu = getOrParseCompilationUnit(className);
                if (cu != null) {
                    return true; // 项目中的类
                }
                // 检查是否是JDK类
                String packageName = getPackageName(className);
                return !packageName.startsWith("java.") && !packageName.startsWith("javax.");
            }
        } else if (preciseRule instanceof DangerModRule) {
            DangerModRule rule = (DangerModRule) preciseRule;
            if (rule.getPreciseModel() == PreciseModel.DANGER_MOD) {
                // DANGER_MOD模式：包含所有类
                return true;
            }
        } else if (preciseRule instanceof CustomRule) {
            CustomRule rule = (CustomRule) preciseRule;
            if (rule.getPreciseModel() == PreciseModel.NORMAL) {
                // NORMAL模式：只包含项目中的类
                CompilationUnit cu = getOrParseCompilationUnit(className);
                return cu != null;
            } else if (rule.getPreciseModel() == PreciseModel.WARN_MOD) {
                // WARN_MOD模式：包含项目中的类和第三方依赖，不包含JDK类
                CompilationUnit cu = getOrParseCompilationUnit(className);
                if (cu != null) {
                    return true; // 项目中的类
                }
                // 检查是否是JDK类
                String packageName = getPackageName(className);
                return !packageName.startsWith("java.") && !packageName.startsWith("javax.");
            } else if (rule.getPreciseModel() == PreciseModel.DANGER_MOD) {
                // DANGER_MOD模式：包含所有类
                return true;
            }
        }
        return true;
    }

    /**
     * 检查类是否在列表中（支持通配符）
     * 
     * @param className 类名
     * @param classList 类列表
     * @return 是否在列表中
     */
    private boolean isClassInList(String className, List<String> classList) {
        if (classList == null || classList.isEmpty()) {
            return false;
        }
        for (String pattern : classList) {
            if (matchesPattern(className, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查类是否在过滤列表中（支持通配符）
     * 
     * @param className  类名
     * @param filterList 过滤列表
     * @return 是否在过滤列表中
     */
    private boolean isClassInFilterList(String className, List<String> filterList) {
        if (filterList == null || filterList.isEmpty()) {
            return true; // 过滤列表为空，默认包含所有类
        }
        for (String pattern : filterList) {
            if (matchesPattern(className, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查类名是否匹配模式（支持通配符）
     * 
     * @param className 类名
     * @param pattern   模式
     * @return 是否匹配
     */
    private boolean matchesPattern(String className, String pattern) {
        if (StringUtils.isBlank(pattern) || StringUtils.isBlank(className))
            return false;

        // 如果模式不包含通配符，直接进行等值比较
        if (!pattern.contains("*"))
            return className.equals(pattern);

        // 将模式转换为正则表达式：点号作为字面量，* 转换为 .*
        String regex = "^" + pattern.replace(".", "\\.")
                .replace("*", ".*") + "$";

        return className.matches(regex);
    }

    public static void main(String[] args) {
        CallChainResolver resolver = new CallChainResolver("");
        System.out.println(resolver.matchesPattern("org.example.callchain2.AAA", "org.example.callchain*"));
    }

    /**
     * 从 preciseRule 获取最大层数
     * 
     * @return 最大层数
     */
    private int getMaxLayer() {
        if (preciseRule instanceof NormalRule) {
            return ((NormalRule) preciseRule).getMaxLayer();
        } else if (preciseRule instanceof WarnModRule) {
            return ((WarnModRule) preciseRule).getMaxLayer();
        } else if (preciseRule instanceof DangerModRule) {
            return ((DangerModRule) preciseRule).getMaxLayer();
        } else if (preciseRule instanceof CustomRule) {
            return ((CustomRule) preciseRule).getMaxLayer();
        }
        return Integer.MAX_VALUE;
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
    private AstNode createCycleNode(String className, String realClassName, String methodName,
            List<String> paramTypes) {
        AstNode cycleNode = new AstNode();

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

        // 初始化子节点列表和父节点列表
        cycleNode.setChildren(new ArrayList<>());
        cycleNode.setParents(new ArrayList<>());

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
                    // String declaringClass = resolvedMethod.getClassName();
                    String methodName = resolvedMethod.getName();

                    // 构建完整的类名（包含包名）
                    String fullDeclaringClass = resolvedMethod.getClassName();
                    String packageName = resolvedMethod.getPackageName();
                    if (!packageName.isEmpty()) {
                        fullDeclaringClass = packageName + "." + fullDeclaringClass;
                    }
                    String[] realFullDeclaringClass = { fullDeclaringClass }; // 默认为相同的类，使用数组包装

                    // 尝试解析作用域的实际类型（多态情况）
                    try {
                        if (callExpr.getScope().isPresent()) {
                            Expression scopeExpr = callExpr.getScope().get();
                            // 尝试解析变量声明，查找初始化表达式
                            if (scopeExpr.isNameExpr()) {
                                NameExpr nameExpr = scopeExpr.asNameExpr();
                                String variableName = nameExpr.getNameAsString();

                                // 在当前方法中查找变量声明
                                Optional<VariableDeclarationExpr> varDeclOpt = finalMethod
                                        .findAll(VariableDeclarationExpr.class)
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
                                                        ObjectCreationExpr creationExpr = initializer
                                                                .asObjectCreationExpr();
                                                        // 获取实际实例化的类型
                                                        String actualType = creationExpr.getType().toString();
                                                        // 构建完整的类名
                                                        String fullActualType;
                                                        if (actualType.contains(".")) {
                                                            // 已经是完整类名
                                                            fullActualType = actualType;
                                                        } else {
                                                            // 尝试从import语句中获取完整类名
                                                            String fullClassName = findFullClassNameFromImports(
                                                                    actualType, finalMethod);
                                                            if (fullClassName != null) {
                                                                fullActualType = fullClassName;
                                                            } else {
                                                                // 如果找不到import，使用当前包名
                                                                String initPackageName = finalMethod
                                                                        .findCompilationUnit()
                                                                        .flatMap(CompilationUnit::getPackageDeclaration)
                                                                        .map(pd -> pd.getNameAsString())
                                                                        .orElse("");
                                                                fullActualType = initPackageName.isEmpty() ? actualType
                                                                        : initPackageName + "." + actualType;
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

                    calls.add(
                            new MethodCallInfo(fullDeclaringClass, realFullDeclaringClass[0], methodName, paramTypes));
                } catch (Exception e) {
                    // 解析失败时，使用简单推断
                    String methodName = callExpr.getNameAsString();
                    String[] inferredClass = { inferClassNameFromCall(callExpr, finalMethod) }; // 使用数组包装
                    String[] realInferredClass = { inferredClass[0] }; // 默认为相同的类，使用数组包装

                    // 尝试解析作用域的实际类型（多态情况）
                    try {
                        if (callExpr.getScope().isPresent()) {
                            Expression scopeExpr = callExpr.getScope().get();
                            // 尝试解析变量声明，查找初始化表达式
                            if (scopeExpr.isNameExpr()) {
                                NameExpr nameExpr = scopeExpr.asNameExpr();
                                String variableName = nameExpr.getNameAsString();

                                // 在当前方法中查找变量声明
                                Optional<VariableDeclarationExpr> varDeclOpt = finalMethod
                                        .findAll(VariableDeclarationExpr.class)
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
                                                        String fullClassName = findFullClassNameFromImports(
                                                                variableType, finalMethod);
                                                        if (fullClassName != null) {
                                                            inferredClass[0] = fullClassName;
                                                        } else {
                                                            // 如果找不到import，使用当前包名
                                                            inferredClass[0] = packageName.isEmpty() ? variableType
                                                                    : packageName + "." + variableType;
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
                                                        ObjectCreationExpr creationExpr = initializer
                                                                .asObjectCreationExpr();
                                                        // 获取实际实例化的类型
                                                        String actualType = creationExpr.getType().toString();
                                                        // 构建完整的类名
                                                        String initPackageName = finalMethod.findCompilationUnit()
                                                                .flatMap(CompilationUnit::getPackageDeclaration)
                                                                .map(pd -> pd.getNameAsString())
                                                                .orElse("");
                                                        realInferredClass[0] = initPackageName.isEmpty() ? actualType
                                                                : initPackageName + "." + actualType;
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
                    ResolvedConstructorDeclaration resolvedConstructor = creationExpr
                            .resolve();
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

            final String[] scopeArray = { scopeExpr.toString() }; // 使用数组包装，以便在lambda中修改
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
                                        scopeArray[0] = packageName.isEmpty() ? variableType
                                                : packageName + "." + variableType;
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
            // 获取 int
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
    private AstNode createLeafNode(String className, String realClassName, String methodName, List<String> paramTypes) {
        AstNode node = new AstNode();

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
        // 尝试解析类文件，判断是否是项目内的类
        CompilationUnit cu = getOrParseCompilationUnit(className);
        if (cu != null) {
            // 如果有CompilationUnit，说明是项目内的类
            classInfo.setClassOrigin(ClassOrigin.PROJECT);
        } else if (packageName.startsWith("java.") || packageName.startsWith("javax.")) {
            // JDK类
            classInfo.setClassOrigin(ClassOrigin.JDK);
        } else {
            // 第三方依赖类
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
            if (Modifier.isPublic(classModifiers)) {
                classModifierList.add(Keyword.PUBLIC);
            }
            if (Modifier.isFinal(classModifiers)) {
                classModifierList.add(Keyword.FINAL);
            }
            if (Modifier.isAbstract(classModifiers)) {
                classModifierList.add(Keyword.ABSTRACT);
            }
            if (Modifier.isStatic(classModifiers)) {
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
                if (Modifier.isTransient(methodModifiers)) {
                    methodModifierList.add(Keyword.TRANSIENT);
                }
                if (Modifier.isVolatile(methodModifiers)) {
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

        // 叶节点没有子节点
        node.setChildren(new ArrayList<>());
        node.setParents(new ArrayList<>());

        return node;
    }

    /**
     * 合并两个方法调用有根DAG为一个更大的无环图
     * 
     * @param node1 第一个DAG的根节点
     * @param node2 第二个DAG的根节点
     */
    public void mergeCallChain(AstNode node1, AstNode node2) {
        // 如果两个节点相同，直接返回
        if (node1 == node2) {
            return;
        }

        // 遍历第一个DAG的所有节点，更新节点缓存
        updateNodeCache(node1);
        // 遍历第二个DAG的所有节点，更新节点缓存
        updateNodeCache(node2);
    }

    /**
     * 合并多个有根DAG为一个更大的有向无环图
     * @param nodes 多个有根DAG的根节点列表
     */
    public void mergeCallChain(List<AstNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        // 遍历所有DAG的根节点，更新节点缓存
        for (AstNode node : nodes) {
            if (node != null) {
                updateNodeCache(node);
            }
        }
    }

    /**
     * 检查两个AstNode是否相同（用于去重）
     * @param node1 第一个节点
     * @param node2 第二个节点
     * @return 是否相同
     */
    private boolean areNodesEqual(AstNode node1, AstNode node2) {
        if (node1 == node2) return true;
        if (node1 == null || node2 == null) return false;
        
        // 检查isLoopCall
        if (node1.isLoopCall() != node2.isLoopCall()) return false;
        
        // 检查PackageInfo
        PackageInfo pkg1 = node1.getPackageInfo();
        PackageInfo pkg2 = node2.getPackageInfo();
        if (pkg1 == null || pkg2 == null) return false;
        if (!Objects.equals(pkg1.getPackageName(), pkg2.getPackageName())) return false;
        if (!Objects.equals(pkg1.getRealPackageName(), pkg2.getRealPackageName())) return false;
        
        // 检查ClassInfo
        ClassInfo cls1 = node1.getClassInfo();
        ClassInfo cls2 = node2.getClassInfo();
        if (cls1 == null || cls2 == null) return false;
        if (!Objects.equals(cls1.getClassName(), cls2.getClassName())) return false;
        if (!Objects.equals(cls1.getRealClassName(), cls2.getRealClassName())) return false;
        
        // 检查FuncInfo
        FuncInfo func1 = node1.getFuncInfo();
        FuncInfo func2 = node2.getFuncInfo();
        if (func1 == null || func2 == null) return false;
        if (!Objects.equals(func1.getFuncName(), func2.getFuncName())) return false;
        if (!Objects.equals(func1.getFuncParams(), func2.getFuncParams())) return false;
        if (!Objects.equals(func1.getFuncParamsPackageName(), func2.getFuncParamsPackageName())) return false;
        
        return true;
    }

    /**
     * 检查节点是否是叶子节点
     * @param node 节点
     * @return 是否是叶子节点
     */
    private boolean isLeafNode(AstNode node) {
        if (node == null || node.isLoopCall()) {
            return false;
        }
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            return true;
        }
        // 检查是否所有子节点都是循环调用
        for (AstNode child : node.getChildren()) {
            if (!child.isLoopCall()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 查找所有叶子节点并去重
     * @param root 根节点
     * @return 去重后的叶子节点集合
     */
    public Set<AstNode> findLeaf(AstNode root) {
        Set<AstNode> leafNodes = new HashSet<>();
        Set<AstNode> visited = new HashSet<>();
        findLeafNodes(root, leafNodes, visited);
        return leafNodes;
    }

    /**
     * 递归查找叶子节点
     */
    private void findLeafNodes(AstNode node, Set<AstNode> leafNodes, Set<AstNode> visited) {
        if (node == null || visited.contains(node)) {
            return;
        }
        visited.add(node);
        
        if (isLeafNode(node)) {
            // 检查是否已存在相同的节点
            boolean exists = false;
            for (AstNode existingNode : leafNodes) {
                if (areNodesEqual(node, existingNode)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                leafNodes.add(node);
            }
        }
        
        // 递归处理子节点
        if (node.getChildren() != null) {
            for (AstNode child : node.getChildren()) {
                findLeafNodes(child, leafNodes, visited);
            }
        }
        
        visited.remove(node);
    }

    /**
     * 查找符合条件的叶子节点并去重
     * @param root 根节点
     * @param packageName 包名
     * @param realClassName 真实类名
     * @param funcName 方法名
     * @param params 方法参数
     * @return 符合条件的叶子节点集合
     */
    public Set<AstNode> findLeaf(AstNode root, String packageName, String realClassName, String funcName, List<String> params) {
        Set<AstNode> leafNodes = new HashSet<>();
        Set<AstNode> visited = new HashSet<>();
        findLeafNodesByCondition(root, packageName, realClassName, funcName, params, leafNodes, visited);
        return leafNodes;
    }

    /**
     * 递归查找符合条件的叶子节点
     */
    private void findLeafNodesByCondition(AstNode node, String packageName, String realClassName, String funcName, 
                                         List<String> params, Set<AstNode> leafNodes, 
                                         Set<AstNode> visited) {
        if (node == null || visited.contains(node)) {
            return;
        }
        visited.add(node);
        
        if (isLeafNode(node) && matchesCondition(node, packageName, realClassName, funcName, params)) {
            // 检查是否已存在相同的节点
            boolean exists = false;
            for (AstNode existingNode : leafNodes) {
                if (areNodesEqual(node, existingNode)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                leafNodes.add(node);
            }
        }
        
        // 递归处理子节点
        if (node.getChildren() != null) {
            for (AstNode child : node.getChildren()) {
                findLeafNodesByCondition(child, packageName, realClassName, funcName, params, leafNodes, visited);
            }
        }
        
        visited.remove(node);
    }

    /**
     * 查找符合条件的所有节点并去重
     * @param root 根节点
     * @param packageName 包名
     * @param realClassName 真实类名
     * @param funcName 方法名
     * @param params 方法参数
     * @return 符合条件的节点集合
     */
    public Set<AstNode> findNode(AstNode root, String packageName, String realClassName, String funcName, List<String> params) {
        Set<AstNode> nodes = new HashSet<>();
        Set<AstNode> visited = new HashSet<>();
        findNodesByCondition(root, packageName, realClassName, funcName, params, nodes, visited);
        return nodes;
    }

    /**
     * 递归查找符合条件的所有节点
     */
    private void findNodesByCondition(AstNode node, String packageName, String realClassName, String funcName, 
                                     List<String> params, Set<AstNode> nodes, 
                                     Set<AstNode> visited) {
        if (node == null || visited.contains(node)) {
            return;
        }
        visited.add(node);
        
        if (matchesCondition(node, packageName, realClassName, funcName, params)) {
            // 检查是否已存在相同的节点
            boolean exists = false;
            for (AstNode existingNode : nodes) {
                if (areNodesEqual(node, existingNode)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                nodes.add(node);
            }
        }
        
        // 递归处理子节点
        if (node.getChildren() != null) {
            for (AstNode child : node.getChildren()) {
                findNodesByCondition(child, packageName, realClassName, funcName, params, nodes, visited);
            }
        }
        
        visited.remove(node);
    }

    /**
     * 检查节点是否符合条件
     */
    private boolean matchesCondition(AstNode node, String packageName, String realClassName, String funcName, List<String> params) {
        if (node == null || node.getPackageInfo() == null || node.getClassInfo() == null || node.getFuncInfo() == null) {
            return false;
        }
        
        // 排除循环调用节点
        if (node.isLoopCall()) {
            return false;
        }
        
        // 检查包名
        String nodePackageName = node.getPackageInfo().getRealPackageName();
        if (packageName != null && !packageName.equals(nodePackageName)) {
            return false;
        }
        
        // 检查真实类名
        String nodeRealClassName = node.getClassInfo().getRealClassName();
        if (realClassName != null && !realClassName.equals(nodeRealClassName)) {
            return false;
        }
        
        // 检查方法名
        String nodeFuncName = node.getFuncInfo().getFuncName();
        if (funcName != null && !funcName.equals(nodeFuncName)) {
            return false;
        }
        
        // 检查参数
        java.util.List<String> nodeParams = node.getFuncInfo().getFuncParams();
        boolean paramsIsEmpty = (params == null || params.isEmpty());
        boolean nodeParamsIsEmpty = (nodeParams == null || nodeParams.isEmpty());
        
        // 如果一方为空，另一方不为空，返回 false
        if (paramsIsEmpty != nodeParamsIsEmpty) {
            return false;
        }
        
        // 如果都不为空，比较元素是否相等
        if (!paramsIsEmpty && !Objects.equals(params, nodeParams)) {
            return false;
        }
        
        return true;
    }

    /**
     * 生成从节点到出口节点的所有可能链路
     * @param node 起始节点
     * @return 链路字符串列表
     */
    public List<String> reverseCallChainStr(AstNode node) {
        List<String> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        
        // 生成节点的字符串表示
        String nodeStr = formatNodeToString(node);
        
        // 检查是否是出口节点（没有父节点）
        if (node.getParents() == null || node.getParents().isEmpty()) {
            result.add(nodeStr);
            return result;
        }
        
        // 递归处理每个父节点
        for (AstNode parent : node.getParents()) {
            // 避免循环
            if (parent.isLoopCall()) {
                continue;
            }
            
            // 递归获取父节点到出口节点的链路
            List<String> parentChains = reverseCallChainStr(parent);
            
            // 拼接当前节点到每条父节点链路
            for (String parentChain : parentChains) {
                result.add(nodeStr + "-->" + parentChain);
            }
        }
        
        return result;
    }

    /**
     * 将节点格式化为字符串
     * @param node 节点
     * @return 格式化后的字符串
     */
    private String formatNodeToString(AstNode node) {
        if (node == null || node.getPackageInfo() == null || node.getClassInfo() == null || node.getFuncInfo() == null) {
            return "";
        }
        
        // 构建包名和类名
        String packageName = node.getPackageInfo().getRealPackageName();
        String className = node.getClassInfo().getRealClassName();
        String fullClassName = packageName.isEmpty() ? className : packageName + "." + className;
        
        // 构建方法名和参数
        String methodName = node.getFuncInfo().getFuncName();
        java.util.List<String> params = node.getFuncInfo().getFuncParams();
        String paramsStr = params != null && !params.isEmpty() ? String.join(", ", params) : "";
        
        return fullClassName + "#" + methodName + "(" + paramsStr + ")";
    }

    /**
     * 获取从节点到出口节点的所有链路的出口节点信息
     * @param node 起始节点
     * @return 出口节点信息列表
     */
    public List<String> reverseLeavesName(AstNode node) {
        List<String> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        
        // 调用 reverseCallChainStr 获取所有链路
        List<String> chains = reverseCallChainStr(node);
        
        // 提取每个链路的出口节点
        for (String chain : chains) {
            // 按照 "-->" 切割链路
            String[] nodes = chain.split("-->+");
            if (nodes.length > 0) {
                // 取最后一个节点作为出口节点
                result.add(nodes[nodes.length - 1]);
            }
        }
        
        return result;
    }

    /**
     * 遍历DAG并更新节点缓存
     */
    private void updateNodeCache(AstNode node) {
        if (node == null || node.isLoopCall()) {
            return;
        }

        // 生成节点唯一标识
        String className = node.getClassInfo().getClassName();
        String realClassName = node.getClassInfo().getRealClassName();
        String packageName = node.getPackageInfo().getPackageName();
        String realPackageName = node.getPackageInfo().getRealPackageName();
        String fullClassName = packageName.isEmpty() ? className : packageName + "." + className;
        String fullRealClassName = realPackageName.isEmpty() ? realClassName : realPackageName + "." + realClassName;
        String methodName = node.getFuncInfo().getFuncName();
        List<String> paramTypes = node.getFuncInfo().getFuncParams();
        String nodeKey = generateNodeKey(fullClassName, fullRealClassName, methodName, paramTypes);

        // 如果缓存中已存在该节点，需要合并
        if (nodeCache.containsKey(nodeKey)) {
            AstNode cachedNode = nodeCache.get(nodeKey);
            // 合并子节点
            for (AstNode child : node.getChildren()) {
                if (!cachedNode.getChildren().contains(child)) {
                    cachedNode.getChildren().add(child);
                    // 更新子节点的父节点列表
                    if (child.getParents() == null) {
                        child.setParents(new ArrayList<>());
                    }
                    if (!child.getParents().contains(cachedNode)) {
                        child.getParents().add(cachedNode);
                    }
                }
            }
            // 合并父节点
            for (AstNode parent : node.getParents()) {
                if (!cachedNode.getParents().contains(parent)) {
                    cachedNode.getParents().add(parent);
                    // 更新父节点的子节点列表
                    if (!parent.getChildren().contains(cachedNode)) {
                        parent.getChildren().add(cachedNode);
                    }
                }
            }
        } else {
            // 将节点加入缓存
            nodeCache.put(nodeKey, node);
        }

        // 递归处理子节点（创建副本避免 ConcurrentModificationException）
        List<AstNode> childrenCopy = new ArrayList<>(node.getChildren());
        for (AstNode child : childrenCopy) {
            updateNodeCache(child);
        }
    }

    /**
     * 通过反射查找方法
     */
    private java.lang.reflect.Method findMethodByReflection(Class<?> clazz, String methodName,
            List<String> paramTypes) {
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
