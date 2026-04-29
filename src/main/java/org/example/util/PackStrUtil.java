package org.example.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.example.constant.PathConstant;

/**
 * 包类型的文本处理工具
 * 即，处理的是包文本
 */
public class PackStrUtil {

    /**
     * 判断是不是包的形式
     * 
     * @param packStr 包名
     * @return 是否是包的形式
     */
    public static boolean isPack(String packStr) {
        if (StringUtils.isBlank(packStr)) {
            return false;
        }
        packStr = packStr.trim();
        // 首字母不是小写
        // false: Com.xxx.User
        // false: _com.xxx.User
        if (!Character.isLowerCase(packStr.charAt(0))) {
            return false;
        }
        String segs[] = packStr.split(PathConstant.ESCAPE_DOT);
        // 只有一个元素
        // false: User
        if (segs.length <= 1) {
            return false;
        }
        // 不包含泛型
        if (packStr.contains(PathConstant.LEFT_ANGLE_BRACKET)) {
            return false;
        }
        return true;
    }

    /**
     * 判断包是不是有 class 形式（不含有泛型）:
     * 
     * @param packStr
     */
    public static boolean isPackEndWithSimpleClass(String packStr) {
        if (StringUtils.isBlank(packStr)) {
            return false;
        }
        // 如果本身不是包的形式
        if (!isPack(packStr)) {
            return false;
        }
        String segs[] = packStr.split(PathConstant.ESCAPE_DOT);
        // 最后一个元素的首字母不是的大写
        // false: com.xxx.User
        // false: com.xxx.*
        if (!Character.isUpperCase(segs[segs.length - 1].charAt(0))) {
            return false;
        }
        // 不含有泛型
        if (packStr.contains(PathConstant.LEFT_ANGLE_BRACKET)) {
            return false;
        }
        // 如果每个 seg 有超过 1 个大写字母开头的，形如 com.xxx.User.Name/com.xxx.User.NAME
        int count = 0;
        for (String seg : segs) {
            if (Character.isUpperCase(seg.charAt(0))) {
                count++;
            }
            if (count > 1) {
                return false;
            }
        }
        // 剩下就是正确的最后是 class 的包名
        return true;
    }

    /**
     * 判断包是不是有 field 形式
     * 
     * @param packStr 包名
     * @return 是否是 field 包形式
     */
    public static boolean isPackEndWithField(String packStr) {
        if (StringUtils.isBlank(packStr)) {
            return false;
        }
        // 如果本身不是包的形式
        if (!isPack(packStr)) {
            return false;
        }
        String segs[] = packStr.split(PathConstant.ESCAPE_DOT);
        if (segs.length <= 2) {
            return false;
        }
        String lastSeg = segs[segs.length - 1];
        String classSeg = segs[segs.length - 2];

        // false: com.xxx.*
        if (lastSeg.equals(PathConstant.STAR)) {
            return false;
        }
        // 最后一个 seg 首字母小写
        if (Character.isLowerCase(lastSeg.charAt(0))) {
            return true;
        }
        // false: com.xxx.User.NAME
        if (Character.isUpperCase(classSeg.charAt(0)) && lastSeg.length() >= 2) {
            boolean isField = true;
            for (int i = 1; i < lastSeg.length(); i++) {
                if (Character.isLowerCase(lastSeg.charAt(i))) {
                    isField = false;
                    break;
                }
            }
            if (isField) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断包是不是有 method 形式
     * 
     * @param packStr 包名
     * @return 是否是 method 包形式
     */
    public static boolean isPackEndWithMethod(String packStr) {
        return isPackEndWithStar(packStr);
    }

    /**
     * 判断包是不是有 * 形式
     * 
     * @param packStr 包名
     * @return 是否是 * 包形式
     */
    public static boolean isPackEndWithStar(String packStr) {
        if (StringUtils.isBlank(packStr)) {
            return false;
        }
        // 如果本身不是包的形式
        if (!isPack(packStr)) {
            return false;
        }
        String segs[] = packStr.split(PathConstant.ESCAPE_DOT);
        if (!segs[segs.length - 1].equals(PathConstant.STAR)) {
            return false;
        }
        return true;
    }

    /**
     * 获取项目路径下所有包路径的集合
     * 
     * @param projectPath 项目绝对路径
     * @return 包路径集合
     */
    public static Set<String> getAllPackStrFromProject(String projectPath) {
        if (StringUtils.isBlank(projectPath)) {
            return Collections.emptySet();
        }

        Path rootPath = Paths.get(projectPath);
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            return Collections.emptySet();
        }
        try {
            return Files.walk(rootPath)
                    .filter(Files::isDirectory)
                    .filter(dir -> hasJavaFile(dir))
                    .map(dir -> rootPath.relativize(dir).toString().replace(File.separator, PathConstant.DOT))
                    .filter(pack -> !pack.isEmpty())
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            return Collections.emptySet();
        }
    }

    /**
     * 判断目录下是否有 Java 文件
     * 
     * @param dir 目录
     * @return 是否有 Java 文件
     */
    private static boolean hasJavaFile(Path dir) {
        try {
            return Files.list(dir)
                    .filter(Files::isRegularFile)
                    .anyMatch(file -> file.getFileName().toString().endsWith(PathConstant.DOT_JAVA_SUFFIX));
        } catch (IOException e) {
            return false;
        }
    }

}
