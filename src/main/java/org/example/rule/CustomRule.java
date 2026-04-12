package org.example.rule;

import java.util.List;

import org.example.util.StringUtil;

import lombok.Getter;

/**
 * 自定义规则
 * 1. 模式
 * 2. 最大层数
 * 3. 需要舍弃的类
 * 4. 需要包含的类
 */
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

    /**
     * 通过 thrownClasses 和 filterClasses 检查是否应该构造该节点
     * 
     * @param realClassName 全限定实际类名
     * @return 是否应该构造节点
     */
    @Override
    public boolean shouldCreateNode(String realClassName) {
        // 首先检查是否在抛出列表中
        if (isInThrownClasses(realClassName, getThrownClasses())) {
            return false;
        }

        // 再检查是否在过滤列表中
        if (!isInFilterClasses(realClassName, getFilterClasses())) {
            return false;
        }

        return true;
    }

    /**
     * 检查类是否在抛出列表中
     * 
     * @param realClassName 全限定类名
     * @return 是否在抛出列表中
     */
    private boolean isInThrownClasses(String realClassName, List<String> thrownClasses) {
        if (thrownClasses == null || thrownClasses.isEmpty()) {
            return false;
        }
        for (String pattern : thrownClasses) {
            if (StringUtil.matchesPattern(realClassName, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查类是否在过滤列表中
     * 
     * @param realClassName 全限定类名
     * @return 是否在过滤列表中
     */
    private boolean isInFilterClasses(String realClassName, List<String> filterClasses) {
        if (filterClasses == null || filterClasses.isEmpty()) {
            return true; // 过滤列表为空，默认包含所有类
        }
        for (String pattern : filterClasses) {
            if (StringUtil.matchesPattern(realClassName, pattern)) {
                return true;
            }
        }
        return false;
    }

}
