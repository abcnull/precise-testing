package org.example.rule;

import java.util.List;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.example.constant.PathConstant;

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
     * @param allPackStr    所有包路径
     * @param currentLayer  当前层级
     * @return 是否应该构造节点
     */
    @Override
    public boolean shouldCreateNode(String realClassName, Set<String> allPackStr, int currentLayer) {
        // 判断层级
        if (currentLayer > maxLayer) {
            return false;
        }

        // 判断包是否要过滤
        if (StringUtils.isBlank(realClassName) || !realClassName.contains(PathConstant.DOT)) {
            return true;
        }
        // 遍历项目中每一个包
        if (!CollectionUtils.isEmpty(allPackStr)) {
            for (String packStr : allPackStr) {
                if (StringUtils.isBlank(packStr)) {
                    continue;
                }
                if (StringUtils.isNotBlank(realClassName) && realClassName.trim().startsWith(packStr.trim())) {
                    return true;
                }
            }
        }

        return false;
    }
}
