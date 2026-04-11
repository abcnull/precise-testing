package org.example.callchain.parentclass;

/**
 * ParentClass 类的注释
 */
@SuppressWarnings("unchecked")
public class ParentClass {
    private String parentName;

    /**
     * ParentClass.secondLevel3_2Func 方法的注释
     * 
     * @param name 姓名
     * @return 方法返回值
     */
    public String secondLevel3_2Func(String name) {
        System.out.println("here secondLevel3_2Func, name: " + name);
        return "secondLevel3_2Func";
    }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public String testMyParent(String name) {
        return "nihao";
    }


    public String testMyChild(String name) {
        return "nihao111";
    }
}
