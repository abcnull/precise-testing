# CallChainResolver 白盒测试文档

## 1. 测试方法列表

| 方法名 | 描述 | 参数 | 返回值 |
|-------|------|------|-------|
| resolveCallChain | 从指定方法开始解析调用链路 | className: 类全限定名<br>methodName: 方法名<br>paramTypes: 参数类型列表 | AstNode 调用链树根节点 |
| mergeCallChain | 合并两个方法调用有根DAG为一个更大的无环图 | node1: 第一个DAG的根节点<br>node2: 第二个DAG的根节点 | void |
| mergeCallChain | 合并多个有根DAG为一个更大的有向无环图 | nodes: 多个有根DAG的根节点列表 | void |
| findLeaf | 查找所有叶子节点并去重 | root: 根节点 | Set<AstNode> 去重后的叶子节点集合 |
| findLeaf | 查找符合条件的叶子节点并去重 | root: 根节点<br>packageName: 包名<br>realClassName: 真实类名<br>funcName: 方法名<br>params: 方法参数 | Set<AstNode> 符合条件的叶子节点集合 |
| findNode | 查找符合条件的所有节点并去重 | root: 根节点<br>packageName: 包名<br>realClassName: 真实类名<br>funcName: 方法名<br>params: 方法参数 | Set<AstNode> 符合条件的节点集合 |
| reverseCallChainStr | 生成从节点到出口节点的所有可能链路 | node: 起始节点 | List<String> 链路字符串列表 |
| reverseLeavesName | 获取从节点到出口节点的所有链路的出口节点信息 | node: 起始节点 | List<String> 出口节点信息列表 |
| matchesPattern | 检查类名是否匹配模式（支持通配符） | className: 类名<br>pattern: 模式 | boolean 是否匹配 |

## 2. 测试用例设计

### 2.1 resolveCallChain 方法测试

**测试要点**：
- 正常情况下能够正确解析调用链
- 处理循环调用的情况
- 处理无法解析的类或方法的情况
- 处理达到最大层数的情况
- 处理不同规则模式下的过滤情况

**测试用例**：
1. **正常解析**：传入项目中存在的类和方法，验证返回的调用链是否正确
2. **循环调用**：传入存在循环调用的方法，验证是否正确标记循环节点
3. **无法解析的类**：传入不存在的类，验证是否返回叶节点
4. **无法解析的方法**：传入存在的类但不存在的方法，验证是否返回叶节点
5. **达到最大层数**：设置较小的最大层数，验证是否在达到层数时停止递归
6. **不同规则模式**：使用不同的规则模式（NORMAL、WARN_MOD、DANGER_MOD）测试过滤效果

### 2.2 mergeCallChain 方法测试

**测试要点**：
- 能够正确合并两个调用链
- 能够正确合并多个调用链
- 处理相同节点的去重
- 处理空列表的情况

**测试用例**：
1. **合并两个不同的调用链**：创建两个不同的调用链，合并后验证节点是否正确合并
2. **合并包含相同节点的调用链**：创建包含相同节点的两个调用链，合并后验证节点是否去重
3. **合并空列表**：传入空列表，验证是否正常执行
4. **合并包含null的列表**：传入包含null的列表，验证是否正常执行

### 2.3 findLeaf 方法测试

**测试要点**：
- 能够正确查找所有叶子节点
- 能够正确去重
- 能够根据条件查找叶子节点
- 处理空根节点的情况

**测试用例**：
1. **查找所有叶子节点**：创建一个调用链，验证返回的叶子节点集合是否正确
2. **根据条件查找叶子节点**：创建一个调用链，使用不同的条件查找叶子节点，验证结果是否正确
3. **空根节点**：传入null作为根节点，验证是否返回空集合
4. **没有叶子节点**：创建一个没有叶子节点的调用链（只有循环节点），验证是否返回空集合

### 2.4 findNode 方法测试

**测试要点**：
- 能够正确查找符合条件的所有节点
- 能够正确去重
- 处理空根节点的情况
- 处理不同条件组合的情况

**测试用例**：
1. **查找所有节点**：创建一个调用链，不设置条件，验证返回的节点集合是否正确
2. **根据不同条件组合查找节点**：使用包名、类名、方法名、参数等不同组合条件查找节点，验证结果是否正确
3. **空根节点**：传入null作为根节点，验证是否返回空集合
4. **没有符合条件的节点**：创建一个调用链，使用不存在的条件查找节点，验证是否返回空集合

