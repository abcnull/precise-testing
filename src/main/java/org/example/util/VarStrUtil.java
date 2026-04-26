package org.example.util;

import org.apache.commons.lang3.StringUtils;
import org.example.constant.PathConstant;
import org.example.constant.WordConstant;

/**
 * 变量名的处字符串处理
 * 即，处理的是变量文本
 */
public class VarStrUtil {
    /**
     * 判断是否为常量
     * - 一般认为所有a-zA-Z都要大写 ||
     * - 小驼峰
     * 
     * @param varStr 变量名
     * @return 是否为静态常量
     */
    public static boolean isConstantStr(String varStr) {
        if (StringUtils.isBlank(varStr)) {
            return false;
        }
        // 不含有包名
        if (varStr.startsWith(PathConstant.DOT)) {
            return false;
        }
        // 字符全部大写
        if ((Character.isUpperCase(varStr.charAt(0)) || varStr.startsWith(PathConstant.UNDER_LINE)) &&
                !StringUtils.containsAny(varStr, WordConstant.LOWER_A_TO_Z)) {
            return true;
        }
        // 小驼峰
        if ((Character.isLowerCase(varStr.charAt(0)) || varStr.startsWith(PathConstant.UNDER_LINE)) &&
                StringUtils.containsAny(varStr, WordConstant.UPPER_A_TO_Z)) {
            return true;
        }
        return false;
    }

    /**
     * 判断是否是变量名形式
     * 
     * @param varStr 变量名
     * @return 是否为变量名形式
     */
    public static boolean isVarStr(String varStr) {
        if (StringUtils.isBlank(varStr)) {
            return false;
        }
        // 不含有包名
        if (varStr.startsWith(PathConstant.DOT)) {
            return false;
        }
        // 首字母不是大写
        if (Character.isUpperCase(varStr.charAt(0))) {
            return false;
        }
        // 当至少存在 2 个 a-zA-Z 当前提下，所有 a-zA-Z 都是大写，说明应该是常量而不是变量
        int azCount = 0; // a-zA-Z 的数量
        int uppperCount = 0; // A-Z 的数量
        for (char ch : varStr.toCharArray()) {
            // ch 匹配 a-ZA-Z
            if (Character.isLetter(ch)) {
                azCount++;
            }
            // 如果匹配到 A-Z
            if (Character.isUpperCase(ch)) {
                uppperCount++;
            }
        }
        if (uppperCount > 1 && azCount == uppperCount) {
            // 说明是常量
            return false;
        }

        return true;
    }

    /**
     * 判断是否为 field 名
     * - 变量属于
     * - static final 常量属于
     * 
     * @param varStr 变量名
     * @return 是否为变量名
     */
    public static boolean isFieldStr(String varStr) {
        if (StringUtils.isBlank(varStr)) {
            return false;
        }
        // 变量
        if (isVarStr(varStr)) {
            return true;
        }
        // 常量
        if (isConstantStr(varStr)) {
            return true;
        }
        // 其他情况
        return false;
    }
}
