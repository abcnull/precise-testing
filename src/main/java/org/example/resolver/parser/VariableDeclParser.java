package org.example.resolver.parser;

import org.example.constant.PathConstant;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;

/**
 * 变量解析器
 */
public class VariableDeclParser {

    /**
     * 判断变量是否等于指定名称
     * 
     * @param var  变量声明器
     * @param name 名称
     * @return 是否等于指定名称
     */
    public boolean isVarEqualsName(VariableDeclarator var, String name) {
        if (var == null || name == null) {
            return false;
        }
        return var.getNameAsString().equals(name);
    }

    /**
     * 判断变量是否有初始化器
     * 
     * @param var 变量声明器
     * @return 是否有初始化器
     */
    public boolean isVarHasInitializer(VariableDeclarator var) {
        if (var == null) {
            return false;
        }
        return var.getInitializer().isPresent();
    }

    /**
     * 解析变量初始化器表达式
     * 
     * @param var 变量声明器
     * @return 初始化器表达式
     */
    public Expression parseOutInitializerFromVar(VariableDeclarator var) {
        if (var == null || !isVarHasInitializer(var)) {
            return null;
        }
        return var.getInitializer().get();
    }

    /**
     * 解析变量类型的基础名称，一般不带有包名
     * 比如文本写的是 A a，解析的就是 A 不带有包名，除非写的是 com.xxx.A a 解析的就是 com.xxx.A
     * 
     * @param var 变量声明器
     * @return 变量类型基础名称，可能带有泛型，一般不带有包名
     */
    public String parseOutBaseTypeStrFromVar(VariableDeclarator var) {
        if (var == null) {
            return null;
        }
        return var.getType().toString();
    }

    /**
     * 解析变量类型名称，一般带有包名
     * 更耗性能，通过解析出类，来获取类的名称，带有包名
     * 可能会抛出异常，如果 resolve 失败
     * 
     * @param var 变量声明器
     * @return 变量类型名称，带有包名
     */
    public String parseOutTypeStrFromVar(VariableDeclarator var) {
        if (var == null) {
            return null;
        }
        return var.getType().resolve().describe();
    }

    /**
     * 解析变量类型基础名称，去掉泛型，一般不带有包名
     * 比如文本写的是 A<T> a，解析的就是 A 不带有包名，除非写的是 com.xxx.A<T> a 解析的就是 com.xxx.A
     * 
     * @param var 变量声明器
     * @return 变量类型基础名称，去掉泛型，一般不带有包名
     */
    public String parseOutBaseTypeStrFromVarWithNoGeneric(VariableDeclarator var) {
        if (var == null) {
            return null;
        }
        String varTypeName = var.getType().toString();
        if (varTypeName == null) {
            return null;
        }
        // 去掉泛型
        if (varTypeName != null && varTypeName.contains(PathConstant.LEFT_ANGLE_BRACKET)) {
            varTypeName = varTypeName.substring(0, varTypeName.indexOf(PathConstant.LEFT_ANGLE_BRACKET));
        }
        // User.Person -> User$Person
        if (varTypeName.contains(PathConstant.DOT)) {
            // 因为可能带有包名
            String afterClass = "";
            boolean prefix = false;
            String[] segs = varTypeName.split(PathConstant.ESCAPE_DOT);
            for (String seg : segs) {
                seg = seg.trim();
                if (afterClass.equals("")) {
                    afterClass += seg;
                    prefix = Character.isUpperCase(seg.charAt(0)) ? true : false;
                    continue;
                }
                if (prefix && Character.isUpperCase(seg.charAt(0))) {
                    // 上一个 seg 大写开头，这一个 seg 也是
                    afterClass += (PathConstant.HYP_DOLLAR + seg);
                    prefix = true;
                } else {
                    // 上一个 seg 非大写开头
                    afterClass += PathConstant.DOT + seg;
                    if (Character.isUpperCase(seg.charAt(0))) {
                        prefix = true;
                    } else {
                        prefix = false;
                    }
                }
            }
            return afterClass;
        }
        return varTypeName.trim();
    }

    /**
     * 解析变量类型名称，去掉泛型，一般带有包名
     * 更耗性能，通过解析出类，来获取类的名称，带有包名，但去掉了泛型
     * 可能会抛出异常，如果 resolve 失败
     * 
     * @param var 变量声明器
     * @return 变量类型名称，去掉泛型，带有包名
     */
    public String parseOutTypeStrFromVarWithNoGeneric(VariableDeclarator var) {
        if (var == null) {
            return null;
        }
        String varTypeName = var.getType().resolve().describe();
        // 去掉泛型
        if (varTypeName != null && varTypeName.contains(PathConstant.LEFT_ANGLE_BRACKET)) {
            varTypeName = varTypeName.substring(0, varTypeName.indexOf(PathConstant.LEFT_ANGLE_BRACKET));
        }
        return varTypeName;
    }
}
