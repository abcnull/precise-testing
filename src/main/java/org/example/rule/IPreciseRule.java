package org.example.rule;

import java.util.List;
import java.util.Set;

/**
 * 精确测试规则的接口
 */
public interface IPreciseRule {
    PreciseModel getPreciseModel();

    int getMaxLayer();

    List<String> getThrownClasses();

    List<String> getFilterClasses();

    boolean shouldCreateNode(String className, Set<String> allPackStr, int currentLayer);
}
