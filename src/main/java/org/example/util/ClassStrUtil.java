package org.example.util;

import org.apache.commons.lang3.StringUtils;
import org.example.constant.PathConstant;
import org.example.constant.WordConstant;

import com.google.common.base.Objects;

/**
 * 解析类名字符串的工具
 * 即，处理的是类文本
 */
public class ClassStrUtil {
    /**
     * 判断是不是类名的形式
     * 
     * @param classStr 类名字符串
     * @return 是否是类名的形式
     */
    public static boolean isSimpleClassName(String classStr) {
        if (StringUtils.isBlank(classStr)) {
            return false;
        }
        classStr = classStr.trim();
        // 首字母要大写
        if (!Character.isUpperCase(classStr.charAt(0))) {
            return false;
        }
        // 不含有 .
        if (classStr.contains(PathConstant.DOT)) {
            return false;
        }
        // 不含有泛型
        if (classStr.contains(PathConstant.LEFT_ANGLE_BRACKET)) {
            return false;
        }
        // 如果有数字，下划线不好判定，在无数字和下划线前提下，且字符 >= 2 的前提下，全是大写就不认为是 className
        if (!classStr.contains(PathConstant.UNDER_LINE) && !classStr.matches("[0-9]+") && classStr.length() > 1) {
            boolean isLowerCharExist = false;
            for (char ch : classStr.toCharArray()) {
                if (Character.isLowerCase(ch)) {
                    isLowerCharExist = true;
                }
            }
            if (!isLowerCharExist) {
                // 说明很可能是常量，全部大写
                return false;
            }
        }

        return true;
    }

    /**
     * 判断 className 是不是 User.Person 的格式
     * 
     * @param className
     * @return 是否是 User.Person 的格式
     */
    public static boolean isNestedClassName(String className) {
        if (StringUtils.isBlank(className)) {
            return false;
        }
        // 不含有 .
        if (!className.contains(PathConstant.DOT)) {
            return false;
        }
        String[] segs = className.split(PathConstant.ESCAPE_DOT);
        if (segs.length < 2) {
            return false;
        }
        String lastSeg = segs[segs.length - 1];
        String secLastSeg = segs[segs.length - 2];

        // seg 首字母不是大写
        if (!Character.isUpperCase(lastSeg.charAt(0)) || !Character.isUpperCase(secLastSeg.charAt(0))) {
            return false;
        }
        // 不含有 a-z，说明可能是常量
        if (!StringUtils.containsAny(lastSeg, WordConstant.LOWER_A_TO_Z)
                || !StringUtils.containsAny(secLastSeg, WordConstant.LOWER_A_TO_Z)) {
            return false;
        }
        return true;
    }

