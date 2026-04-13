package org.example.resolver;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.example.constant.PathConstant;
import org.example.node.DagNode;
import org.example.node.field.ClassInfo;
import org.example.node.field.FuncInfo;
import org.example.resolver.extractor.ClassInfoExtractor;
import org.example.resolver.extractor.MethodInfoExtractor;
import org.example.resolver.extractor.PackageInfoExtractor;
import org.example.resolver.factory.NodeFactory;
import org.example.resolver.guesser.ClassGuesser;
import org.example.resolver.guesser.ClassInfoGuesser;
import org.example.resolver.model.MethodBelongs2Class;
import org.example.resolver.model.MethodCallInfo;
import org.example.resolver.parser.ClassParser;
import org.example.resolver.parser.FileParser;
import org.example.resolver.parser.MethodParser;
import org.example.rule.IPreciseRule;
import org.example.rule.NormalRule;
import org.example.util.StringUtil;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

/**
 * 调用链解析器
 */
public class CallChainResolver {
    // 项目根目录，比如 /Users/abcnull/IdeaProjects/precise-testing/src/main/java
    private final String sourceRootPath;
    // 精确规则，用于过滤调用链路
    private final IPreciseRule preciseRule;

    // 节点工厂
    private final NodeFactory nodeFactory;

    // 节点缓存
    private final Map<String, DagNode> nodeCache; // 节点缓存，key: 方法唯一标识，value: 对应的 DagNode
    private final Set<String> visitedMethods; // 已访问的方法签名集合，用于判定是否循环调用
    private final Map<String, CompilationUnit> parsedFiles; // key: 类全限定名, value: 编译单元

    // 解析器
    private final JavaParser javaParser; // 用于解析Java源文件
    private final FileParser fileParser; // 文件解析器
    private final ClassParser classParser; // 方法解析器
    private final MethodParser methodParser; // 参数解析器

    // 抽取器
    private final PackageInfoExtractor packageInfoExtractor; // 包信息提取器
    private final ClassInfoExtractor classInfoExtractor; // 类信息提取器
    private final MethodInfoExtractor methodInfoExtractor; // 方法信息提取器

    // 猜测器
    private final ClassInfoGuesser classInfoGuesser; // 类信息猜测器

    public CallChainResolver(String sourceRootPath) {
        this(sourceRootPath, new NormalRule());
    }

    /**
     * 构造函数，初始化解析器组件
     * 
     * @param sourceRootPath 项目根目录，比如
     *                       /Users/abcnull/IdeaProjects/precise-testing/src/main/java
     * @param preciseRule    精确规则，用于过滤调用链路
     */
    public CallChainResolver(String sourceRootPath, IPreciseRule preciseRule) {
        this.sourceRootPath = sourceRootPath;
        this.parsedFiles = new HashMap<>();
        this.visitedMethods = new HashSet<>();
        this.nodeCache = new HashMap<>();
        this.preciseRule = preciseRule;

        // 配置类型解析器
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        // 添加JDK库
        combinedTypeSolver.add(new ReflectionTypeSolver());
        // 添加JavaParserTypeSolver来解析项目中的自定义类
        try {
            File sourceRoot = new File(sourceRootPath);
            if (!sourceRoot.exists()) {
                // 尝试使用src/main/java作为默认源码目录
                sourceRoot = new File(sourceRootPath, PathConstant.JAVA_SOURCE_DIR);
            }
            if (sourceRoot.exists()) {
                // 添加项目源代码
                combinedTypeSolver.add(new JavaParserTypeSolver(sourceRoot));
            }
        } catch (Exception e) {
            // 忽略异常，继续使用ReflectionTypeSolver
        }
        // 配置JavaParser
        ParserConfiguration config = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(combinedTypeSolver));
        this.javaParser = new JavaParser(config);

        this.fileParser = new FileParser(javaParser, sourceRootPath);
        this.classParser = new ClassParser();
        this.methodParser = new MethodParser();

