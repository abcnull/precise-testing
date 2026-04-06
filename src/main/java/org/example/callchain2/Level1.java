package org.example.callchain2;

import org.apache.commons.lang3.StringUtils;

public class Level1 {
    public void level1Func() {
        System.out.println(123);
        StringUtils.isNotBlank("342");
        Level2 level2 = new Level2();
        level2.level2Func();
    }
}
