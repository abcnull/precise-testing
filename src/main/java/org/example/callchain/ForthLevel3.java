package org.example.callchain;

import org.example.callchain.parentclass.ParentInterface;

public class ForthLevel3 implements ParentInterface {
    @Override
    public String parentInterfaceFunc(String name, int age) {
        System.out.printf("here parentInterfaceFunc, name: {}, age: {}\n", name, age);
        return "parentInterfaceFunc";
    }
}
