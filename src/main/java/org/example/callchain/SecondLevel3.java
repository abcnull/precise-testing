package org.example.callchain;

import org.example.callchain.parentclass.ParentClass;

/**
 * SecondLevel3 类的注释
 */
@SuppressWarnings("unchecked")
public class SecondLevel3 extends ParentClass {

    /**
     * secondLevel3_1Func 方法的注释
     * 
     * @param name 姓名
     * @param age  年龄
     * @return 方法返回值
     */
    public String secondLevel3_1Func(String name, int age) {
        System.out.printf("here secondLevel3_1Func, name: {}, age: {}\n", name, age);

        return "secondLevel3_1Func";
    }

    /**
     * SecondLevel3.secondLevel3_2Func 方法的注释
     * 
     * @param name 姓名
     * @return 方法返回值
     */
    @Override
    public String secondLevel3_2Func(String name) {
        System.out.printf("here secondLevel3_2Func, name: {}\n", name);
        return super.secondLevel3_2Func(name);
    }

    
}
