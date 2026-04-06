package org.example.resolver.model;

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