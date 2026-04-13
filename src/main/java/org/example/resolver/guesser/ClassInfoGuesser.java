package org.example.resolver.guesser;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.example.node.field.ClassDeclaration;
import org.example.resolver.extractor.ClassInfoExtractor;
import org.example.resolver.model.MethodBelongs2Class;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

/**
 * 用于猜测分析 ClassInfo 中的数据
 */
public class ClassInfoGuesser {
    private final ClassInfoExtractor classInfoExtractor = new ClassInfoExtractor();

    public MethodBelongs2Class guessMethodFormClass(CompilationUnit parentCu, CompilationUnit cu,
            String className, String realClassName,
            String methodName, List<String> paramTypes) {

        // 该类是 jdk/第三方依赖
        if (cu == null) {
            return MethodBelongs2Class.SELF_CLASS;
        }

        // realClassName == className, 直接判断来自本身类
        if (realClassName.equals(className)) {
            return MethodBelongs2Class.SELF_CLASS;
        }

        // 因为后续判定逻辑完全基于 parentCu，如果为 null 则无法判定
        if (parentCu == null) {
            return MethodBelongs2Class.UNKNOWN;
        }

        /* 复杂判断 */

        // 如果 parentCu 不是 class 类型，说明 cu 中该方法必实现了，则直接判定 method 来自自身类
        ClassDeclaration classDeclaration = classInfoExtractor.extractClassDeclaration(parentCu, className);
        if (classDeclaration != classDeclaration.CLASS) {
            return MethodBelongs2Class.SELF_CLASS;
        }

        int paramCount = paramTypes == null ? 0 : paramTypes.size(); // 方法参数数量
        boolean ifCuHasMethod = roughJudgeUsualMethodDeclarationExist(cu, methodName, paramCount); // 粗略判断 cu 中是否含有该方法
        if (!ifCuHasMethod) {
            // 如果 cu 中不包含该方法（方法名+参数数量），则只可能是父类或者祖先类有该方法

            boolean ifParentCuHasMethod = roughJudgeUsualMethodDeclarationExist(parentCu, methodName, paramCount); // 粗略判断
            // parentCu
            // 中是否含有该方法
            if (!ifParentCuHasMethod) {
                // 如果 parentCu 也不包含该方法（方法名+参数数量），只可能是祖先类
                return MethodBelongs2Class.ANCESTOR_CLASS;
            } else {
                // 如果 parentCu 包含了该方法（方法名+参数数量）

                boolean ifParentCuExtendsOthers = judgeClassHasExtends(parentCu); // 粗略判断 parentCu 是否 extends 祖先类
                if (!ifParentCuExtendsOthers) {
                    // 如果 parentCu 没有 extends 祖先类
                    return MethodBelongs2Class.PARENT_CLASS;
                } else {
                    // 如果 parentCu extends 祖先类，则无法判定这个方法来自父类还是祖先类
                    return MethodBelongs2Class.UNKNOWN;
                }
            }
        } else {
            // 如果 cu 中包含该方法（方法名+参数数量）

            boolean ifParentCuHasMethod = roughJudgeUsualMethodDeclarationExist(parentCu, methodName, paramCount); // 粗略判断
            // parentCu
            // 中是否含有该方法
            if (!ifParentCuHasMethod) {
                // 如果 parentCu 不包含该方法（方法名+参数数量），只可能是祖先类
                return MethodBelongs2Class.ANCESTOR_CLASS;
            } else {
                // 如果 parentCu 包含了该方法（方法名+参数数量）

                boolean ifParentCuExtendsOthers = judgeClassHasExtends(parentCu); // 粗略判断 parentCu 是否 extends 祖先类
                if (!ifParentCuExtendsOthers) {
                    // 如果 parentCu 没有 extends 祖先类

                    Boolean isAbstractMethod = judgeMethodAbstract(parentCu, methodName, paramCount); // 粗略判断 parentCu
                                                                                                      // 中该方法是否是抽象方法
                    if (isAbstractMethod == true) {
                        // 如果 parentCu 只有抽象方法有这个方法，说明来自子类本身
                        return MethodBelongs2Class.SELF_CLASS;
                    } else if (isAbstractMethod == false) {
                        // 如果 parentCu 只有非抽象方法有这个方法，说明来自父类
                        return MethodBelongs2Class.PARENT_CLASS;
                    } else {
                        // 其他情况，无法判定
                        return MethodBelongs2Class.UNKNOWN;
                    }
                } else {
                    // 如果 parentCu extends 祖先类，则无法判定这个方法来自父类还是祖先类
                    return MethodBelongs2Class.UNKNOWN;
                }
            }
        }
    }

    /**
     * 粗糙的判断是否存在该一般方法
     *
     * @param cu         编译单元
     * @param methodName 方法名
     * @param paramCount 参数数量
     * @return 是否存在该方法
     */
    private boolean roughJudgeUsualMethodDeclarationExist(CompilationUnit cu, String methodName, int paramCount) {
        // 存在就返回 true，否则返回 false
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals(methodName))
                // 参数数量匹配时才通过
                .filter(method -> {
                    // 参数数量匹配
                    if (paramCount == method.getParameters().size()) {
                        return true;
                    }
                    return false;
                })
                .findFirst()
                .isPresent();
    }

    /**
     * 判断类是否有 extends 的方法
     * 
     * @param cu        编译单元
     * @param className 类名
     * @return 是否有 extends 的方法
     */
    private boolean judgeClassHasExtends(CompilationUnit cu) {
        // 获取所有的类声明
        List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);

        for (ClassOrInterfaceDeclaration classDecl : classes) {
            // 检查是否有扩展类型
            if (!classDecl.getExtendedTypes().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 粗糙的通过方法名+方法参数的数量来匹配编译单元中的对应的方法 list
     * 
     * @param cu         编译单元
     * @param methodName 方法名
     * @param paramCount 参数数量
     * @return 匹配的方法声明列表
     */
    private List<MethodDeclaration> roughGainMethodDeclarations(CompilationUnit cu, String methodName,
            int paramCount) {
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals(methodName))
                // 参数数量匹配时才通过
                .filter(method -> {
                    // 参数数量匹配
                    if (paramCount == method.getParameters().size()) {
                        return true;
                    }
                    return false;
                }).collect(Collectors.toList());
    }

    /**
     * 判断方法是否为抽象方法
     * 
     * @param cu         编译单元
     * @param methodName 方法名
     * @param paramCount 参数数量
     * @return true 抽象方法，false 非抽象方法，null 无法判定
     */
    private Boolean judgeMethodAbstract(CompilationUnit cu, String methodName, int paramCount) {
        List<MethodDeclaration> methods = roughGainMethodDeclarations(cu, methodName, paramCount);

        boolean isUsualMehtod = false;
        boolean isAbstractMethod = false;

        if (CollectionUtils.isEmpty(methods)) {
            return null;
        }

        for (MethodDeclaration method : methods) {
            if (method.isAbstract()) {
                isAbstractMethod = true;
            } else {
                isUsualMehtod = true;
            }
        }

        if (isAbstractMethod && !isUsualMehtod) {
            return true;
        }
        if (!isAbstractMethod && isUsualMehtod) {
            return false;
        }

        return null;
    }
}
