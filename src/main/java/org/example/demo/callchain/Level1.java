package org.example.demo.callchain;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class Level1 {
    // jdk 方法调用链路
    public void level1_func1() {
        System.out.println("precise-testing");
    }

    // 第三方依赖
    public void level1_func2() {
        StringUtils.isBlank("");
    }

    // 项目中的类，易解析
    public void level1_func3() {
        new Level2();
    }

    // 循环调用
    public void level1_func4() {
        Level2 level2 = new Level2();
        level2.level2_func4();
    }

    // 泛型
    public void level1_func5() {
        List<String> list = new ArrayList<>();
        list.add("precise-testing");
    }

    // 多态
    public void level1_func6() {
        ILevel2 level2 = new Level2();
        level2.level2_func6();
    }

    /**
     * 注解，注释，返回值，传参等
     */
    @SuppressWarnings("level1_ann")
    public void level1_func7() {
        Level2 level2 = new Level2();
        level2.level2_func7("hello", 1);
    }

    // 混合复杂场景
    public void level1_func8(String arg1, int arg2) {
        Level2 level2 = new Level2();
        level2.level2_func8();
    }
}
