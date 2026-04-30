package org.example.rule;

import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.example.constant.PathConstant;

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
        // 判断层级
        if (currentLayer > maxLayer) {
            return false;
        }

        // 只要是 jdk 的就不行
        if (StringUtils.isNotBlank(realClassName)
                && (realClassName.trim().startsWith(PathConstant.JAVA_DOT_PREFIX)
                        || realClassName.trim().startsWith(PathConstant.JAVAX_DOT_PREFIX)
                        || realClassName.startsWith(PathConstant.ORG_DOT_W3C_DOT_DOM_DOT)
                        || realClassName.startsWith(PathConstant.ORG_DOT_XML_DOT_SAX_DOT))) {
            return false;
        }

        return true;
    }
}
