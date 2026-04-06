package org.example.resolver.util;

import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class StringUtil {
    /**
     * 获取包名
     */
    public static String getPackageName(String className) {
        if (className == null) {
            return "";
        }
        // 处理泛型类型，找到泛型参数的开始位置
        int genericStart = className.indexOf('<');
        // 找到最后一个点的位置，在泛型参数之前
        int lastDot = genericStart > 0 ? className.lastIndexOf('.', genericStart) : className.lastIndexOf('.');
        return lastDot > 0 ? className.substring(0, lastDot) : "";
    }

    /**
     * 获取简单类名
     */
    public static String getSimpleClassName(String className) {
        if (className == null) {
            return "";
        }
        // 找到最后一个点的位置
        int lastDot = className.lastIndexOf('.');
        // 提取类名部分
        String classNamePart = lastDot > 0 ? className.substring(lastDot + 1) : className;
        // 移除泛型参数
        int genericStart = classNamePart.indexOf('<');
        if (genericStart > 0) {
            classNamePart = classNamePart.substring(0, genericStart);
        }
        return classNamePart;
    }

    /**
     * 构建方法签名
     */
    public static String buildMethodSignature(String className, String realClassName, String methodName, List<String> paramTypes) {
        StringBuilder sb = new StringBuilder();
        // 添加声明类名（多态场景下的接口或父类）
        sb.append(getPackageName(className)).append(".");
        sb.append(getSimpleClassName(className)).append("#");
        // 添加真实类名
        sb.append(getPackageName(realClassName)).append(".");
        sb.append(getSimpleClassName(realClassName)).append(".");
        sb.append(methodName).append("(");
        if (paramTypes != null && !paramTypes.isEmpty()) {
            sb.append(String.join(",", paramTypes));
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * 检查类名是否匹配模式（支持通配符）
     */
    public static boolean matchesPattern(String className, String pattern) {
        if (StringUtils.isBlank(pattern) || StringUtils.isBlank(className))
            return false;

        // 如果模式不包含通配符，直接进行等值比较
        if (!pattern.contains("*"))
            return className.equals(pattern);

        // 将模式转换为正则表达式：点号作为字面量，* 转换为 .*
        String regex = "^" + pattern.replace(".", "\\.")
                .replace("*", ".*") + "$";

        return className.matches(regex);
    }
}