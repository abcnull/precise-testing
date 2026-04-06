package org.example.resolver.extractor;

import org.example.resolver.model.ParameterInfo;
import org.example.resolver.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 参数信息提取器
 */
public class ParameterInfoExtractor {

    /**
     * 提取参数信息（简单类型名和包名）
     */
    public static ParameterInfo extractParameterInfo(List<String> paramTypes) {
        List<String> simpleTypes = new ArrayList<>();
        List<String> packageNames = new ArrayList<>();

        if (paramTypes != null) {
            for (String paramType : paramTypes) {
                simpleTypes.add(StringUtil.getSimpleClassName(paramType));
                packageNames.add(StringUtil.getPackageName(paramType));
            }
        }

        return new ParameterInfo(simpleTypes, packageNames);
    }
}
