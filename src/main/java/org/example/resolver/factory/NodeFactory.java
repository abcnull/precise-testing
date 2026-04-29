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
import org.example.resolver.model.MethodBelongs2Class;
import org.example.resolver.parser.CompilationUnitParser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

/**
 * 节点工厂类，用于创建 DAG 节点
 */
public class NodeFactory {

    private PackageInfoExtractor packageInfoExtractor;
    private ClassInfoExtractor classInfoExtractor;
    private MethodInfoExtractor methodInfoExtractor;
    private CompilationUnitParser classParser;

    private PackageInfoExtractor getPackageInfoExtractor() {
        if (packageInfoExtractor == null) {
            packageInfoExtractor = new PackageInfoExtractor();
        }
        return packageInfoExtractor;
    }

    private ClassInfoExtractor getClassInfoExtractor() {
        if (classInfoExtractor == null) {
            classInfoExtractor = new ClassInfoExtractor();
        }
        return classInfoExtractor;
    }

    private MethodInfoExtractor getMethodInfoExtractor() {
        if (methodInfoExtractor == null) {
            methodInfoExtractor = new MethodInfoExtractor();
        }
        return methodInfoExtractor;
    }

    private CompilationUnitParser getClassParser() {
        if (classParser == null) {
            classParser = new CompilationUnitParser();
        }
        return classParser;
    }

    /**
     * 创建并初始化节点（不包括连接关系）
     * 
     * @param parentCu      父类的编译单元
     * @param cu            编译单元，包含类定义和方法定义
     * @param fromClass     方法所属的类
     * @param className     声明类名（多态场景下的接口或父类）
     * @param realClassName 真实类名（多态场景下的子类）
     * @param methodName    方法名
     * @param paramTypes    参数类型列表，比如 String, int
     * @return 初始化后的节点
     */
    public DagNode createAndInitializeNode(CompilationUnit parentCu, CompilationUnit cu,
            MethodBelongs2Class fromClass,
            String className, String realClassName,
            String methodName, List<String> paramTypes) {
        // 选择编译单元进行抽取数据
        CompilationUnit targetCu = cu;
        String targetClassName = realClassName;
        if (fromClass == MethodBelongs2Class.REAL_CLASS) {
            targetCu = cu;
            targetClassName = realClassName;
        } else if (fromClass == MethodBelongs2Class.DECL_CLASS) {
            targetCu = parentCu;
            targetClassName = className;
        }

        // 创建当前节点
        DagNode currentNode = new DagNode();

        // 设置包信息
        PackageInfo packageInfo = getPackageInfoExtractor().extract(className, realClassName);
        currentNode.setPackageInfo(packageInfo);

        // 创建并设置 ClassInfo
        ClassInfo classInfo = getClassInfoExtractor().extract(targetCu, fromClass, className, realClassName);
        currentNode.setClassInfo(classInfo);

        // 从目标 cu 中解析出唯一的 method，并且 paramTypes 如果有 UNKNOWN 会被修正
        MethodDeclaration method = getClassParser().parseOutMethodDeclaration(targetCu, fromClass, methodName, paramTypes);

        // 创建并设置 FuncInfo
        FuncInfo funcInfo = getMethodInfoExtractor().extract(method, targetClassName, fromClass, methodName, paramTypes);
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
     * @param parentCu      父类的编译单元
     * @param cu            真实类的编译单元
     * @param fromClass     方法所属的类
     * @param className     全限定类名
     * @param realClassName 全限定真实类名（多态场景下真实的类）
     * @param methodName    方法名
     * @param paramTypes    参数类型列表
     * @return 循环调用节点
     */
    public DagNode createCycleNode(CompilationUnit parentCu, CompilationUnit cu, MethodBelongs2Class fromClass,
            String className, String realClassName,
            String methodName, List<String> paramTypes) {
        // 创建并初始化循环调用节点
        DagNode cycleNode = createAndInitializeNode(parentCu, cu, fromClass,
                className, realClassName, methodName, paramTypes);
        if (cycleNode == null) {
            return cycleNode;
        }
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
    public DagNode createLeafNode(CompilationUnit parentCu, CompilationUnit cu, MethodBelongs2Class fromClass,
            String className, String realClassName, String methodName,
            List<String> paramTypes) {
        // 创建并初始化叶节点
        DagNode leafNode = createAndInitializeNode(parentCu, cu, fromClass, className, realClassName, methodName,
                paramTypes);
        if (leafNode == null) {
            return leafNode;
        }
        // 设置循环调用标志
        leafNode.setLoopCall(false);
        return leafNode;
    }

}