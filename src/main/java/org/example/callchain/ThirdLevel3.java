package org.example.callchain;

import org.example.callchain.parentclass.ParentAbstract;

/**
 * ThirdLevel3 类
 */
public class ThirdLevel3 extends ParentAbstract {
    
    /**
     * ThirdLevel3.parentAbstractFunc 方法
     */
    @Override
    public void parentAbstractFunc(String name) {
        System.out.println("here, ThirdLevel3.parentAbstractFunc");
    }
}
