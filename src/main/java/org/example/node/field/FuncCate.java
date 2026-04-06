package org.example.node.field;

/**
 * 方法分类
 * 1. 默认方法
 * 2. 构造方法
 * 3. 主方法
 */
public enum FuncCate {
    DEFAULT("default"),
    CONSTRUCTOR("constructor"),
    MAIN("main");

    private final String funCate;

    FuncCate(String funCate) {
        this.funCate = funCate;
    }

    public String asString() {
        return funCate;
    }

    public static FuncCate fromString(String funCate) {
        for (FuncCate category : FuncCate.values()) {
            if (category.funCate.equals(funCate)) {
                return category;
            }
        }
        throw new IllegalArgumentException("No enum constant with value: " + funCate);
    }
}
