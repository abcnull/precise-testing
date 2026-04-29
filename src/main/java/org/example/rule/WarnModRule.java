package org.example.rule;

import java.util.List;
import java.util.Set;

import lombok.Getter;

/**
 * 警告模式的规则
 * 1. 模式：PreciseModel.WARN_MOD
 * 2. 最大层数：20
 * 3. 需要舍弃的类
 * 4. 需要包含的类
 */
@Getter
public class WarnModRule implements IPreciseRule {
    // 模式
    private final PreciseModel preciseModel = PreciseModel.WARN_MOD;

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
    public boolean shouldCreateNode(String realClassName, Set<String> allPackStr, int currentLayer) {
        return true;
    }
}
