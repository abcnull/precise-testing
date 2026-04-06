package org.example.rule;

import java.util.List;

import lombok.Getter;

@Getter
public class DangerModRule implements IPreciseRule {
    // 模式
    private final PreciseModel preciseModel = PreciseModel.DANGER_MOD;

    private final int maxLayer = Integer.MAX_VALUE;

    // 需要舍弃的类
    private final List<String> thrownClasses = null;

    // 需要包含的类
    private final List<String> filterClasses = null;
}
