package org.example.rule;

import java.util.List;

import lombok.Getter;

/**
 * 常规模式的规则（推荐）：
 * 1. 模式：PreciseModel.NORMAL
 * 2. 最大层数：20
 * 3. 需要舍弃的类
 * 4. 需要包含的类
 */
@Getter
public class NormalRule implements IPreciseRule {
    // 模式
    private final PreciseModel preciseModel = PreciseModel.NORMAL;

    private final int maxLayer = 20;

    // 需要舍弃的类
    private final List<String> thrownClasses = null;

    // 需要包含的类
    private final List<String> filterClasses = null;

    /**
     * 通过 thrownClasses 和 filterClasses 检查是否应该构造该节点
     * 
     * @param realClassName 全限定实际类名
     * @return 是否应该构造节点
     */
    @Override
    public boolean shouldCreateNode(String realClassName) {
        return true;
    }
}
