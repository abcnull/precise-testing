package org.example.callchain2;

import java.util.Arrays;
import java.util.List;

import org.example.rule.CustomRule;
import org.example.rule.PreciseModel;

public class MyCustomRule extends CustomRule {
    /**
     * 构造函数，设置默认值
     */
    public MyCustomRule() {
        // 默认使用 NORMAL 模式
        setPreciseModel(PreciseModel.NORMAL);
        // 默认最大层数为 20
        setMaxLayer(20);
        // 设置需要舍弃的类
        setThrownClasses(Arrays.asList());
        // 设置需要包含的类
        setFilterClasses(Arrays.asList());
    }

    /**
     * 设置模式
     * 
     * @param preciseModel 模式
     */
    @Override
    public void setPreciseModel(PreciseModel preciseModel) {
        super.preciseModel = preciseModel;
    }

    /**
     * 设置最大层数
     * 
     * @param maxLayer 最大层数
     */
    @Override
    public void setMaxLayer(int maxLayer) {
        super.maxLayer = maxLayer;
    }

    /**
     * 设置需要舍弃的类
     * 
     * @param thrownClasses 需要舍弃的类列表
     */
    @Override
    public void setThrownClasses(List<String> thrownClasses) {
        super.thrownClasses = thrownClasses;
    }

    /**
     * 设置需要包含的类
     * 
     * @param filterClasses 需要包含的类列表
     */
    @Override
    public void setFilterClasses(List<String> filterClasses) {
        super.filterClasses = filterClasses;
    }
}