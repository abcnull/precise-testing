package org.example.resolver.model;

import java.util.List;

/**
 * 参数信息
 * 用于存储方法调用的参数信息，包括参数类型、参数包名等
 */
public class ParameterInfo {
    private final List<String> simpleTypes;
    private final List<String> packageNames;

    public ParameterInfo(List<String> simpleTypes, List<String> packageNames) {
        this.simpleTypes = simpleTypes;
        this.packageNames = packageNames;
    }

    public List<String> getSimpleTypes() {
        return simpleTypes;
    }

    public List<String> getPackageNames() {
        return packageNames;
    }
}