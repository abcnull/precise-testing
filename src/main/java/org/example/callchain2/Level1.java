package org.example.callchain2;

import org.apache.commons.lang3.StringUtils;

@Deprecated
public class Level1 {
    /**
     * dsfaa
     * dafafaddd到底发生的
     */

    /*
    
    @SuppressWarnings(value = "a")
    
    */ 
    public void level1Func() {
        System.out.println(123);
        StringUtils.isNotBlank("342");
        Level2 level2 = new Level2("");
        level2.level2Func();
    }
/*
    @SuppressWarnings(value = "b")
    public void level1Func2(String a, int b) {
        System.out.println(456);
    }
         */
}

