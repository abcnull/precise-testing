package org.example.rule;

import java.util.List;

/**
 * 精确测试模式的接口
 */
public interface IPreciseRule {
    PreciseModel getPreciseModel();

    int getMaxLayer();

    List<String> getThrownClasses();

    List<String> getFilterClasses();
}
