package org.example.callchain;

public class Level3 {

    public String level3_1Func(String name, int age) {
        System.out.printf("here level3_1Func, name: {}, age: {}\n", name, age);

        return "level3_1Func";
    }

    public String level3_2Func(String name) {
        System.out.printf("here level3_2Func, name: {}\n", name);

        Level1 level1 = new Level1();
        // 循环调用 level1Func 方法
        level1.level1Func(name, 18);

        return "level3_2Func";
    }

    public String test_level3(int a) {
        return "test_level3";
    }
}