    /**
     * 判断 scopeStr 是否是枚举字段的形式
     * 比如 DemoEnum.FIELD_NAME
     * 
     * @param scopeStr
     * @return 是否是枚举字段的形式
     */
    public static boolean isClassConstant(String scopeStr) {
        if (StringUtils.isBlank(scopeStr)) {
            return false;
        }
        if (!scopeStr.contains(PathConstant.DOT)) {
            return false;
        }

        String[] segs = scopeStr.split(PathConstant.ESCAPE_DOT);
        if (segs.length == 2) {
            if (Character.isUpperCase(segs[0].charAt(0))) {
                // 只要 segs[1] 中不含有一个小写 a-z 字母
                if (!StringUtils.containsAny(segs[1], WordConstant.LOWER_A_TO_Z)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 粗糙的比较两个类是否是同一个类，由于两个可能都含有包名，或者只有一方有包名
     * 
     * @param fstClassName 第一个类名字符串
     * @param secClassName 第二个类名字符串
     * @return 是否是同一个类
     */
    public static boolean roughJudge2ClassNameEquals(String fstClassName, String secClassName) {
        if (StringUtils.isBlank(fstClassName) || StringUtils.isBlank(secClassName)) {
            return false;
        }
        if (Objects.equal(fstClassName, secClassName)) {
            return true;
        }
        if (fstClassName.endsWith(PathConstant.DOT + secClassName)
                || secClassName.endsWith(PathConstant.DOT + fstClassName)) {
            return true;
        }
        return false;
    }

    /**
     * 将 User.Person 转换为 User$Person，也可以是 com.xxx.User.Person 转化为
     * com.xxx.User$Person
     * 最终结果去掉泛型
     * 
     * @param className 多层嵌套的类名字符串
     * @return 转换后的类名字符串
     */
    public static String transInnerClass2DollarWithNoGeneric(String className) {
        if (StringUtils.isBlank(className)) {
            return null;
        }
        // 不含有 .
        if (!className.contains(PathConstant.DOT)) {
            // 去掉泛型
            return className.substring(0, className.indexOf(PathConstant.LEFT_ANGLE_BRACKET));
        }

        String[] segs = className.split(PathConstant.ESCAPE_DOT);
        String resutl = "";
        for (String seg : segs) {
            if (!Character.isUpperCase(seg.charAt(0))) {
                resutl += (seg + PathConstant.DOT);
                continue;
            }
            resutl += (seg + PathConstant.HYP_DOLLAR);
        }
        if (resutl.endsWith(PathConstant.HYP_DOLLAR) || resutl.endsWith(PathConstant.DOT)) {
            resutl = resutl.substring(0, resutl.length() - 1);
        }
        return resutl;
    }

    /**
     * 判断实际方法参数类型字符串与期望类型字符串是否匹配。
     * 
     * 支持以下情况：
     * 1. 带或不带包名的普通类（如 "java.util.List" 与 "List"）
     * 2. 泛型写法（如 "Map<String, Integer>" 与 "Map"、"Map<String,Integer>"）
     * 3. 完全相同的泛型写法（如 "Map<String,Integer>" 与 "Map<String,Integer>"）
     * 4. 数组类型（如 "int[]" 与 "int[]"）
     */
    public static boolean typeMatches(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        // 去掉两端空格
        actual = actual.trim();
        expected = expected.trim();

        // 直接相等（包括完整的泛型、数组等）
        if (actual.equals(expected)) {
            return true;
        }

        // 去掉泛型
        String actualBase = actual.split(PathConstant.LEFT_ANGLE_BRACKET)[0];
        String expectedBase = expected.split(PathConstant.LEFT_ANGLE_BRACKET)[0];

        if (actualBase.endsWith(PathConstant.DOT + expectedBase)) {
            return true;
        }
        if (expectedBase.endsWith(PathConstant.DOT + actualBase)) {
            return true;
        }
        return false;
    }

    /**
     * 获取包名
     * 
     * @param className 全限定类名
     * @return 包名
     */
    public static String getPackageName(String className) {
        if (className == null) {
            return "";
        }
        // 处理泛型类型，找到泛型参数的开始位置
        int genericStart = className.indexOf(PathConstant.CHAR_LEFT_ANGLE_BRACKET);
        // 找到最后一个点的位置，在泛型参数之前
        int lastDot = genericStart > 0 ? className.lastIndexOf(PathConstant.CHAR_DOT, genericStart) : className.lastIndexOf(PathConstant.CHAR_DOT);
        return lastDot > 0 ? className.substring(0, lastDot) : "";
    }

    /**
     * 获取简单类名，不包含泛型
     * 
     * @param className 全限定类名
     * @return 简单类名
     */
    public static String getSimpleClassName(String className) {
        if (className == null) {
            return "";
        }
        // 找到最后一个点的位置
        int lastDot = className.lastIndexOf(PathConstant.CHAR_DOT);
        // 提取类名部分
        String classNamePart = lastDot > 0 ? className.substring(lastDot + 1) : className;
        // 移除泛型参数
        int genericStart = classNamePart.indexOf(PathConstant.CHAR_LEFT_ANGLE_BRACKET);
        if (genericStart > 0) {
            classNamePart = classNamePart.substring(0, genericStart);
        }
        return classNamePart;
    }

    /**
     * 判断是否是接口
     * 
     * @param className 全限定类名，或者非全限定类名，不带泛型
     * @return 是否是接口
     */
    public static boolean isInterface(String className) {
        if (StringUtils.isBlank(className)) {
            return false;
        }
        // 找最后一个
        String[] segs = className.split(PathConstant.ESCAPE_DOT);
        String simpleClass = segs[segs.length - 1];

        if (simpleClass.length() <= 2) {
            return false;
        }
        if (Character.isUpperCase(simpleClass.charAt(0)) && simpleClass.charAt(0) == WordConstant.CHAR_I &&
                Character.isUpperCase(simpleClass.charAt(1)) &&
                Character.isLowerCase(simpleClass.charAt(simpleClass.length() - 1))) {
            return true;
        }
        return false;
    }
}
