package org.example.resolver.find;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.example.constant.PathConstant;
import org.example.node.DagNode;
import org.example.resolver.model.MethodBelongs2Class;
import org.example.resolver.model.MethodCallInfo;

import com.google.common.base.Objects;

/**
 * 查找遍历 DAG
 */
public class TraverseDag {
    /**
     * 递归先序遍历 Demo
     */
    public void preOrderRecursive(DagNode node) {
        if (node == null) {
            return;
        }

        // 这里可对 node 进行操作

        if (node.getChildren() != null) {
            for (DagNode child : node.getChildren()) {
                preOrderRecursive(child);
            }
        }
    }

    /**
     * 递归后序遍历 Demo
     */
    public void postOrderRecursive(DagNode node) {
        if (node == null) {
            return;
        }

        if (node.getChildren() != null) {
            for (DagNode child : node.getChildren()) {
                postOrderRecursive(child);
            }
        }

        // 这里可对 node 进行操作
    }

    /**
     * 查找特定节点
     * 通过传入 methodCallInfos，依据类名，方法名，方法参数相匹配的原则，从 DAG 中找出所有满足的节点，最后返回这些节点的集合
     * 注意：是从 DagNode 自上而下找孩子节点，不会往上去找父节点
     * 
     * @param root            根节点
     * @param methodCallInfos 方法调用信息列表
     * @return 找到匹配上 methodCallInfos 的特定节点集合
     */
    public Set<DagNode> findDownSpecificNodes(DagNode root, List<MethodCallInfo> methodCallInfos) {
        // transform
        List<MethodCallInfo> bakList = new ArrayList<>();
        for (MethodCallInfo callInfo : methodCallInfos) {
            if (callInfo == null) {
                continue;
            }
            String myMethodName = callInfo.getMethodName(); // 方法名
            String myDeclClassName = callInfo.getClassName(); // 节点声明类名
            if (myDeclClassName != null) {
                if (myDeclClassName.contains(PathConstant.DOT)) {
                    myDeclClassName = myDeclClassName.substring(myDeclClassName.lastIndexOf(PathConstant.DOT));
                }
                if (myDeclClassName.contains(PathConstant.HYP_DOLLAR)) {
                    myDeclClassName = myDeclClassName.substring(myDeclClassName.lastIndexOf(PathConstant.HYP_DOLLAR));
                }
            }
            String myRealClassName = callInfo.getRealClassName(); // 节点实现类名
            if (myRealClassName != null) {
                if (myRealClassName.contains(PathConstant.DOT)) {
                    myRealClassName = myRealClassName.substring(myRealClassName.lastIndexOf(PathConstant.DOT));
                }
                if (myRealClassName.contains(PathConstant.HYP_DOLLAR)) {
                    myRealClassName = myRealClassName.substring(myRealClassName.lastIndexOf(PathConstant.HYP_DOLLAR));
                }
            }
            List<String> myFuncParams = callInfo.getParamTypes(); // 节点方法参数
            List<String> myBakFuncParams = new ArrayList<>();
            if (myFuncParams != null) {
                for (String param : myFuncParams) {
                    if (param != null && param.contains(PathConstant.DOT)) {
                        param = param.substring(param.lastIndexOf(PathConstant.DOT));
                    }
                    if (param != null && param.contains(PathConstant.HYP_DOLLAR)) {
                        param = param.substring(param.lastIndexOf(PathConstant.HYP_DOLLAR));
                    }
                    myBakFuncParams.add(param);
                }
            }

            // check
            if (!checkMethodCallInfosValid(bakList)) {
                continue;
            }

            bakList.add(new MethodCallInfo(myDeclClassName, myRealClassName, myMethodName, myBakFuncParams));
        }

        Set<DagNode> nodes = new HashSet<>();
        Set<DagNode> visited = new HashSet<>();
        findDownSpecificNodesRecursive(root, bakList, nodes, visited);
        return nodes;
    }

