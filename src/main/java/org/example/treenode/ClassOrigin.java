package org.example.treenode;

/**
 * 类来源
 * 项目类/ JDK 类/ 依赖类
 */
public enum ClassOrigin {
    PROJECT("project"),
    JDK("jdk"),
    DEPENDENCY("dependency");

    private final String origin;

    ClassOrigin(String origin) {
        this.origin = origin;
    }

    public String getOrigin() {
        return origin;
    }

    public static ClassOrigin fromOrigin(String origin) {
        for (ClassOrigin o : ClassOrigin.values()) {
            if (o.getOrigin().equals(origin)) {
                return o;
            }
        }
        return null;
    }
}
