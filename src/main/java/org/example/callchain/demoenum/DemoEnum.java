package org.example.callchain.demoenum;

public enum DemoEnum {
    ENUM1("ONE", 1),
    ENUM2("TWO", 2);

    // 字段
    private final String name;
    private final int age;

    // 构造函数
    private DemoEnum(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }
    
    public int getAge() {
        return age;
    }
}
