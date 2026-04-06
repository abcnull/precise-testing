package org.example.resolver.model;

import java.util.List;

public class MethodCallInfo {
    private final String className;
    private final String realClassName;
    private final String methodName;
    private final List<String> paramTypes;

    public MethodCallInfo(String className, String realClassName, String methodName, List<String> paramTypes) {
        this.className = className;
        this.realClassName = realClassName;
        this.methodName = methodName;
        this.paramTypes = paramTypes;
    }

    public MethodCallInfo(String className, String methodName, List<String> paramTypes) {
        this(className, className, methodName, paramTypes);
    }

    public String getClassName() {
        return className;
    }

    public String getRealClassName() {
        return realClassName;
    }

    public String getMethodName() {
        return methodName;
    }

    public List<String> getParamTypes() {
        return paramTypes;
    }
}