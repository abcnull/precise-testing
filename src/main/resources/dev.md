# CallChainResolver 设计结构与重构方案

## 1. 当前设计结构分析

### 1.1 核心功能
CallChainResolver 是一个用于解析 Java 方法调用链的工具，主要功能包括：
- 从指定方法开始，递归解析调用链
- 支持多态情况下的方法解析
- 处理循环调用
- 提供节点过滤机制（基于规则）
- 支持合并多个调用链
- 提供查找叶子节点和特定节点的功能
- 支持反向生成调用链字符串

### 1.2 代码结构
当前 CallChainResolver 类包含以下主要部分：

1. **成员变量**：
   - sourceRootPath：项目根目录
   - javaParser：用于解析 Java 源文件
   - parsedFiles：已解析的文件缓存
   - visitedMethods：已访问的方法签名集合
   - nodeCache：节点缓存
   - preciseRule：精确规则，用于过滤调用链路

2. **构造方法**：
   - 支持默认规则和自定义规则

3. **核心方法**：
   - resolveCallChain：从指定方法开始解析调用链路
   - resolveMethodCall：递归解析方法调用
   - extractMethodCalls：提取方法体内的所有方法调用
   - createCycleNode：创建循环调用节点
   - createLeafNode：创建叶节点
   - mergeCallChain：合并多个调用链
   - findLeaf：查找叶子节点
   - findNode：查找符合条件的节点
   - reverseCallChainStr：生成从节点到出口节点的所有可能链路

4. **辅助方法**：
   - generateNodeKey：生成节点唯一标识
   - buildMethodSignature：构建方法签名
   - getPackageName：获取包名
   - getSimpleClassName：获取简单类名
   - findFullClassNameFromImports：从导入语句中查找类的完整包名
   - extractClassInfo：提取类结构信息
   - extractFuncInfo：提取方法信息
   - extractAnnotations：提取注解信息
   - extractParameterInfo：提取参数信息
   - extractReturnTypeInfo：提取返回值信息

5. **内部类**：
   - MethodCallInfo：方法调用信息
   - ParameterInfo：参数信息
   - ReturnTypeInfo：返回值信息

### 1.3 存在的问题
1. **代码过于庞大**：单个类文件超过 2000 行，包含多个职责
2. **职责不清晰**：既包含解析逻辑，又包含节点操作和工具方法
3. **可维护性差**：方法之间耦合度高，难以理解和修改
4. **可扩展性差**：新增功能需要修改原有代码，容易引入 bug

## 2. 重构方案

### 2.1 重构目标
- 降低类的复杂度，提高代码可读性
- 明确职责划分，提高代码可维护性
- 提高代码可扩展性，便于后续功能增强
- 保持原有功能不变

### 2.2 重构策略
采用分层架构，将不同职责的代码分离到不同的类中：

1. **核心解析器**：负责整体调用链解析逻辑
2. **节点工厂**：负责创建和管理不同类型的节点
3. **信息提取器**：负责从源码中提取类、方法、注解等信息
4. **工具类**：提供通用工具方法
5. **数据模型**：定义内部使用的数据结构

### 2.3 重构后的包结构
```
org.example.resolver
├── CallChainResolver.java       # 核心解析器
├── factory/
│   ├── NodeFactory.java         # 节点工厂
│   └── CycleNodeFactory.java    # 循环节点工厂
├── extractor/
│   ├── InfoExtractor.java       # 信息提取器接口
│   ├── ClassInfoExtractor.java  # 类信息提取器
│   ├── MethodInfoExtractor.java # 方法信息提取器
│   └── AnnotationExtractor.java # 注解提取器
├── util/
│   ├── ParserUtil.java          # 解析工具
│   ├── StringUtil.java          # 字符串工具
│   └── TypeUtil.java            # 类型工具
└── model/
    ├── MethodCallInfo.java      # 方法调用信息
    ├── ParameterInfo.java       # 参数信息
    └── ReturnTypeInfo.java      # 返回值信息
```

### 2.4 重构详细计划

1. **创建数据模型**：
   - 将内部类 MethodCallInfo、ParameterInfo、ReturnTypeInfo 提取为独立的类

2. **创建工具类**：
   - 提取字符串处理和类型处理相关方法到工具类

3. **创建信息提取器**：
   - 提取类信息、方法信息、注解信息的相关方法到专门的提取器类

4. **创建节点工厂**：
   - 提取节点创建相关方法到工厂类

5. **重构核心解析器**：
   - 保留核心解析逻辑，调用其他组件完成具体功能

6. **测试验证**：
   - 确保重构后的代码功能与原代码一致

## 3. 重构实施步骤

1. 创建 model 包，移动数据模型类
2. 创建 util 包，提取工具方法
3. 创建 extractor 包，实现信息提取器
4. 创建 factory 包，实现节点工厂
5. 重构 CallChainResolver 类，调用新创建的组件
6. 运行测试，验证功能正确性

## 4. 预期效果

- 代码结构清晰，职责分明
- 单个类文件大小合理，易于理解和维护
- 组件之间低耦合，高内聚
- 功能保持不变，性能不劣化
- 便于后续功能扩展和维护