package org.example.node.field;

/**
 * 类声明
 * 1. 普通类
 * 2. 接口
 * 3. 枚举
 * 4. 注解
 * 5. 记录类
 */
public enum ClassDeclaration {
    CLASS("class"), // 普通类
    INTERFACE("interface"), // 接口
    ENUM("enum"), // 枚举
    ANNOTATION("annotation"), // 注解
    RECORD("record"); // 记录类

    private final String declaration;

    ClassDeclaration(String declaration) {
        this.declaration = declaration;
    }

    public String asString() {
        return declaration;
    }

    public static ClassDeclaration fromString(String declaration) {
        for (ClassDeclaration c : ClassDeclaration.values()) {
            if (c.declaration.equals(declaration)) {
                return c;
            }
        }
        return null;
    }
}
