package org.example.demo.callchain;

public class Level2 implements ILevel2 {
    // 循环调用
    public void level2_func4() {
        Level1 level1 = new Level1();
        level1.level1_func4();
    }

    @Override
    public void level2_func6() {
        // TODO Auto-generated method stub
        System.out.println("hello");
    }

    /**
     * 注解，注释，返回值，传参等
     * 
     * @param arg1 参数1
     * @param arg2 参数2
     * @return 返回值
     */
    @SuppressWarnings("level2_ann")
    public String level2_func7(String arg1, int arg2) {
        return "hi";
    }

    // 混合复杂场景
    public void level2_func8() {
        Level3 level3 = new Level3();
        System.out.println("hello");
        if (true) {
            level3.level3_func8(true);
        }
    }

}
