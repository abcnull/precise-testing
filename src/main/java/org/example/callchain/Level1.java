package org.example.callchain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Strings;
import com.google.common.primitives.Chars;

import javafx.beans.binding.StringBinding;

import org.example.callchain.parentclass.Level1Parant;
import org.example.callchain.parentclass.ParentClass;
import org.example.callchain.parentclass.ParentInterface;
import org.example.util.StringUtil;
import org.example.callchain.FanXing;
import org.example.callchain.ForthLevel3;
import org.example.callchain.demoenum.DemoEnum;

/**
 * 测试类 Level1
 * 
 * @author abcnull
 * @date 2023/12/12
 */
public class Level1 extends Level1Parant {
    private Level2 level2 = new Level2("", 2);
    /**
     * ddddd
     * 
     * @param name
     * @param age
     * @return
     */
    public String level1Func(String name, int age) {

        // System.out.println("level1Func");


        // 测试普通类
        /*
         * 测试完全普通的类
         */
        // org.example.callchain.Level2 level2 = new org.example.callchain.Level2(name, age);
        // java.lang.String str = level2.myTest(name);

        /*
         * 简单父子类
         * 2.❌ SecondLevel3 secondLevel3 = new SecondLevel3();
         * 子类继承正常父类，子类用父类的方法（该方法子类中没有），FuncInfo 整个拿不到。后头看看这块要改吗，可能不需要改，可能 FuncInfo
         * 🌞🌞 增加个字段判断方法是否属于本体
         * 3.❌ ParentClass secondLevel3 = new SecondLevel3(); 子类继承父类，多态，用父类的方法，FuncInfo
         * 为空
         * 原因：因为分析的是子类，考虑 FuncInfo 要不要再加一个字段
         * 可以考虑加
         */
        // ParentInterface forthLevel3 = new ForthLevel3();
        // forthLevel3.parentInterfaceFunc("", 1);
        // ParentClass secondLevel3 = new SecondLevel3();
        // secondLevel3.equals("");

        /*
         * 枚举类型
         */
        // DemoEnum.ENUM1.getName();

        /*
         * jdk 依赖
         */
        // String a = new String(new byte[0], 0);
        // String.valueOf(23);

        /*
         * 第三方依赖
         * 7.❌ StringUtils.isBlank("dsf");其中该方法返回类型没有，其中该方法修饰符为空
         * 原因：List<MethodCallInfo>
         * methodCalls解析方法内到调用关系时，入参是String，javaparser只能解析到此，但是第三方依赖是入参类型是CharSequence，
         * 导致后头通过反射找不到该方法
         * 不好解决
         */
        // CharSequence d = "dsf";
        // StringUtils.isBlank(d);

        /*
         * lambda 表达式
         */
        // String[] arr = { "", "1", "2" };
        // List<String> list = Arrays.stream(arr)
        //         .filter(e -> StringUtils.isBlank(e))
        //         .collect(Collectors.toList());

        /*
         * 其他：
         * - 试下带有包名的写法
         * - 试下泛型的情况
         * 8.❌ 如果方法入参和方法返回值，类带有泛型，在结构中都会给去掉，未来看看这块可以新增结构来带上
         * - 试下和白名单的类的过滤
         * - 测试下一个大的项目的输出情况
         * 9.❌ 如果变量声明在class的field中，然后在方法中使用者field这个数据，调用里头的方法，这个方法就为空了
         * 🌞 原因：因为javaparser解析一个方法里头的有哪些调用的方法时候，没有分析出这个被调用方法来自的类型
         * 10.❌ clickButton(a.getFocusBtn());这个clickButton方法FuncInfo中的参数a.getFocusBtn()解析出来的参数名是getFocusBtn()，参数的包名a，严重不符合预期
         * 🌞🌞 原因：需要专门看下javaparser解析一个方法内所有的调用List数据时候的处理逻辑，需要优化
         * - 整体结构再优化下：删除不要；部分方法可以提取出来 🤔
         * - 测试项目中merge等方法；测试包弄一下；写 github 文章
         * - 看怎么感知运行时候jvm消耗多少所占内存多大？
         * - 测试main中传入的数据的那个方法怎么优化，以及可以优化参数，参数必须支持识别带有包名的参数
         */
        // FanXing<Level2> fanxinging = new FanXing();
        // fanxinging.end(fanxinging);

        /**
         * 问题：
         * 1.如果被调用方法入参类型是第三方依赖类型，则分析不出来该被调用方法的参数类型
         * 2.如果被调用方法入参中含有第三方依赖的表达式，比如StringUtils.isBlank("d")，也分析不出来该被调用方法的参数类型
         * 3.
         */


        /**
         * 测试用例：func(参数);
         * 测试用例：super.func(参数); this.func(参数);
         * 测试用例：父func(参数)
         * 测试用例：类属性.func(参数)
         */
        // Level3 level3 = new Level3();
        // // Level2 level2 = new Level2("");
        // level3.test_level3(Integer.valueOf(12));

        // Level1 level1 = new Level1();
        // ParentInterface parentInterface = new ForthLevel3();


        // if (true) {
        //     String level3 = "";
        // }

        // org.example.callchain2.Level3 level3 = new org.example.callchain2.Level2("");

        // level3.level3Func(true);
        // level3.level3Func(StringUtils.isBlank("d"));

        // StringUtils.isBlank("");

        // parentInterface.parentInterfaceFunc(StringUtils.strip(""), 2);
        // test_leve1(StringUtils.isBlank("d"));

        // super.getLevel1Parent(test_leve1(true));
        // level2.myTest("3232");
        // StringUtils a = new StringUtils();
        // Level2 a = new Level2("l", 0);
        // test_boolean(StringUtils.isBlank("d"));
        // StringUtils.isBlank("d");
        // ParentInterface a = new ForthLevel3();
        // a.parentInterfaceFunc(StringUtils.strip(""), 2);
        // level2.test_leve1("");
        // Level1Parant a = new Level1();
        // a.getLevel1Parent("");
        // StringUtils.isBlank("e3");

        /**
         * 🌞🌞 测试直接在方法中写父类方法，参数用了第三方依赖，看下为啥不行
         */


        /**
         * 方法的注解做依赖注入
         */
        

        // System.out.printf("here level1Func, name: {}, age: {}\n", name, age);

        // org.example.callchain2.Level2 level22 = new
        // org.example.callchain2.Level2("");
        // level22.level2Func();

        Level2 level2 = new Level2(name, age);
        // // 测试基础的方法调用链路分析
        level2.level2_1Func(name, age).equals("23");
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

    // public String test_leve1(StringUtils a) {
    //     return "test_leve1";
    // }

    public Boolean test_boolean(Boolean a) {
        return true;
    }
}
