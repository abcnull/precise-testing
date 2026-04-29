package org.example.callchain2;

/**
 * 二级类
 */
@Deprecated
public class Level2 extends Level3 {

    /**
     * 构造器
     */
    @Deprecated
    public Level2(String name) {
        System.out.println(name);
    }

    /**
     * 二级方法
     * @return
     */
    @SuppressWarnings("level2Func")
    public String level2Func() {
        Level1 level1 = new Level1();
        level1.level1Func();
        return "";
    }
}