    /**
     * 检查方法调用信息是否有效
     * 
     * @param methodCallInfos 方法调用信息列表
     * @return 是否有效
     */
    private boolean checkMethodCallInfosValid(List<MethodCallInfo> methodCallInfos) {
        if (CollectionUtils.isEmpty(methodCallInfos)) {
            return false;
        }
        for (MethodCallInfo callInfo : methodCallInfos) {
            if (callInfo == null) {
                return false;
            }
            if (StringUtils.isBlank(callInfo.getMethodName())) {
                return false;
            }
            if (StringUtils.isBlank(callInfo.getRealClassName())) {
                return false;
            }
            if (!CollectionUtils.isEmpty(callInfo.getParamTypes())) {
                for (String param : callInfo.getParamTypes()) {
                    if (StringUtils.isBlank(param)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * 递归查找匹配方法名的节点
     * 
     * @param node            当前节点
     * @param methodCallInfos 方法调用信息
     * @param nodes           存储找到的特定节点集合
     * @param visited         已访问节点集合
     */
    private void findDownSpecificNodesRecursive(DagNode node, List<MethodCallInfo> methodCallInfos,
            Set<DagNode> nodes, Set<DagNode> visited) {
        if (node == null || visited.contains(node)) {
            return;
        }
        visited.add(node);

        if (node.getFuncInfo() != null && node.getClassInfo() != null && !node.isLoopCall()) {
            // 遍历的节点
            String methodName = node.getFuncInfo().getFuncName(); // 节点方法名
            String declClassName = node.getClassInfo().getDeclClassName(); // 节点声明类名
            declClassName = declClassName == null ? declClassName
                    : declClassName.substring(declClassName.lastIndexOf(PathConstant.HYP_DOLLAR));
            String realClassName = node.getClassInfo().getRealClassName(); // 节点实现类名
            realClassName = realClassName == null ? realClassName
                    : realClassName.substring(realClassName.lastIndexOf(PathConstant.HYP_DOLLAR));
            MethodBelongs2Class fromClass = node.getClassInfo().getFromClass(); // 节点方法所属的类

            // 遍历传入的 methodCallInfos 找到匹配上的节点
            for (MethodCallInfo callInfo : methodCallInfos) {
                if (callInfo == null) {
                    continue;
                }
                // 匹配到类
                if ((Objects.equal(callInfo.getRealClassName(), realClassName) && (fromClass == null
                        || fromClass == MethodBelongs2Class.REAL_CLASS || fromClass == MethodBelongs2Class.UNKNOWN)) ||

                        (Objects.equal(callInfo.getRealClassName(), declClassName)
                                && (fromClass == null || fromClass == MethodBelongs2Class.DECL_CLASS
                                        || fromClass == MethodBelongs2Class.UNKNOWN))) {
                    // 匹配方法
                    if (Objects.equal(callInfo.getMethodName(), methodName)) {
                        // 匹配参数
                        boolean isParamsMatch = true;
                        int callInfoParamsCount = CollectionUtils.isEmpty(callInfo.getParamTypes()) ? 0
                                : callInfo.getParamTypes().size();
                        int nodeParamsCount = CollectionUtils.isEmpty(node.getFuncInfo().getFuncParams()) ? 0
                                : node.getFuncInfo().getFuncParams().size();
                        if (callInfoParamsCount != nodeParamsCount) {
                            continue;
                        }
                        if (callInfoParamsCount == nodeParamsCount && callInfoParamsCount == 0) {
                            nodes.add(node);
                            break;
                        }
                        for (int i = 0; i < callInfoParamsCount; i++) {
                            String nodeParam = node.getFuncInfo().getFuncParams().get(i);
                            nodeParam = nodeParam.substring(nodeParam.lastIndexOf(PathConstant.HYP_DOLLAR));
                            if (!Objects.equal(callInfo.getParamTypes().get(i), nodeParam)) {
                                isParamsMatch = false;
                                break;
                            }
                        }
                        if (isParamsMatch) {
                            nodes.add(node);
                            break;
                        }
                    }
                }
            }
        }

        if (node.getChildren() != null) {
            for (DagNode child : node.getChildren()) {
                findDownSpecificNodesRecursive(child, methodCallInfos, nodes, visited);
            }
        }
    }

    /**
     * 从指定节点向上查找所有最顶层的根父节点（根父节点，即其再没有任何父节点）
     * 
     * @param specificNode 起始节点
     * @return 所有最顶层的根父节点集合
     */
    public Set<DagNode> findUpRootsFromSpecificNode(DagNode specificNode) {
        Set<DagNode> roots = new HashSet<>();
        Set<DagNode> visited = new HashSet<>();
        findUpRootsRecursive(specificNode, roots, visited);
        return roots;
    }

    /**
     * 递归向上查找根父节点
     */
    private void findUpRootsRecursive(DagNode node, Set<DagNode> roots, Set<DagNode> visited) {
        if (node == null || visited.contains(node)) {
            return;
        }
        visited.add(node);

        if (node.getParents() == null || node.getParents().isEmpty()) {
            roots.add(node);
            return;
        }

        for (DagNode parent : node.getParents()) {
            findUpRootsRecursive(parent, roots, visited);
        }
    }

    /**
     * 从指定节点的根父节点，最终构成 map
     * map.key = 指定节点 node
     * map.value = 指定节点 node 的所有最顶层的根父节点集合 roots
     * 
     * @param root            起始节点
     * @param methodCallInfos 方法调用信息列表
     * @return 所有指定节点的根父节点映射
     */
    public Map<DagNode, Set<DagNode>> findSpecific2RootMap(DagNode root, List<MethodCallInfo> methodCallInfos) {
        if (root == null || CollectionUtils.isEmpty(methodCallInfos)) {
            return null;
        }

        // 找到所有指定的节点
        Set<DagNode> nodes = findDownSpecificNodes(root, methodCallInfos);
        if (CollectionUtils.isEmpty(nodes)) {
            return null;
        }

        // 找到所有指定节点的根父节点
        Map<DagNode, Set<DagNode>> resultMap = new HashMap<>();
        for (DagNode node : nodes) {
            Set<DagNode> roots = findUpRootsFromSpecificNode(node);
            if (CollectionUtils.isEmpty(roots)) {
                continue;
            }
            resultMap.put(node, roots);
        }

        return resultMap;
    }

    /**
     * 根据 DAG 中的任意节点，找到整个 DAG 中所有的根节点
     * 根节点特点：没有父节点，但有子节点
     * 
     * @param anyNode DAG中的任意节点
     * @return 整个DAG中所有的根节点集合
     */
    public Set<DagNode> findAllRoots(DagNode anyNode) {
        if (anyNode == null) {
            return new HashSet<>();
        }

        Set<DagNode> allNodes = new HashSet<>();
        Set<DagNode> visited = new HashSet<>();

        traverseAllNodes(anyNode, allNodes, visited);

        Set<DagNode> roots = new HashSet<>();
        for (DagNode node : allNodes) {
            boolean hasNoParents = node.getParents() == null || node.getParents().isEmpty();
            boolean hasChildren = node.getChildren() != null && !node.getChildren().isEmpty();
            if (hasNoParents && hasChildren) {
                roots.add(node);
            }
        }

        return roots;
    }

    /**
     * 遍历整个DAG的所有节点（向上和向下）
     */
    private void traverseAllNodes(DagNode node, Set<DagNode> allNodes, Set<DagNode> visited) {
        if (node == null || visited.contains(node)) {
            return;
        }
        visited.add(node);
        allNodes.add(node);

        if (node.getParents() != null) {
            for (DagNode parent : node.getParents()) {
                traverseAllNodes(parent, allNodes, visited);
            }
        }

        if (node.getChildren() != null) {
            for (DagNode child : node.getChildren()) {
                traverseAllNodes(child, allNodes, visited);
            }
        }
    }

}
