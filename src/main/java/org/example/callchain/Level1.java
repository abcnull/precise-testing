package org.example.callchain;

import org.apache.commons.lang3.StringUtils;

import com.google.common.primitives.Chars;
import org.example.callchain.parentclass.ParentClass;
import org.example.callchain.parentclass.ParentInterface;

/**
 * 测试类 Level1
 * 
 * @author abcnull
 * @date 2023/12/12
 */
public class Level1 {
    /**
     * ddddd
     * @param name
     * @param age
     * @return
     */
    public String level1Func(String name, int age) {

        // 测试普通类
        /*
        测试完全普通的类
        1.❌ 构造器的方法注释没有拿到
        原因：因为构造器转换为方法，注释丢失了，所以最后要改造，生成的是通用类型，而不是方法类型
         */
        Level2 level2 = new Level2(name, age);
        // level2.myTest(name);


        /*
        简单父子类
        2.❌ SecondLevel3 secondLevel3 = new SecondLevel3(); 子类继承正常父类，子类用父类的方法（该方法子类中没有），FuncInfo 整个拿不到。后头看看这块要改吗，可能不需要改，可能 FuncInfo 增加个字段判断方法是否属于本体
        3.❌ ParentClass secondLevel3 = new SecondLevel3(); 子类继承父类，多态，用父类的方法，FuncInfo 为空
        原因：因为分析的是子类，考虑 FuncInfo 要不要再加一个字段
        建议先不加
         */
        // ParentInterface forthLevel3 = new ForthLevel3();
        // forthLevel3.parentInterfaceFunc("", 1);
        // ParentClass secondLevel3 = new SecondLevel3();
        // secondLevel3.equals("");



        /*
        枚举类型
         */
        // DemoEnum.ENUM1.getName();

        /*
        jdk 依赖
         */
        // String a = new String(new byte[0], 0);
        // String.valueOf(23);

        /*
        第三方依赖
        7.❌ StringUtils.isBlank("dsf");其中该方法返回类型没有，其中该方法修饰符为空
        原因：List<MethodCallInfo> methodCalls解析方法内到调用关系时，入参是String，javaparser只能解析到此，但是第三方依赖是入参类型是CharSequence，导致后头通过反射找不到该方法
        不好解决
         */
        // CharSequence d = "dsf";
        // StringUtils.isBlank(d);

        /*
        lambda 表达式
         */

        /*
        其他：
        - 试下接口类
        - 试下带有包名的写法
        - 试下泛型的情况
        - 试下和白名单的类的过滤
        - 试下其他方法
         */






        // System.out.printf("here level1Func, name: {}, age: {}\n", name, age);

        // org.example.callchain2.Level2 level22 = new org.example.callchain2.Level2("");
        // level22.level2Func();

        // Level2 level2 = new Level2(name, age);
        // // 测试基础的方法调用链路分析
        // level2.level2_1Func(name, age);
        // // 测试类 extends
        // level2.level2_2Func(name);
        // // 测试类 extends abstract 类
        // level2.level2_3Func(name);
        // // 测试类 implements 接口
        // level2.level2_4Func(name, age);
        // // 测试类 enum
        // level2.level2_5Func();
        // // 测试类 lambda 表达式和函数式接口
        // level2.level2_6Func();

        // String.valueOf(123);

        // StringUtils.isBlank(name);

        // ClassDeclaration.INTERFACE.asString();

        return "level1Func";
    }
}
