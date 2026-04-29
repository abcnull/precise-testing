package org.example.resolver.model;

/**
 * 方法来源的类
 */
public enum MethodBelongs2Class {
    // 来自方法的实现类（但是静态代码分析时，很多情况并不真的知道实现类具体是啥，很多情况都是基于目前代码猜测出最有可能的实现类）
    // 比如实际方法来自多态下 B b = new C(); 的 C
    REAL_CLASS,
    
    // 来自方法的声明类
    // 比如实际方法来自多态下 B b = new C(); 的 B
    DECL_CLASS,

    // 来自方法的某个祖先类
    // 实际方法来自多态下 B b = new C(); 的 A，因为 B 的父辈是 A
    ANCESTOR_CLASS,

    // 无法判定，暂不知道方法来源谁
    UNKNOWN;
}
