package org.example.resolver.parser;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.example.constant.PathConstant;
import org.example.constant.WordConstant;

import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedParameterDeclaration;

public class ResolveParser {
    /**
     * 从ResolvedParameterDeclaration 中解析参数类型字符串
     * 包含包名，带有泛型
     * 
     * @param resolvedParamDecl 参数声明
     * @return 参数类型字符串
     */
    public String parseOutTypeStrFromParam(ResolvedParameterDeclaration resolvedParamDecl) {
        if (resolvedParamDecl == null) {
            return null;
        }
        return resolvedParamDecl.getType().describe();
    }

    /**
     * 从 ResolvedParameterDeclaration 中解析参数类型字符串
     * 包含包名，不包含泛型
     * 
     * @param resolvedParamDecl 参数声明
     * @return 参数类型字符串
     */
    public String parseOutTypeStrFromParamWithNoGeneric(ResolvedParameterDeclaration resolvedParamDecl) {
        if (resolvedParamDecl == null) {
            return null;
        }
        // 全限定类名，擦除泛型，内部类可返回 com.xxx.User$Person
        // 需要注意当被调用方法的参数是第三方依赖类时候，这里是否会有异常
        String typeName = resolvedParamDecl.getType().erasure().describe();

        return typeName;
    }

    /**
     * 从 ResolvedMethodLikeDeclaration 中解析其中的声明类
     * 包含包名，不含泛型
     * 
     * @param resolvedMethod 方法声明
     * @param packStr        包名字符串
     * @return 方法所在类的全名
     */
    public String parseOutFullClassNameFromResolveWithNoGeneric(ResolvedMethodLikeDeclaration resolvedMethod,
            String packStr) {
        if (resolvedMethod == null) {
            return null;
        }
        // 不含有包名的声明类字符串，肯定不带有泛型，内部类则是 User$Person 形式
        String fullClassStr = null;
        try {
            fullClassStr = resolvedMethod.declaringType().getQualifiedName();
        } catch (Exception e) {
            fullClassStr = packStr + PathConstant.DOT + resolvedMethod.getClassName();
        }

        return fullClassStr;
    }

    /**
     * 从 ResolvedMethodLikeDeclaration 中解析其中的参数类型字符串
     * 适用于 resolvedMethod 对应的方法是不含有第三方依赖的
     * 
     * @param resolvedMethod 方法声明
     * @return 参数类型字符串列表
     */
    public List<String> parseOutParamsTypeFromResolvedMethodWithNoOuterDep(
            ResolvedMethodLikeDeclaration resolvedMethod) {
        if (resolvedMethod == null) {
            return null;
        }
        List<String> paramTypes = new ArrayList<>();
        // 获取参数类型：遍历方法参数，获取每个参数的类型
        for (int i = 0; i < resolvedMethod.getNumberOfParams(); i++) {
            try {
                // 获取参数类型
                String paramType = parseOutTypeStrFromParamWithNoGeneric(resolvedMethod.getParam(i));
                paramTypes.add(paramType);
            } catch (Exception e) {
                // 降级：尝试获取类型描述后手动去掉泛型
                try {
                    String fullType = resolvedMethod.getParam(i).getType().describe();
                    if (StringUtils.isBlank(fullType)) {
                        paramTypes.add(WordConstant.PARAM_TYPE_UNKNOWN);
                        continue;
                    }
                    // 去泛型
                    fullType = fullType.contains(PathConstant.LEFT_ANGLE_BRACKET)
                            ? fullType.substring(0, fullType.indexOf(PathConstant.LEFT_ANGLE_BRACKET))
                            : fullType;
                    paramTypes.add(fullType);
                } catch (Exception ex) {
                    // 最终降级
                    paramTypes.add(WordConstant.PARAM_TYPE_UNKNOWN);
                }
            }
        }

        return paramTypes;
    }

}
