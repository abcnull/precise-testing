package org.example.resolver.model;

/**
 * 方法来源的类
 */
public enum MethodBelongs2Class {
    // 多态下 B b = new C(); 的 C
    SELF_CLASS,
    // 多态下 B b = new C(); 的 B
    PARENT_CLASS,
    // 多态下 B b = new C(); 的 A，因为 B 的父辈是 A
    ANCESTOR_CLASS,
    // 暂不知道来源谁
    UNKNOWN;
}
