package org.example.util;

import java.util.List;

import org.example.constant.PathConstant;

/**
 * 签名文本工具类
 */
public class SignatureUtil {

    /**
     * 构建方法签名
     * 
     * @param className     声明类名（多态场景下的接口或父类）
     * @param realClassName 真实类名（多态场景下的子类）
     * @param methodName    方法名
     * @param paramTypes    参数类型列表，比如 String, int
     * @return 方法签名
     */
    public static String buildMethodSignature(String className, String realClassName, String methodName,
            List<String> paramTypes) {
        StringBuilder sb = new StringBuilder();
        // 添加声明类名（多态场景下的接口或父类）
        sb.append(ClassStrUtil.getPackageName(className)).append(PathConstant.DOT);
        sb.append(ClassStrUtil.getSimpleClassName(className)).append(PathConstant.HYP_SHARP);
        // 添加真实类名
        sb.append(ClassStrUtil.getPackageName(realClassName)).append(PathConstant.DOT);
        sb.append(ClassStrUtil.getSimpleClassName(realClassName)).append(PathConstant.HYP_SHARP);
        sb.append(methodName).append(PathConstant.LEFT_BRACKET);
        if (paramTypes != null && !paramTypes.isEmpty()) {
            sb.append(String.join(PathConstant.HYP_PARAM_SEPARATOR1, paramTypes));
        }
        sb.append(PathConstant.RIGHT_BRACKET);
        return sb.toString();
    }

}
