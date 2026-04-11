package org.example.rule;

import java.util.List;

import lombok.Getter;

@Getter
public class NormalRule implements IPreciseRule {
    // 模式
    private final PreciseModel preciseModel = PreciseModel.NORMAL;

    private final int maxLayer = 20;

    // 需要舍弃的类
    private final List<String> thrownClasses = null;

    // 需要包含的类
    private final List<String> filterClasses = null;
}
