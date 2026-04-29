package org.example.util;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.example.constant.PathConstant;

/**
 * 签名文本工具类
 */
public class SignatureUtil {

    /**
     * 构建方法签名
     * 
     * @param fullDeclClassName 全限定声明类名（多态场景下的接口或父类）
     * @param fullRealClassName 全限定真实类名（多态场景下的子类）
     * @param methodName        方法名
     * @param paramTypes        参数类型列表，比如 String, int
     * @return 方法签名
     */
    public static String buildMethodSignature(String fullDeclClassName, String fullRealClassName, String methodName,
            List<String> paramTypes) {
        StringBuilder sb = new StringBuilder();
        // 添加声明类名（多态场景下的接口或父类）
        sb.append(ClassStrUtil.getPackageName(fullDeclClassName)).append(PathConstant.DOT);
        sb.append(ClassStrUtil.getSimpleClassName(fullDeclClassName)).append(PathConstant.HYP_SHARP);
        // 添加真实类名
        sb.append(ClassStrUtil.getPackageName(fullRealClassName)).append(PathConstant.DOT);
        sb.append(ClassStrUtil.getSimpleClassName(fullRealClassName)).append(PathConstant.HYP_SHARP);
        sb.append(methodName).append(PathConstant.LEFT_BRACKET);
        if (CollectionUtils.isNotEmpty(paramTypes)) {
            sb.append(String.join(PathConstant.HYP_PARAM_SEPARATOR1, paramTypes));
        }
        sb.append(PathConstant.RIGHT_BRACKET);
        return sb.toString();
    }

    /**
     * 构建简单方法签名
     * 
     * @param fullDeclClassName 全限定声明类名（多态场景下的接口或父类）
     * @param methodName        方法名
     * @param paramTypes        参数类型列表，比如 String, int
     * @return 简单方法签名
     */
    public static String buildSimpleMethodSignature(String fullDeclClassName, String methodName,
            List<String> paramTypes) {
        StringBuilder sb = new StringBuilder();

        sb.append(ClassStrUtil.getSimpleClassName(fullDeclClassName)).append(PathConstant.HYP_SHARP);
        sb.append(methodName).append(PathConstant.LEFT_BRACKET);
        if (CollectionUtils.isNotEmpty(paramTypes)) {
            sb.append(String.join(PathConstant.HYP_PARAM_SEPARATOR1, paramTypes.stream()
                    .map(param -> ClassStrUtil.getSimpleClassName(param)).collect(Collectors.toList())));
        }
        sb.append(PathConstant.RIGHT_BRACKET);
        return sb.toString();
    }

}
