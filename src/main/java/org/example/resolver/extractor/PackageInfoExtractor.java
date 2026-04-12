package org.example.resolver.extractor;

import org.example.node.field.PackageInfo;
import org.example.util.StringUtil;

/**
 * 专门用于提取 PackageInfo 和 PackageInfo 中的信息
 */
public class PackageInfoExtractor implements InfoExtractor {
    /**
     * 提取包信息 
     * 
     * @param className     全限定类名
     * @param realClassName 全限定真实类名（多态场景下真实的类）
     * @return 包信息，包含声明包名和真实包名
     */
    public PackageInfo extract(String className, String realClassName) {
        String packageName = StringUtil.getPackageName(className);
        String realPackageName = StringUtil.getPackageName(realClassName);
        return new PackageInfo(packageName, realPackageName);
    }
}
