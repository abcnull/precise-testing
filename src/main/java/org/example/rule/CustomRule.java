package org.example.rule;

import java.util.List;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.example.constant.PathConstant;
import org.example.util.StringUtil;

import com.google.common.base.Objects;

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
    protected PreciseModel preciseModel = PreciseModel.NORMAL;

    // 最大层数
    protected int maxLayer;

    // 需要舍弃的类
    protected List<String> thrownClasses;

    // 需要包含的类
    protected List<String> filterClasses;

    public CustomRule() {
        setPreciseModel();
        setMaxLayer();
        setThrownClasses();
        setFilterClasses();
    }

    abstract public void setPreciseModel();

    abstract public void setMaxLayer();

    abstract public void setThrownClasses();

    abstract public void setFilterClasses();

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
        if (currentLayer > getMaxLayer()) {
            return false;
        }

        // 首先检查是否在抛出列表中
        if (isInThrownClasses(realClassName, getThrownClasses())) {
            return false;
        }

        // 再检查是否在过滤列表中
        if (!isInFilterClasses(realClassName, getFilterClasses())) {
            return false;
        }

        // 判断包是否要过滤
        Boolean isPackAllowed;
        if (preciseModel == PreciseModel.NORMAL) {
            isPackAllowed = false;
            // 最严过滤: 只允许项目中的类生成
            // 无法区分
            if (StringUtils.isBlank(realClassName) || !realClassName.contains(PathConstant.DOT)) {
                isPackAllowed = true;
            }
            // 遍历项目中每一个包
            if (!CollectionUtils.isEmpty(allPackStr)) {
                for (String packStr : allPackStr) {
                    if (StringUtils.isBlank(packStr)) {
                        continue;
                    }
                    if (StringUtils.isNotBlank(realClassName) && realClassName.trim().startsWith(packStr.trim())) {
                        isPackAllowed = true;
                        break;
                    }
                }
            }
        } else if (preciseModel == PreciseModel.WARN_MOD) {
            isPackAllowed = true;
            // 相对宽松过滤：允许项目中的类和第三方依赖生成
            // 只要是 jdk 的就不行
            if (StringUtils.isNotBlank(realClassName)
                    && (realClassName.trim().startsWith(PathConstant.JAVA_DOT_PREFIX)
                            || realClassName.trim().startsWith(PathConstant.JAVAX_DOT_PREFIX)
                            || realClassName.startsWith(PathConstant.ORG_DOT_W3C_DOT_DOM_DOT)
                            || realClassName.startsWith(PathConstant.ORG_DOT_XML_DOT_SAX_DOT))) {
                isPackAllowed = false;
            }
        } else if (preciseModel == PreciseModel.DANGER_MOD) {
            // 最宽松过滤
            isPackAllowed = true;
        } else {
            throw new IllegalArgumentException("preciseModel 不能为空");
        }
        if (Objects.equal(isPackAllowed, false)) {
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
        if (StringUtils.isBlank(realClassName)) {
            return false;
        }
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
        if (StringUtils.isBlank(realClassName)) {
            return true; // 空类名，默认包含所有类
        }
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
