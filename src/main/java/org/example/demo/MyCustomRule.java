package org.example.demo;

import java.util.ArrayList;

import org.example.rule.CustomRule;
import org.example.rule.PreciseModel;

// 继承 CustomRule 抽象类来实现自己的过滤规则
public class MyCustomRule extends CustomRule {
    // 自定义规则：调用最大层数限制
    @Override
    public void setMaxLayer() {
        // TODO Auto-generated method stub
        super.maxLayer = 20;
    }

    // 自定义规则：项目中的，第三方依赖，jdk
    @Override
    public void setPreciseModel() {
        // TODO Auto-generated method stub
        super.preciseModel = PreciseModel.DANGER_MOD;
    }

    // 自定义规则：设置过滤的类白名单
    @Override
    public void setFilterClasses() {
        // TODO Auto-generated method stub
        super.filterClasses = new ArrayList<>();
    }

    // 自定义规则：设置过滤的类黑名单
    @Override
    public void setThrownClasses() {
        // TODO Auto-generated method stub
        super.thrownClasses = new ArrayList<>();
    }
}
