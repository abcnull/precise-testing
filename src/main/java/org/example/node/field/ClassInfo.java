package org.example.node.field;

import java.util.List;
import java.util.Map;

import com.github.javaparser.ast.Modifier.Keyword;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 类信息
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClassInfo {
    // 类来源
    private ClassOrigin classOrigin;

    // 类注释
    private String classComment;

    // annotation
    // key: 注解名，value: 注解参数
    // 注解参数：key：参数名，value 参数值(String/int/bool/enum/arr/class)
    private Map<String, Map<String, Object>> annotations;

    // javaparser Modifier.Keyword，诸如 public, final, abstract 等
    private List<Keyword> classModifiers;

    // 类声明，如 class/interface/enum/annotation/record
    private ClassDeclaration classDeclaration;

    // 类名，比如 ClassInfo
    private String className;
    // 多态场景下的真实类名
    // 比如 Child extends Parent，当 Parent parent = new Child() 时，parent 对应的 className
    // 是 Parent，而 realClassName 是 Child
    // 比如 Child implements Parent，当 Parent parent = new Child() 时，parent 对应的
    // className 是 Parent，而 realClassName 是 Child
    private String realClassName;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ClassInfo classInfo = (ClassInfo) o;
        return java.util.Objects.equals(className, classInfo.className) &&
                java.util.Objects.equals(realClassName, classInfo.realClassName);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(className, realClassName);
    }
}
