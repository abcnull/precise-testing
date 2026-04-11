package org.example.resolver.factory;

import java.util.ArrayList;
import java.util.List;

import org.example.node.AstNode;
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
 * 节点工厂类，用于创建AST节点
 */
public class NodeFactory {

    private final PackageInfoExtractor packageInfoExtractor = new PackageInfoExtractor();
    private final ClassInfoExtractor classInfoExtractor = new ClassInfoExtractor();
    private final MethodInfoExtractor methodInfoExtractor = new MethodInfoExtractor();
    private final ClassParser classParser = new ClassParser();

    /**
     * 创建循环调用节点 DONE
     * 
     * @param cu            真实类的编译单元
     * @param className     全限定类名
     * @param realClassName 全限定真实类名（多态场景下真实的类）
     * @param methodName    方法名
     * @param paramTypes    参数类型列表
     * @return 循环调用节点
     */
    public AstNode createCycleNode(CompilationUnit cu, String className, String realClassName, String methodName,
            List<String> paramTypes) {
        AstNode cycleNode = new AstNode();

        // 设置包信息
        PackageInfo packageInfo = packageInfoExtractor.extract(className, realClassName);
        cycleNode.setPackageInfo(packageInfo);

        // 创建并设置ClassInfo
        ClassInfo classInfo = classInfoExtractor.extract(cu, className, realClassName);
        cycleNode.setClassInfo(classInfo);

        // 创建并设置FuncInfo
        MethodDeclaration method = classParser.parseOutMethodDeclaration(cu, methodName, paramTypes);
        FuncInfo cycleInfo = methodInfoExtractor.extract(method, realClassName, methodName, paramTypes);
        cycleNode.setFuncInfo(cycleInfo);

        // 设置循环调用标志
        cycleNode.setLoopCall(true);

        // 初始化子节点列表和父节点列表
        cycleNode.setChildren(new ArrayList<>());
        cycleNode.setParents(new ArrayList<>());

        return cycleNode;
    }

    /**
     * 创建叶节点（用于无法解析的方法）DONE
     * 
     * @param className     全限定类名
     * @param realClassName 全限定真实类名（多态场景下真实的类）
     * @param methodName    方法名
     * @param paramTypes    参数类型列表
     * @param cu            真实类的编译单元
     * @return 叶节点
     */
    public AstNode createLeafNode(CompilationUnit cu, String className, String realClassName, String methodName,
            List<String> paramTypes) {
        AstNode node = new AstNode();

        // 设置包信息
        PackageInfo packageInfo = packageInfoExtractor.extract(className, realClassName);
        node.setPackageInfo(packageInfo);

        // 创建并设置ClassInfo
        ClassInfo classInfo = classInfoExtractor.extract(cu, className, realClassName);
        node.setClassInfo(classInfo);

        // 创建并设置FuncInfo
        MethodDeclaration method = classParser.parseOutMethodDeclaration(cu, methodName, paramTypes);
        FuncInfo funcInfo = methodInfoExtractor.extract(method, realClassName, methodName, paramTypes);
        node.setFuncInfo(funcInfo);

        // 设置循环调用标志
        node.setLoopCall(false);

        // 叶节点没有子节点
        node.setChildren(new ArrayList<>());
        node.setParents(new ArrayList<>());

        return node;
    }

}