package org.example.callchain;

import java.lang.instrument.ClassDefinition;

import org.apache.commons.lang3.StringUtils;
import org.example.treenode.ClassDeclaration;

public class Level1 {

    public String level1Func(String name, int age) {
        System.out.printf("here level1Func, name: {}, age: {}\n", name, age);

        Level2 level2 = new Level2(name, age);
        // 测试基础的方法调用链路分析
        level2.level2_1Func(name, age);
        // 测试类 extends
        level2.level2_2Func(name);
        // 测试类 extends abstract 类
        level2.level2_3Func(name);
        // 测试类 implements 接口
        level2.level2_4Func(name, age);
        // 测试类 enum
        level2.level2_5Func();
        // 测试类 lambda 表达式和函数式接口
        level2.level2_6Func();

        String.valueOf(123);

        StringUtils.isBlank(name);

        ClassDeclaration.INTERFACE.asString();

        return "level1Func";
    }
}
