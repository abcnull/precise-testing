package org.example.rule;

import java.util.List;

import lombok.Getter;

@Getter
public abstract class CustomRule implements IPreciseRule {
    // 模式
    protected PreciseModel preciseModel;

    // 最大层数
    protected int maxLayer;

    // 需要舍弃的类
    protected List<String> thrownClasses;

    // 需要包含的类
    protected List<String> filterClasses;

    abstract public void setPreciseModel(PreciseModel preciseModel);

    abstract public void setMaxLayer(int maxLayer);

    abstract public void setThrownClasses(List<String> thrownClasses);

    abstract public void setFilterClasses(List<String> filterClasses);
}