### 2.5 reverseCallChainStr 方法测试

**测试要点**：
- 能够正确生成从节点到出口节点的所有可能链路
- 处理循环调用的情况
- 处理空节点的情况
- 处理没有父节点的节点的情况

**测试用例**：
1. **生成链路**：创建一个调用链，从中间节点生成链路，验证返回的链路字符串列表是否正确
2. **处理循环调用**：创建包含循环调用的调用链，验证是否正确处理循环情况
3. **空节点**：传入null作为节点，验证是否返回空列表
4. **没有父节点的节点**：传入没有父节点的节点，验证是否返回只包含该节点的列表

### 2.6 reverseLeavesName 方法测试

**测试要点**：
- 能够正确提取所有链路的出口节点信息
- 处理空节点的情况
- 处理没有父节点的节点的情况

**测试用例**：
1. **提取出口节点**：创建一个调用链，从中间节点提取出口节点信息，验证返回的出口节点信息列表是否正确
2. **空节点**：传入null作为节点，验证是否返回空列表
3. **没有父节点的节点**：传入没有父节点的节点，验证是否返回只包含该节点的列表

### 2.7 matchesPattern 方法测试

**测试要点**：
- 能够正确匹配精确类名
- 能够正确匹配包含通配符的模式
- 处理空字符串的情况
- 处理null的情况

**测试用例**：
1. **精确匹配**：使用精确的类名和模式进行匹配，验证结果是否正确
2. **通配符匹配**：使用包含通配符的模式进行匹配，验证结果是否正确
3. **不匹配的情况**：使用不匹配的类名和模式进行匹配，验证结果是否正确
4. **空字符串**：传入空字符串作为类名或模式，验证结果是否正确
5. **null值**：传入null作为类名或模式，验证结果是否正确

## 3. 测试执行步骤

1. **准备测试环境**：
   - 确保项目编译成功
   - 创建测试所需的测试类和方法

2. **执行测试用例**：
   - 对每个方法执行对应的测试用例
   - 记录测试结果

3. **验证测试结果**：
   - 检查测试结果是否符合预期
   - 验证重构后的功能是否与重构前一致

4. **记录问题**：
   - 如果发现问题，记录问题描述和复现步骤
   - 分析问题原因
   - 提出修复方案

## 4. 测试预期

- 所有测试用例都应该通过
- 重构后的功能应该与重构前一致
- 代码应该能够正确处理各种边界情况
- 性能应该不劣于重构前

## 5. 测试工具

- 使用 JUnit 进行单元测试
- 使用 Maven 执行测试
- 可以使用调试工具进行问题定位

## 6. 测试代码示例

### 6.1 matchesPattern 方法测试示例

```java
@Test
public void testMatchesPattern() {
    CallChainResolver resolver = new CallChainResolver("");
    
    // 精确匹配
    assertTrue(resolver.matchesPattern("org.example.Test", "org.example.Test"));
    assertFalse(resolver.matchesPattern("org.example.Test", "org.example.Other"));
    
    // 通配符匹配
    assertTrue(resolver.matchesPattern("org.example.Test", "org.example.*"));
    assertTrue(resolver.matchesPattern("org.example.callchain2.AAA", "org.example.callchain*"));
    assertFalse(resolver.matchesPattern("org.example.Test", "com.example.*"));
    
    // 空字符串和null
    assertFalse(resolver.matchesPattern("", "org.example.*"));
    assertFalse(resolver.matchesPattern("org.example.Test", ""));
    assertFalse(resolver.matchesPattern(null, "org.example.*"));
    assertFalse(resolver.matchesPattern("org.example.Test", null));
}
```

### 6.2 resolveCallChain 方法测试示例

