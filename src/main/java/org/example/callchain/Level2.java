package org.example.callchain;

import java.util.ArrayList;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.example.callchain.parentclass.ParentAbstract;
import org.example.callchain.parentclass.ParentClass;
import org.example.callchain.parentclass.ParentInterface;

public class Level2 {

    public Level2(String name, int age) {
    }

    @SuppressWarnings("unchecked")
    public String level2_1Func(String name, int age) {
        System.out.printf("here level2Func, name: {}, age: {}\n", name, age);

        Level3 level3 = new Level3();
        // 调用 level3_1Func 方法
        level3.level3_1Func(name, age);

        // 调用 level3_2Func 方法
        level3.level3_2Func(name);

        return "level2_1Func";
    }

    public String level2_2Func(String name) {
        System.out.printf("here level2_2Func, name: {}\n", name);

        SecondLevel3 secondLevel3 = new SecondLevel3();
        // 调用 secondLevel3_1Func 方法
        secondLevel3.secondLevel3_1Func(name, 0);

        ParentClass parentClass = new SecondLevel3();
        // 调用 secondLevel3_2Func 方法
        parentClass.secondLevel3_2Func(name);

        return "level2_2Func";
    }

    public String level2_3Func(String name) {
        System.out.printf("here level2_3Func, name: {}\n", name);

        // 实例类
        ThirdLevel3 thirdLevel3 = new ThirdLevel3();
        thirdLevel3.parentAbstractFunc(name);

        // 多态
        ParentAbstract parentAbstract = new ThirdLevel3();
        parentAbstract.parentAbstractFunc(name);

        return "level2_3Func";
    }

    public String level2_4Func(String name, int age) {
        System.out.printf("here level2_4Func, name: {}, age: {}\n", name, age);

        ForthLevel3 forthLevel3 = new ForthLevel3();
        forthLevel3.parentInterfaceFunc(name, age);

        // 多态
        ParentInterface parentInterface = new ForthLevel3();
        parentInterface.parentInterfaceFunc(name, age);

        return "level2_4Func";
    }

    public String level2_5Func() {
        System.out.println("here, Level2.level2_5Func");

        FifthLevel3 fifthLevel3 = new FifthLevel3();
        fifthLevel3.fifthLevel3Func();

        return "level2_5Func";
    }

    public String level2_6Func() {
        System.out.println("here, Level2.level2_6Func");

        ArrayList<String> arrayList = new ArrayList<>();
        arrayList.add("abc");
        arrayList.stream().filter(StringUtils::isNotBlank).collect(Collectors.toList());

        return "level2_6Func";
    }
}
