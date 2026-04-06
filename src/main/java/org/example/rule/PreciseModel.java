package org.example.rule;

public enum PreciseModel {
    /*
    常规模式：
    - 只管项目中的方法调用，不看 jdk 依赖，不看第三方依赖
     */
    NORMAL,

    /*
    警告模式：可能导致内存溢出
    - 识别项目中的方法调用和第三方依赖的方法调用，不看 jdk 依赖
     */
    WARN_MOD,

    /*
    危险模式：很可能导致内存溢出
    - 识别项目中的所有方法调用，包括 jdk 依赖和第三方依赖
     */
    DANGER_MOD,
}
