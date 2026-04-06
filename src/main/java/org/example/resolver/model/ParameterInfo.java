package org.example.resolver.model;

import java.util.List;

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