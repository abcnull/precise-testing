package org.example.util;

import org.apache.commons.lang3.StringUtils;
import org.example.constant.PathConstant;

/**
 * 纯粹的字符串处理的工具类
 */
public class StringUtil {
    /**
     * 检查类名是否匹配模式（支持通配符）
     */
    public static boolean matchesPattern(String className, String pattern) {
        if (StringUtils.isBlank(pattern) || StringUtils.isBlank(className))
            return false;

        // 如果模式不包含通配符，直接进行等值比较
        if (!pattern.contains(PathConstant.STAR))
            return className.equals(pattern);

        // 将模式转换为正则表达式：点号作为字面量，* 转换为 .*
        String regex = PathConstant.CARET +
                pattern.replace(PathConstant.DOT, PathConstant.ESCAPE_DOT)
                        .replace(PathConstant.STAR, PathConstant.DOT_STAR)
                        .replace(PathConstant.HYP_DOLLAR, PathConstant.ESCAPE_DOLLAR)
                +
                PathConstant.HYP_DOLLAR;

        return className.matches(regex);
    }
}