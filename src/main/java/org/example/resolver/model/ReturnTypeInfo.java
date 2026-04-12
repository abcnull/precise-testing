package org.example.resolver.model;

/**
 * 返回类型信息
 * 用于存储方法调用的返回类型信息，包括返回类型、返回包名等
 */
public class ReturnTypeInfo {
    private final String simpleType;
    private final String packageName;

    public ReturnTypeInfo(String simpleType, String packageName) {
        this.simpleType = simpleType;
        this.packageName = packageName;
    }

    public String getSimpleType() {
        return simpleType;
    }

    public String getPackageName() {
        return packageName;
    }
}