```java
@Test
public void testResolveCallChain() {
    CallChainResolver resolver = new CallChainResolver("src/main/java");
    
    // 测试正常解析
    List<String> params = new ArrayList<>();
    AstNode root = resolver.resolveCallChain("org.example.test.TestClass", "testMethod", params);
    assertNotNull(root);
    
    // 测试循环调用
    // 假设有一个包含循环调用的类
    AstNode cycleRoot = resolver.resolveCallChain("org.example.test.CycleClass", "cycleMethod", params);
    assertNotNull(cycleRoot);
    // 验证是否包含循环节点
    boolean hasCycle = false;
    // 遍历节点检查
    // ...
    
    // 测试无法解析的类
    AstNode nonExistentClassRoot = resolver.resolveCallChain("org.example.NonExistent", "method", params);
    assertNotNull(nonExistentClassRoot);
    assertTrue(nonExistentClassRoot.isLeafNode());
    
    // 测试无法解析的方法
    AstNode nonExistentMethodRoot = resolver.resolveCallChain("org.example.test.TestClass", "nonExistentMethod", params);
    assertNotNull(nonExistentMethodRoot);
    assertTrue(nonExistentMethodRoot.isLeafNode());
}
```

### 6.3 mergeCallChain 方法测试示例

```java
@Test
public void testMergeCallChain() {
    CallChainResolver resolver = new CallChainResolver("src/main/java");
    
    // 创建两个调用链
    List<String> params = new ArrayList<>();
    AstNode root1 = resolver.resolveCallChain("org.example.test.TestClass1", "method1", params);
    AstNode root2 = resolver.resolveCallChain("org.example.test.TestClass2", "method2", params);
    
    // 合并两个调用链
    resolver.mergeCallChain(root1, root2);
    
    // 验证合并结果
    // ...
    
    // 合并多个调用链
    List<AstNode> nodes = new ArrayList<>();
    nodes.add(root1);
    nodes.add(root2);
    resolver.mergeCallChain(nodes);
    
    // 验证合并结果
    // ...
    
    // 测试空列表
    resolver.mergeCallChain(new ArrayList<>());
    
    // 测试包含null的列表
    nodes.add(null);
    resolver.mergeCallChain(nodes);
}
```

### 6.4 findLeaf 方法测试示例

```java
@Test
public void testFindLeaf() {
    CallChainResolver resolver = new CallChainResolver("src/main/java");
    
    // 创建调用链
    List<String> params = new ArrayList<>();
    AstNode root = resolver.resolveCallChain("org.example.test.TestClass", "testMethod", params);
    
    // 查找所有叶子节点
    Set<AstNode> leafNodes = resolver.findLeaf(root);
    assertNotNull(leafNodes);
    
    // 根据条件查找叶子节点
    Set<AstNode> filteredLeafNodes = resolver.findLeaf(root, "org.example.test", "TestClass", "testMethod", params);
    assertNotNull(filteredLeafNodes);
    
    // 测试空根节点
    Set<AstNode> emptyLeafNodes = resolver.findLeaf(null);
    assertTrue(emptyLeafNodes.isEmpty());
}
```

### 6.5 findNode 方法测试示例

```java
@Test
public void testFindNode() {
    CallChainResolver resolver = new CallChainResolver("src/main/java");
    
    // 创建调用链
    List<String> params = new ArrayList<>();
    AstNode root = resolver.resolveCallChain("org.example.test.TestClass", "testMethod", params);
    
    // 查找所有节点
    Set<AstNode> allNodes = resolver.findNode(root, null, null, null, null);
    assertNotNull(allNodes);
    
    // 根据条件查找节点
    Set<AstNode> filteredNodes = resolver.findNode(root, "org.example.test", "TestClass", "testMethod", params);
    assertNotNull(filteredNodes);
    
    // 测试空根节点
    Set<AstNode> emptyNodes = resolver.findNode(null, null, null, null, null);
    assertTrue(emptyNodes.isEmpty());
}
```

### 6.6 reverseCallChainStr 方法测试示例

```java
@Test
public void testReverseCallChainStr() {
    CallChainResolver resolver = new CallChainResolver("src/main/java");
    
    // 创建调用链
    List<String> params = new ArrayList<>();
    AstNode root = resolver.resolveCallChain("org.example.test.TestClass", "testMethod", params);
    
    // 假设找到一个中间节点
    AstNode middleNode = null;
    // 查找中间节点
    // ...
    
    if (middleNode != null) {
        // 生成链路
        List<String> chains = resolver.reverseCallChainStr(middleNode);
        assertNotNull(chains);
    }
    
    // 测试空节点
    List<String> emptyChains = resolver.reverseCallChainStr(null);
    assertTrue(emptyChains.isEmpty());
    
    // 测试没有父节点的节点
    List<String> singleNodeChains = resolver.reverseCallChainStr(root);
    assertNotNull(singleNodeChains);
    assertFalse(singleNodeChains.isEmpty());
}
```

