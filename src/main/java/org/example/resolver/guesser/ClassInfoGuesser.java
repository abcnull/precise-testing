package org.example.resolver.guesser;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.example.constant.PathConstant;
import org.example.node.field.ClassDeclaration;
import org.example.resolver.model.MethodBelongs2Class;
import org.example.resolver.parser.CompilationUnitParser;
import org.example.util.ClassStrUtil;

import com.github.javaparser.ast.CompilationUnit;
import com.google.common.base.Objects;

/**
 * 用于猜测分析 ClassInfo 中的数据
 */
public class ClassInfoGuesser {
    private CompilationUnitParser classParser;

    private CompilationUnitParser getClassParser() {
        if (classParser == null) {
            classParser = new CompilationUnitParser();
        }
        return classParser;
    }

    /**
     * 猜测判断代码执行的 method 真正来源实现类，or 声明类，or 祖先类，or 其他
     * 
     * @param parentCu      声明类 CompilationUnit
     * @param cu            实现类 CompilationUnit
     * @param className     声明类名，一般情况下声明类存在，实现类可能找不到
     * @param realClassName 实现类名，一般情况下声明类存在，实现类可能找不到
     * @param methodName    方法名
     * @param paramTypes    参数类型列表
     * @return 方法来源类
     */
    public MethodBelongs2Class guessMethodFormClass(CompilationUnit parentCu, CompilationUnit cu,
            String className, String realClassName,
            String methodName, List<String> paramTypes) {
        if (StringUtils.isBlank(className) && StringUtils.isBlank(realClassName)) {
            return MethodBelongs2Class.UNKNOWN;
        }

        // 该类是 jdk/第三方依赖
        if (cu == null) {
            return MethodBelongs2Class.REAL_CLASS;
        }
        if (StringUtils.isNotBlank(className) && !className.contains(PathConstant.DOT)
                && Character.isLowerCase(className.charAt(0))) {
            // 一般情况下声明类存在，实现类可能找不到，所以这里用声明类来判定
            return MethodBelongs2Class.REAL_CLASS;
        }

        // realClassName == className, 直接判断来自本身类
        if (ClassStrUtil.roughJudge2ClassNameEquals(className, realClassName)) {
            return MethodBelongs2Class.REAL_CLASS;
        }

        // 因为后续判定逻辑完全基于 parentCu，如果为 null 则无法判定
        if (parentCu == null) {
            return MethodBelongs2Class.UNKNOWN;
        }

        // 如果是构造方法，那么说明方法来自子类
        if (Objects.equal(realClassName, methodName) || realClassName.endsWith(PathConstant.DOT + methodName)) {
            return MethodBelongs2Class.REAL_CLASS;
        }

        /* 复杂判断 */
        ClassDeclaration classDeclaration = getClassParser().parseOutClassDeclaration(parentCu, className);
        if (classDeclaration == null) {
            return MethodBelongs2Class.UNKNOWN;
        }

        // 如果 parentCu 不是 class 类型，说明 cu 中该方法必实现了，则直接判定 method 来自自身类
        if (classDeclaration != classDeclaration.CLASS) {
            return MethodBelongs2Class.REAL_CLASS;
        }

        // 父类是抽象类/普通类，于是方法可能只有父类才有，而子类不具有，此种情况时，方法才来源父类
        Boolean ifCuHasMethod = getClassParser().judgeUsualMethodDeclarationExist(cu, methodName, paramTypes); // 判断 cu
        if (ifCuHasMethod == null) {
            // 如果子类 cu 中有多个此名的方法，说明方法来自子类
            return MethodBelongs2Class.REAL_CLASS;
        } else if (ifCuHasMethod.equals(true)) {
            // 子类有该方法
            return MethodBelongs2Class.REAL_CLASS;
        } else {
            // 子类没有该方法
            Boolean ifParentCuHasMethod = getClassParser().judgeUsualMethodDeclarationExist(parentCu, methodName,
                    paramTypes); // 判断父类 cu
            if (ifParentCuHasMethod == null) {
                // 若 Child extends MidChild, 且 MidChild extends Parent，
                // 但是假如这个方法声明类 Parent 有该方法，但是实现类 MidChild 没有该方法，因此无法断定方法一定来自声明类 Parent，也有可能来自
                // MidChild 的某个祖先类
                return MethodBelongs2Class.UNKNOWN; // 来自声明类 or 祖先类
            } else if (ifParentCuHasMethod.equals(true)) {
                // 同理
                return MethodBelongs2Class.UNKNOWN; // 来自声明类 or 祖先类
            } else {
                // 声明类没有该方法，实现类也没有该方法，只可能是实现类的某个祖先类有该方法
                return MethodBelongs2Class.ANCESTOR_CLASS;
            }
        }
    }

}
