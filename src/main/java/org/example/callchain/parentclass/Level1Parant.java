package org.example.callchain.parentclass;

import org.apache.commons.lang3.StringUtils;

public class Level1Parant {
    /**
     * sdfdsadfd
     * @param a
     * @return
     */
    @Deprecated
    public Integer getLevel1Parent(String a) {
        test_leve1(null);
        return 1;
    }

    public String test_leve1(StringUtils a) {
        return "test_leve1";
    }
}
