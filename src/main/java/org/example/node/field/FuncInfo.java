package org.example.node.field;

import java.util.List;
import java.util.Map;

import com.github.javaparser.ast.Modifier.Keyword;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FuncInfo {
    // 方法注释
    private String funcComment;

    // annotation
    // key: 注解名，value: 注解参数
    // 注解参数：key：参数名，value 参数值(String/int/bool/enum/arr/class)
    private Map<String, Map<String, Object>> annotations;

    // 方法分类：普通方法/构造器/main
    private FuncCate funcCate;

    // javaparser Modifier.Keyword，诸如 private, final, static 等
    private List<Keyword> methodModifiers;

    // 方法名
    private String funcName;

    // 方法参数的包名
    private List<String> funcParamsPackageName;
    // 方法参数, 比如 String, int 等
    private List<String> funcParams;

    // 方法返回值包名
    private String funcReturnPackageName;
    // 方法返回值类型，比如 void, int, String 等
    private String funcReturnType;
}