### 6.7 reverseLeavesName 方法测试示例

```java
@Test
public void testReverseLeavesName() {
    CallChainResolver resolver = new CallChainResolver("src/main/java");
    
    // 创建调用链
    List<String> params = new ArrayList<>();
    AstNode root = resolver.resolveCallChain("org.example.test.TestClass", "testMethod", params);
    
    // 假设找到一个中间节点
    AstNode middleNode = null;
    // 查找中间节点
    // ...
    
    if (middleNode != null) {
        // 提取出口节点信息
        List<String> leaves = resolver.reverseLeavesName(middleNode);
        assertNotNull(leaves);
    }
    
    // 测试空节点
    List<String> emptyLeaves = resolver.reverseLeavesName(null);
    assertTrue(emptyLeaves.isEmpty());
    
    // 测试没有父节点的节点
    List<String> singleNodeLeaves = resolver.reverseLeavesName(root);
    assertNotNull(singleNodeLeaves);
    assertFalse(singleNodeLeaves.isEmpty());
}
```

## 7. 测试结果记录

| 测试方法 | 测试用例 | 预期结果 | 实际结果 | 状态 |
|---------|---------|---------|---------|------|
| matchesPattern | 精确匹配 | 正确匹配 | | |
| matchesPattern | 通配符匹配 | 正确匹配 | | |
| matchesPattern | 不匹配的情况 | 不匹配 | | |
| matchesPattern | 空字符串 | 不匹配 | | |
| matchesPattern | null值 | 不匹配 | | |
| resolveCallChain | 正常解析 | 返回正确的调用链 | | |
| resolveCallChain | 循环调用 | 正确标记循环节点 | | |
| resolveCallChain | 无法解析的类 | 返回叶节点 | | |
| resolveCallChain | 无法解析的方法 | 返回叶节点 | | |
| resolveCallChain | 达到最大层数 | 停止递归并返回叶节点 | | |
| mergeCallChain | 合并两个不同的调用链 | 正确合并 | | |
| mergeCallChain | 合并包含相同节点的调用链 | 正确去重 | | |
| mergeCallChain | 合并空列表 | 正常执行 | | |
| mergeCallChain | 合并包含null的列表 | 正常执行 | | |
| findLeaf | 查找所有叶子节点 | 返回正确的叶子节点集合 | | |
| findLeaf | 根据条件查找叶子节点 | 返回符合条件的叶子节点 | | |
| findLeaf | 空根节点 | 返回空集合 | | |
| findLeaf | 没有叶子节点 | 返回空集合 | | |
| findNode | 查找所有节点 | 返回正确的节点集合 | | |
| findNode | 根据条件查找节点 | 返回符合条件的节点 | | |
| findNode | 空根节点 | 返回空集合 | | |
| findNode | 没有符合条件的节点 | 返回空集合 | | |
| reverseCallChainStr | 生成链路 | 返回正确的链路字符串列表 | | |
| reverseCallChainStr | 处理循环调用 | 正确处理循环情况 | | |
| reverseCallChainStr | 空节点 | 返回空列表 | | |
| reverseCallChainStr | 没有父节点的节点 | 返回只包含该节点的列表 | | |
| reverseLeavesName | 提取出口节点 | 返回正确的出口节点信息列表 | | |
| reverseLeavesName | 空节点 | 返回空列表 | | |
| reverseLeavesName | 没有父节点的节点 | 返回只包含该节点的列表 | | |

## 8. 问题记录与修复

| 问题描述 | 复现步骤 | 原因分析 | 修复方案 | 状态 |
|---------|---------|---------|---------|------|
| | | | | |

## 9. 结论

- 重构后的 CallChainResolver 类功能是否与重构前一致
- 是否存在性能问题
- 是否存在功能缺陷
- 测试覆盖是否充分

## 10. 建议

- 对测试中发现的问题进行修复
- 对代码进行进一步优化
- 增加更多的测试用例以提高覆盖率
- 考虑添加性能测试