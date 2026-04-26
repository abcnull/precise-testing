package org.example.resolver.parser;

import org.example.constant.PathConstant;
import org.example.constant.WordConstant;
import org.example.util.ClassStrUtil;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;

/**
 * 字段解析器
 */
public class FieldDeclParser {
    private FileParser getFileParser() {
        return FileParser.getInstance();
    }

    /**
     * 判定 field 是否有赋值给它的语句
     * 
     * @param field 字段声明
     * @return 是否有赋值给它的语句
     */
    public boolean isFieldHasInitializer(FieldDeclaration field) {
        if (field == null) {
            return false;
        }
        return field.getVariables().stream().anyMatch(var -> var.getInitializer().isPresent());
    }

    /**
     * 判断 field 是否有 @Autowired 或 @Resource 注解
     * 
     * @param field 字段声明
     * @return 是否有 @Autowired 或 @Resource 注解
     */
    public boolean isAutowiredOrResource(FieldDeclaration field) {
        if (field == null) {
            return false;
        }
        return field.getAnnotations().stream().anyMatch(ann -> ann.getNameAsString().equals(WordConstant.ANN_AUTOWIRED)
                || ann.getNameAsString().equals(WordConstant.ANN_RESOURCE));
    }

    /**
     * 从 @Autowired 或 @Resource 注解中推断出注入的实现类名
     * 全限定类名，包括包名，不包括泛型
     * 
     * @param field         字段声明
     * @param declClassName 声明的类名
     * @return 注入的实现类名
     */
    public String inferRealClassNameFromAutowiredField(FieldDeclaration field, String declClassName) {
        if (field == null) {
            return declClassName;
        }
        if (!isAutowiredOrResource(field)) {
            return declClassName;
        }

        // 如果不是接口形式，直接返回声明的类名
        if (!ClassStrUtil.isInterface(declClassName)) {
            return declClassName;
        }

        // 下面明确它是接口了

        String[] segs = declClassName.split(PathConstant.ESCAPE_DOT);
        // 获取包名
        String packageName = ClassStrUtil.getPackageName(declClassName);
        if (packageName.isEmpty() || !packageName.contains(PathConstant.DOT)
                || !packageName.contains(PathConstant.DOT + WordConstant.IMPL + PathConstant.DOT)) {
            // 包名不存在，或者包名无法推断依赖注入的实现类的包名
            return null;
        }
        // 获取接口名称
        String interfaceName = segs[segs.length - 1];

        // 推测包名
        // 把 packageName 中的最后一个 ".impl." 提传承 "."
        String inferPackageName = packageName.replaceFirst(PathConstant.DOT + WordConstant.IMPL + PathConstant.DOT,
                PathConstant.DOT);

        // 推测类名
        // 剔除第一个 I
        String className1 = interfaceName.substring(1);
        // 剔除第一个 I，尾部加 Impl
        String className2 = className1 + WordConstant.IMPL;
        // 组合可能性 1 ，2
        String[] inferClassNameArr = { className1, className2 };

        // 验证依赖注入的实现类
        for (String inferClassName : inferClassNameArr) {
            CompilationUnit cu = getFileParser().parseOutCompilationUnit(inferClassName);
            if (cu != null) {
                return inferPackageName + PathConstant.DOT + inferClassName;
            }
        }

        // 没推测出来实现类在哪
        return null;
    }
}
