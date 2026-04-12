package org.example.resolver.factory;

import java.util.ArrayList;
import java.util.List;

import org.example.node.DagNode;
import org.example.node.field.ClassInfo;
import org.example.node.field.FuncInfo;
import org.example.node.field.PackageInfo;
import org.example.resolver.extractor.ClassInfoExtractor;
import org.example.resolver.extractor.MethodInfoExtractor;
import org.example.resolver.extractor.PackageInfoExtractor;
import org.example.resolver.parser.ClassParser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

/**
 * 节点工厂类，用于创建 DAG 节点
 */
public class NodeFactory {

    private final PackageInfoExtractor packageInfoExtractor = new PackageInfoExtractor();
    private final ClassInfoExtractor classInfoExtractor = new ClassInfoExtractor();
    private final MethodInfoExtractor methodInfoExtractor = new MethodInfoExtractor();
    private final ClassParser classParser = new ClassParser();

    /**
     * 创建并初始化节点（不包括连接关系）
     * 
     * @param cu            译单元，包含类定义和方法定义
     * @param className     声明类名（多态场景下的接口或父类）
     * @param realClassName 真实类名（多态场景下的子类）
     * @param methodName    方法名
     * @param paramTypes    参数类型列表，比如 String, int
     * @return 初始化后的节点
     */
    public DagNode createAndInitializeNode(CompilationUnit cu, String className, String realClassName,
            String methodName, List<String> paramTypes) {
        // 创建当前节点
        DagNode currentNode = new DagNode();

        // 设置包信息
        PackageInfo packageInfo = packageInfoExtractor.extract(className, realClassName);
        currentNode.setPackageInfo(packageInfo);

        // 创建并设置 ClassInfo
        ClassInfo classInfo = classInfoExtractor.extract(cu, className, realClassName);
        currentNode.setClassInfo(classInfo);

        // 创建并设置FuncInfo
        MethodDeclaration method = classParser.parseOutMethodDeclaration(cu, methodName, paramTypes);
        FuncInfo funcInfo = methodInfoExtractor.extract(method, realClassName, methodName, paramTypes);
        currentNode.setFuncInfo(funcInfo);

        // 设置循环调用标志
        currentNode.setLoopCall(false);

        // 初始化子节点列表和父节点列表
        currentNode.setChildren(new ArrayList<>());
        currentNode.setParents(new ArrayList<>());

        return currentNode;
    }

    /**
     * 创建循环调用节点
     * 
     * @param cu            真实类的编译单元
     * @param className     全限定类名
     * @param realClassName 全限定真实类名（多态场景下真实的类）
     * @param methodName    方法名
     * @param paramTypes    参数类型列表
     * @return 循环调用节点
     */
    public DagNode createCycleNode(CompilationUnit cu, String className, String realClassName, String methodName,
            List<String> paramTypes) {
        // 创建并初始化循环调用节点
        DagNode cycleNode = createAndInitializeNode(cu, className, realClassName, methodName, paramTypes);

        // 设置循环调用标志
        cycleNode.setLoopCall(true);

        return cycleNode;
    }

    /**
     * 创建叶节点（用于无法解析的方法）
     * 
     * @param className     全限定类名
     * @param realClassName 全限定真实类名（多态场景下真实的类）
     * @param methodName    方法名
     * @param paramTypes    参数类型列表
     * @param cu            真实类的编译单元
     * @return 叶节点
     */
    public DagNode createLeafNode(CompilationUnit cu, String className, String realClassName, String methodName,
            List<String> paramTypes) {
        // 创建并初始化叶节点
        DagNode leafNode = createAndInitializeNode(cu, className, realClassName, methodName, paramTypes);

        // 设置循环调用标志
        leafNode.setLoopCall(false);

        return leafNode;
    }

}