        this.nodeFactory = new NodeFactory();
        this.packageInfoExtractor = new PackageInfoExtractor();
        this.classInfoExtractor = new ClassInfoExtractor();
        this.methodInfoExtractor = new MethodInfoExtractor();

        this.classInfoGuesser = new ClassInfoGuesser();
    }

    /**
     * 从指定方法开始解析调用链路
     *
     * @param className  类全限定名
     * @param methodName 方法名
     * @param paramTypes 参数类型列表，比如 String, int
     * @return DagNode 调用链树根节点
     */
    public DagNode resolveCallChain(String className, String methodName, List<String> paramTypes) {
        // 用于记录已访问的方法签名，循环调用时，避免重复解析相同方法
        visitedMethods.clear();
        // 每次解析新的调用链时，清空节点缓存
        nodeCache.clear();

        // 获取或解析源文件，获取完整的参数类型
        CompilationUnit cu = getOrParseCompilationUnit(className);
        if (cu != null) {
            // 查找目标方法
            MethodDeclaration targetMethod = classParser.parseOutMethodDeclaration(cu, methodName, paramTypes);
            if (targetMethod != null) {
                // 使用完整参数类型重新构建方法签名，含有包名
                List<String> fullParamTypes = methodParser.parseOutFullParamTypes(targetMethod);
                // 递归解析方法调用，初始层级为1
                return resolveMethodCall(className, className, methodName, fullParamTypes, 1);
            }
        }

        return resolveMethodCall(className, className, methodName, paramTypes, 1);
    }

    /**
     * 生成节点的唯一标识符
     * 基于方法名、参数类型、realClassName和realPackageName
     * 
     * @param className     类全限定名
     * @param realClassName 真实全限定类名（考虑多态）
     * @param methodName    方法名
     * @param paramTypes    参数类型列表，比如 String, int
     * @return 节点唯一标识符
     */
    private String generateNodeKey(String className, String realClassName, String methodName, List<String> paramTypes) {
        return StringUtil.buildMethodSignature(className, realClassName, methodName, paramTypes);
    }

    /**
     * 递归解析方法调用
     * 
     * @param className     类全限定名
     * @param realClassName 真实全限定类名（考虑多态）
     * @param methodName    方法名
     * @param paramTypes    参数类型列表，比如 String, int
     * @param currentLayer  当前递归层级
     * @return DagNode 调用链树节点
     */
    private DagNode resolveMethodCall(String className, String realClassName, String methodName,
            List<String> paramTypes, int currentLayer) {
        /* 检测基本的精准测试模式过滤 */
        // 检查是否应该构造该节点
        if (!preciseRule.shouldCreateNode(realClassName)) {
            // 不应该构造节点，直接返回 null
            return null;
        }

        // 生成唯一标识符用于循环检测
        String methodSignature = buildMethodSignature(className, realClassName, methodName, paramTypes);
        // 生成节点唯一标识并检查缓存
        String nodeKey = generateNodeKey(className, realClassName, methodName, paramTypes);

        // 编译单元
        CompilationUnit cu = getOrParseCompilationUnit(realClassName); // 真实类的 cu
        CompilationUnit parentCu = null; // 多态场景下方法不一定都来自 cu，也有可能来自 parentCu
        if (!realClassName.equals(className)) {
            parentCu = getOrParseCompilationUnit(className);
        }
        MethodBelongs2Class fromClass = classInfoGuesser.guessMethodFormClass(parentCu, parentCu,
                className, realClassName, methodName, paramTypes); // 猜测方法来源的类

        /* 检测是否达到最大深度 */
        // 检查是达到最大层数，创建叶子节点
        int maxLayer = preciseRule.getMaxLayer();
        if (currentLayer >= maxLayer) {
            // 如果有循环调用
            if (visitedMethods.contains(methodSignature)) {
                return nodeFactory.createCycleNode(cu, className, realClassName, methodName, paramTypes);
            }
            // 如果节点可以复用
            if (nodeCache.containsKey(nodeKey)) {
                // 节点复用
                DagNode cachedNode = getCachedNodeIfAvailable(nodeKey);
                if (cachedNode != null) {
                    return cachedNode;
                }
            }
            // 否则创建一个叶子节点返回
            DagNode leafNode = nodeFactory.createLeafNode(cu, className, realClassName, methodName, paramTypes);
            if (!leafNode.isLoopCall()) {
                nodeCache.put(nodeKey, leafNode);
            }
            return leafNode;
        }

        /* 检测是否循环调用节点 */
        // 检测循环调用
        if (visitedMethods.contains(methodSignature)) {
            // 创建循环调用节点
            return nodeFactory.createCycleNode(cu, className, realClassName, methodName, paramTypes);
        }
        // 检测完，没有循环调用就 add
        visitedMethods.add(methodSignature);

        /* 检测节点是否可以复用 */
        DagNode cachedNode = getCachedNodeIfAvailable(nodeKey);
        if (cachedNode != null) {
            return cachedNode;
        }

        /* 创建并初始化节点 */
        DagNode currentNode = nodeFactory.createAndInitializeNode(cu, className, realClassName, methodName, paramTypes);

        /* 递归解析方法调用，DFS */
        // 查找方法体内的所有方法调用
        MethodDeclaration targetMethod = classParser.parseOutMethodDeclaration(cu, methodName, paramTypes);
        List<MethodCallInfo> methodCalls = methodParser.parseOutMethodCalls(targetMethod);
        // 递归解析每个方法调用，深度优先搜索
        for (MethodCallInfo callInfo : methodCalls) {
            DagNode childNode = resolveMethodCall(
                    callInfo.getClassName(),
                    callInfo.getRealClassName(),
                    callInfo.getMethodName(),
                    callInfo.getParamTypes(),
                    currentLayer + 1);
            // 只有当子节点不为 null 时，才将其添加到父节点的 children 列表中
            if (childNode != null) {
                // currentNode 更新孩子节点列表
                currentNode.getChildren().add(childNode);
                // childNode 更新父节点列表
                if (childNode.getParents() == null) {
                    childNode.setParents(new ArrayList<>());
                }
                childNode.getParents().add(currentNode);
            }
        }

        /* 回溯：移除当前方法 */
        visitedMethods.remove(methodSignature); // 表示该节点要返回，访问记录中没有它
        return currentNode;
    }

    /**
     * 从缓存中获取节点（如果节点可以复用）
     * 
     * @param nodeKey 节点唯一标识符
     * @return Node if available, null otherwise
     */
    public DagNode getCachedNodeIfAvailable(String nodeKey) {
        // 节点可以复用，直接返回
        if (nodeCache.containsKey(nodeKey)) {
            // 节点复用
            DagNode cachedNode = nodeCache.get(nodeKey);
            // 只有非循环调用节点可以被复用
            if (!cachedNode.isLoopCall()) {
                return cachedNode;
            }
        }
        return null;
    }

    /**
     * 通过获取或解析CompilationUnit
     * 
     * @param className 全限定类名
     * @return CompilationUnit
     */
    private CompilationUnit getOrParseCompilationUnit(String className) {
        if (parsedFiles.containsKey(className)) {
            return parsedFiles.get(className);
        }

        // 调用ClassParser解析类文件
        CompilationUnit cu = fileParser.parseOutCompilationUnit(className);
        if (cu != null) {
            parsedFiles.put(className, cu);
        }
        return cu;
    }

    /**
     * 合并两个方法调用有根DAG为一个更大的无环图
     * 
     * @param node1 第一个DAG的根节点
     * @param node2 第二个DAG的根节点
     */
    public void mergeCallChain(DagNode node1, DagNode node2) {
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
     * 
     * @param nodes 多个有根DAG的根节点列表
     */
    public void mergeCallChain(List<DagNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        // 遍历所有DAG的根节点，更新节点缓存
        for (DagNode node : nodes) {
            if (node != null) {
                updateNodeCache(node);
            }
        }
    }

    /**
     * 检查节点是否是叶子节点
     * 
     * @param node 节点
     * @return 是否是叶子节点
     */
    private boolean isLeafNode(DagNode node) {
        if (node == null || node.isLoopCall()) {
            return false;
        }
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            return true;
        }
        // 检查是否所有子节点都是循环调用
        for (DagNode child : node.getChildren()) {
            if (!child.isLoopCall()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 查找所有叶子节点并去重
     * 
     * @param root 根节点
     * @return 去重后的叶子节点集合
     */
    public Set<DagNode> findLeaf(DagNode root) {
        Set<DagNode> leafNodes = new HashSet<>();
        Set<DagNode> visited = new HashSet<>();
        findLeafNodes(root, leafNodes, visited);
        return leafNodes;
    }

    /**
     * 递归查找叶子节点
     */
    private void findLeafNodes(DagNode node, Set<DagNode> leafNodes, Set<DagNode> visited) {
        if (node == null || visited.contains(node)) {
            return;
        }
        visited.add(node);

        if (isLeafNode(node)) {
            // 检查是否已存在相同的节点
            boolean exists = false;
            for (DagNode existingNode : leafNodes) {
                if (node.equals(existingNode)) {
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
            for (DagNode child : node.getChildren()) {
                findLeafNodes(child, leafNodes, visited);
            }
        }

        visited.remove(node);
    }

    /**
     * 查找符合条件的叶子节点并去重
     * 
     * @param root          根节点
     * @param packageName   包名
     * @param realClassName 真实类名
     * @param funcName      方法名
     * @param params        方法参数
     * @return 符合条件的叶子节点集合
     */
    public Set<DagNode> findLeaf(DagNode root, String packageName, String realClassName, String funcName,
            List<String> params) {
        Set<DagNode> leafNodes = new HashSet<>();
        Set<DagNode> visited = new HashSet<>();
        findLeafNodesByCondition(root, packageName, realClassName, funcName, params, leafNodes, visited);
        return leafNodes;
    }

    /**
     * 递归查找符合条件的叶子节点
     */
    private void findLeafNodesByCondition(DagNode node, String packageName, String realClassName, String funcName,
            List<String> params, Set<DagNode> leafNodes,
            Set<DagNode> visited) {
        if (node == null || visited.contains(node)) {
            return;
        }
        visited.add(node);

        if (isLeafNode(node) && matchesCondition(node, packageName, realClassName, funcName, params)) {
            // 检查是否已存在相同的节点
            boolean exists = false;
            for (DagNode existingNode : leafNodes) {
                if (node.equals(existingNode)) {
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
            for (DagNode child : node.getChildren()) {
                findLeafNodesByCondition(child, packageName, realClassName, funcName, params, leafNodes, visited);
            }
        }

        visited.remove(node);
    }

    /**
     * 查找符合条件的所有节点并去重
     * 
     * @param root          根节点
     * @param packageName   包名
     * @param realClassName 真实类名
     * @param funcName      方法名
     * @param params        方法参数
     * @return 符合条件的节点集合
     */
    public Set<DagNode> findNode(DagNode root, String packageName, String realClassName, String funcName,
            List<String> params) {
        Set<DagNode> nodes = new HashSet<>();
        Set<DagNode> visited = new HashSet<>();
        findNodesByCondition(root, packageName, realClassName, funcName, params, nodes, visited);
        return nodes;
    }

    /**
     * 递归查找符合条件的所有节点
     */
    private void findNodesByCondition(DagNode node, String packageName, String realClassName, String funcName,
            List<String> params, Set<DagNode> nodes,
            Set<DagNode> visited) {
        if (node == null || visited.contains(node)) {
            return;
        }
        visited.add(node);

        if (matchesCondition(node, packageName, realClassName, funcName, params)) {
            // 检查是否已存在相同的节点
            boolean exists = false;
            for (DagNode existingNode : nodes) {
                if (node.equals(existingNode)) {
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
            for (DagNode child : node.getChildren()) {
                findNodesByCondition(child, packageName, realClassName, funcName, params, nodes, visited);
            }
        }

        visited.remove(node);
    }

    /**
     * 检查节点是否符合条件
     */
    private boolean matchesCondition(DagNode node, String packageName, String realClassName, String funcName,
            List<String> params) {
        if (node == null || node.getPackageInfo() == null || node.getClassInfo() == null
                || node.getFuncInfo() == null) {
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
     * 
     * @param node 起始节点
     * @return 链路字符串列表
     */
    public List<String> reverseCallChainStr(DagNode node) {
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
        for (DagNode parent : node.getParents()) {
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
     * 
     * @param node 节点
     * @return 格式化后的字符串
     */
    private String formatNodeToString(DagNode node) {
        if (node == null || node.getPackageInfo() == null || node.getClassInfo() == null
                || node.getFuncInfo() == null) {
            return "";
        }

        // 构建包名和类名
        String packageName = node.getPackageInfo().getRealPackageName();
        String className = node.getClassInfo().getRealClassName();
        String fullClassName = packageName.isEmpty() ? className : packageName + PathConstant.POINT + className;

        // 构建方法名和参数
        String methodName = node.getFuncInfo().getFuncName();
        java.util.List<String> params = node.getFuncInfo().getFuncParams();
        String paramsStr = params != null && !params.isEmpty() ? String.join(", ", params) : "";

        return fullClassName + "#" + methodName + "(" + paramsStr + ")";
    }

    /**
     * 获取从节点到出口节点的所有链路的出口节点信息
     * 
     * @param node 起始节点
     * @return 出口节点信息列表
     */
    public List<String> reverseLeavesName(DagNode node) {
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
    private void updateNodeCache(DagNode node) {
        if (node == null || node.isLoopCall()) {
            return;
        }

        // 生成节点唯一标识
        String className = node.getClassInfo().getClassName();
        String realClassName = node.getClassInfo().getRealClassName();
        String packageName = node.getPackageInfo().getPackageName();
        String realPackageName = node.getPackageInfo().getRealPackageName();
        String fullClassName = packageName.isEmpty() ? className : packageName + PathConstant.POINT + className;
        String fullRealClassName = realPackageName.isEmpty() ? realClassName
                : realPackageName + PathConstant.POINT + realClassName;
        String methodName = node.getFuncInfo().getFuncName();
        List<String> paramTypes = node.getFuncInfo().getFuncParams();
        String nodeKey = generateNodeKey(fullClassName, fullRealClassName, methodName, paramTypes);

        // 如果缓存中已存在该节点，需要合并
        if (nodeCache.containsKey(nodeKey)) {
            DagNode cachedNode = nodeCache.get(nodeKey);
            // 合并子节点
            for (DagNode child : node.getChildren()) {
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
            for (DagNode parent : node.getParents()) {
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
        List<DagNode> childrenCopy = new ArrayList<>(node.getChildren());
        for (DagNode child : childrenCopy) {
            updateNodeCache(child);
        }
    }

    /**
     * 构建方法签名
     * 
     * @param className     声明类名（多态场景下的接口或父类）
     * @param realClassName 真实类名（多态场景下的子类）
     * @param methodName    方法名
     * @param paramTypes    参数类型列表，比如 String, int
     * @return 方法签名
     */
    private String buildMethodSignature(String className, String realClassName, String methodName,
            List<String> paramTypes) {
        return StringUtil.buildMethodSignature(className, realClassName, methodName, paramTypes);
    }

    /**
     * 打印 DAG 树（从当前节点向下遍历子节点）
     * 
     * @param node 起始节点
     */
    public void printTree(DagNode node) {
        printTreeRecursive(node, 0, true, "");
    }

    /**
     * 递归打印 DAG 树
     */
    private void printTreeRecursive(DagNode node, int depth, boolean isLast, String prefix) {
        if (node == null || node.getFuncInfo() == null) {
            return;
        }

        // 构建当前节点的连接线
        String connector = depth == 0 ? "" : (isLast ? "└── " : "├── ");
        String currentPrefix = prefix + connector;

        // 方法签名
        String methodSignature = buildMethodSignatureForPrint(node);
        System.out.println(currentPrefix + methodSignature);

        // 递归打印子节点
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            int childCount = node.getChildren().size();
            for (int i = 0; i < childCount; i++) {
                boolean childIsLast = (i == childCount - 1);
                String childPrefix = prefix + (isLast ? "    " : "│   ");
                printTreeRecursive(node.getChildren().get(i), depth + 1, childIsLast, childPrefix);
            }
        }
    }

    /**
     * 打印反向 DAG 树（从当前节点向上遍历父节点）
     * 
     * @param node 起始节点
     */
    public void printReverseTree(DagNode node) {
        printReverseTreeRecursive(node, 0, true, "");
    }

    /**
     * 递归打印反向 DAG 树
     */
    private void printReverseTreeRecursive(DagNode node, int depth, boolean isLast, String prefix) {
        if (node == null || node.getFuncInfo() == null) {
            return;
        }

        // 构建当前节点的连接线
        String connector = depth == 0 ? "" : (isLast ? "└── " : "├── ");
        String currentPrefix = prefix + connector;

        // 方法签名
        String methodSignature = buildMethodSignatureForPrint(node);
        System.out.println(currentPrefix + methodSignature);

        // 递归打印父节点
        if (node.getParents() != null && !node.getParents().isEmpty()) {
            int parentCount = node.getParents().size();
            for (int i = 0; i < parentCount; i++) {
                boolean parentIsLast = (i == parentCount - 1);
                String parentPrefix = prefix + (isLast ? "    " : "│   ");
                printReverseTreeRecursive(node.getParents().get(i), depth + 1, parentIsLast, parentPrefix);
            }
        }
    }

    /**
     * 构建方法签名字符串（用于打印）
     */
    private String buildMethodSignatureForPrint(DagNode node) {
        if (node == null || node.getFuncInfo() == null) {
            return "";
        }

        FuncInfo info = node.getFuncInfo();
        ClassInfo classInfo = node.getClassInfo();
        StringBuilder sb = new StringBuilder();

        // 方法修饰符
        if (info.getMethodModifiers() != null && !info.getMethodModifiers().isEmpty()) {
            for (com.github.javaparser.ast.Modifier.Keyword keyword : info.getMethodModifiers()) {
                sb.append(keyword.asString()).append(" ");
            }
        }

        // 返回值类型
        String returnType = info.getFuncReturnType();
        String returnPackage = info.getFuncReturnPackageName();
        if (!"void".equals(returnType) && returnPackage != null && !returnPackage.isEmpty()) {
            sb.append(returnPackage).append(PathConstant.POINT).append(returnType);
        } else {
            sb.append(returnType);
        }
        sb.append(" ");

        // 包名.类名
        String packageName = "";
        if (node.getPackageInfo() != null) {
            packageName = node.getPackageInfo().getPackageName();
        }
        if (packageName != null && !packageName.isEmpty()) {
            sb.append(packageName).append(".");
        }
        if (classInfo != null) {
            sb.append(classInfo.getClassName()).append("#");
        }

        // 方法名
        sb.append(info.getFuncName());

        // 参数列表
        sb.append(PathConstant.LEFT_BRACKET);
        if (info.getFuncParams() != null && !info.getFuncParams().isEmpty()) {
            List<String> fullParams = new ArrayList<>();
            List<String> params = info.getFuncParams();
            List<String> paramPackages = info.getFuncParamsPackageName();

            for (int i = 0; i < params.size(); i++) {
                String param = params.get(i);
                String paramPackage = (paramPackages != null && i < paramPackages.size()
                        && paramPackages.get(i) != null)
                                ? paramPackages.get(i)
                                : "";

                if (!paramPackage.isEmpty()) {
                    fullParams.add(paramPackage + "." + param);
                } else {
                    fullParams.add(param);
                }
            }
            sb.append(String.join(", ", fullParams));
        }
        sb.append(")");

        // 循环调用标记
        if (node.isLoopCall()) {
            sb.append(" [循环调用]");
        }

        return sb.toString();
    }
}
