package org.example.resolver.factory;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier.Keyword;
import org.example.node.AstNode;
import org.example.node.field.ClassInfo;
import org.example.node.field.FuncInfo;
import org.example.node.field.PackageInfo;
import org.example.resolver.extractor.ClassInfoExtractor;
import org.example.resolver.extractor.MethodReflectionExtractor;
import org.example.resolver.util.StringUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class NodeFactory {

    private final ClassInfoExtractor classInfoExtractor = new ClassInfoExtractor();
    private final MethodReflectionExtractor methodReflectionExtractor = new MethodReflectionExtractor();

    /**
     * 创建循环调用节点
     */
    public AstNode createCycleNode(String className, String realClassName, String methodName, List<String> paramTypes, CompilationUnit cu) {
        AstNode cycleNode = new AstNode();

        // 设置包信息
        String packageName = StringUtil.getPackageName(className);
        String realPackageName = StringUtil.getPackageName(realClassName);
        PackageInfo packageInfo = new PackageInfo(packageName, realPackageName);
        cycleNode.setPackageInfo(packageInfo);

        // 创建并设置ClassInfo
        ClassInfo classInfo = classInfoExtractor.extractWithReflection(className, realClassName, cu);
        cycleNode.setClassInfo(classInfo);

        // 创建并设置FuncInfo
        FuncInfo cycleInfo = methodReflectionExtractor.extractMethodInfo(className, methodName, paramTypes, cu);
        cycleNode.setFuncInfo(cycleInfo);

        // 设置循环调用标志
        cycleNode.setLoopCall(true);

        // 初始化子节点列表和父节点列表
        cycleNode.setChildren(new ArrayList<>());
        cycleNode.setParents(new ArrayList<>());

        return cycleNode;
    }
    


    /**
     * 创建叶节点（用于无法解析的方法）
     */
    public AstNode createLeafNode(String className, String realClassName, String methodName, List<String> paramTypes, CompilationUnit cu) {
        AstNode node = new AstNode();

        // 设置包信息
        String packageName = StringUtil.getPackageName(className);
        String realPackageName = StringUtil.getPackageName(realClassName);
        PackageInfo packageInfo = new PackageInfo(packageName, realPackageName);
        node.setPackageInfo(packageInfo);

        // 创建并设置ClassInfo
        ClassInfo classInfo = classInfoExtractor.extractWithReflection(className, realClassName, cu);
        node.setClassInfo(classInfo);

        // 创建并设置FuncInfo
        FuncInfo info = methodReflectionExtractor.extractMethodInfo(className, methodName, paramTypes, cu);
        
        // 尝试通过反射获取构造器信息
        try {
            // 检查是否是构造器
            if (methodName.equals(StringUtil.getSimpleClassName(className))) {
                // 尝试获取构造器
                Class<?> clazz = Class.forName(className);
                Constructor<?> constructor = methodReflectionExtractor.findConstructorByReflection(clazz, paramTypes);
                if (constructor != null && (info.getMethodModifiers() == null || info.getMethodModifiers().isEmpty())) {
                    // 获取构造器修饰符
                    int constructorModifiers = constructor.getModifiers();
                    List<Keyword> constructorModifierList = new ArrayList<>();
                    if (Modifier.isPublic(constructorModifiers)) {
                        constructorModifierList.add(Keyword.PUBLIC);
                    }
                    if (Modifier.isPrivate(constructorModifiers)) {
                        constructorModifierList.add(Keyword.PRIVATE);
                    }
                    if (Modifier.isProtected(constructorModifiers)) {
                        constructorModifierList.add(Keyword.PROTECTED);
                    }
                    info.setMethodModifiers(constructorModifierList);
                }
            }
        } catch (Exception e) {
            // 反射失败时，使用默认值
        }
        
        node.setFuncInfo(info);

        // 设置循环调用标志
        node.setLoopCall(false);

        // 叶节点没有子节点
        node.setChildren(new ArrayList<>());
        node.setParents(new ArrayList<>());

        return node;
    }


}