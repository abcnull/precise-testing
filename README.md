<div align=center>
  <a href="https://www.java.com/" target="_blank" rel="noopener noreferrer">
    <img src="https://img.shields.io/badge/Java-brown"/>
  </a>
  <a href="https://javaparser.org/" target="_blank" rel="noopener noreferrer">
    <img src="https://img.shields.io/badge/Javaparser-yellow"/>
  </a>
  <img src="https://img.shields.io/badge/Code Analysis-black"/>
  <img src="https://img.shields.io/badge/Testing Tools-green"/>
</div>

# precise-testing

此项目使用 java 静态代码分析（底层使用 javaparser 能力），分析 `.java` 文件，从指定方法入口开始，找到项目中方法的层层调用链路

项目想要快速尝试执行下 demo，可以看 `功能概述` 和 `Demo 先跑起来` 即可

建议：对于 java 项目，如果只能拿到 `.java` 文件无法拿到 `.class` 文件，解析方法调用链可以使用 javaparser 来解析。而若能拿到 `.class` 字节码文件，那么可以使用 ASM 来解析方法调用链。

## 功能概述

项目是在做啥：使用 javaparser 的能力，从指定方法入口开始，找到项目中该方法一直往下调用的所有方法调用链

【javaparser 和 ASM】
一般企业在做精准测试时，往往会使用 javaparser 结合 ASM 做方法调用链分析。由于 javaparser 做静态代码分析解析的是 `.java` 文件来构造 AST，而 ASM 则是解析 `.class` 文件来进行方法调用链分析，其实相比 javaparser 而言，ASM 要解析的更完整一些，且性能更好，但是 ASM 更偏低层能力，用户用起来更麻烦

【不好解析的地方】
相比 ASM，其实 javaparser 很多场景不太好解析：

- lambda 表达式的场景：javaparser 解析的很有限
- 多态场景：由于 javaparser 做静态代码分析，而多态在运行时确定，所以 javaparser 无法真正的解析多态场景，可能只能通过 coding 来定一些规则来猜测
- 第三方依赖：项目中往往有很多第三方依赖，如果引入真实的这些依赖的 `.java` 文件的路径，那么这些依赖对应的类也无法解析的完整
- ...

【性能上】
ASM 其实性能要更好，不管是解析速度还是内存占用上

【易用性上】
相比 ASM，javaparser 无疑占优

## Demo 快速先跑起来

`src/main/java/org/example/demo` 下的所有内容都是为了展示，即 `src/main/java/org/example/demo` 整个内容删除也不影响整个项目

你可以执行 `src/main/java/org/example/demo/Main.java` 即可看到打印：

```
Level1#level1_func8(String, int)
    ├── Level2#Level2()
    └── Level2#level2_func8()
        ├── Level3#Level3()
        ├── PrintStream#println(String)
        └── Level3#level3_func8(boolean)
            └── StringUtils#isBlank(null)
```

核心即创建解析器，指定项目路径，符号解析路径，自定义查找规则，以及是否允许多个 dag 连通：

```java
CallChainResolver resolver = new CallChainResolver(sourceRoot, symbolSolverPaths, preciseRule, isConnected);
```

分析某个方法往下层的调用链，你需要传入类的包路径，方法名，以及方法参数，这样才能指定到具体的方法：

```java
DagNode rootNode = resolver.resolveCallChain(startClass, startMethod, methodParams);
```

当然最简单的方法，你可以直接：

```java
// 默认项目路径，符号解析路径都是 sourceRoot，默认用 PreciseRule 是最常规模式，默认不允许多个 Dag 连通
CallChainResolver resolver = new CallChainResolver(sourceRoot);
// 查找指定方法调用链
DagNode rootNode = resolver.resolveCallChain(startClass, startMethod, methodParams);
```

## 方法调用链的结构（Dag 图）

方法调用关系简单想可能是一个树形结构，比如一颗二叉树，方法 A1 -> B1, B2 然后 B1 -> C1, C2
<center>
<img src="https://github.com/abcnull/Image-Resources/blob/master/precise-testing/binary_tree.jpeg" alt="binary_tree.jpeg" width="50%" />
</center>

但其实方法调用与其说像二叉树，其实更像一颗多叉树，因为方法内存在众多方法的调用关系
<center>
<img src="https://github.com/abcnull/Image-Resources/blob/master/precise-testing/mutiple_fork_tree.jpg" alt="mutiple_fork_tree.jpg" width="50%" />
</center>

更进一步，方法调用仅仅是多叉树吗？不一定，因为多叉树要求任何节点有且仅有一个父亲节点，但其实方法调用可能出现如下(左)结构，除非同时被多个方法调用的 C1 方法你需要弄出新的对象，如下(右)结构，但这无疑增加了存储成本

<center>
<img src="https://github.com/abcnull/Image-Resources/blob/master/precise-testing/base_dag.jpg" alt="base_dag.jpg" width="50%" />
</center>

由于 dag 要求是没有环，但是方法调用可能存在环，比如递归，因此 dag 结构似乎也不满足，我们可以把 dag 做下优化，依然将方法调用链构造成一个 dag，做法是在遍历调用链时若发现存在循环调用的方法后，给其打上一个"已出现循环"的标记，而其可作为 dag 中的叶子节点，结构如下

<center>
<img src="https://github.com/abcnull/Image-Resources/blob/master/precise-testing/cycle_dag.jpg" alt="cycle_dag.jpg" width="10%" />
</center>

我们通过如下代码来创建一个方法调用解析器，并查找 2 个方法的调用链：

```java
CallChainResolver resolver = new CallChainResolver(sourceRoot, symbolSolverPaths, preciseRule, isConnected);
DagNode A1 = resolver.resolveCallChain(startClass, startMethod, methodParams);
DagNode A2 = resolver.resolveCallChain(startClass2, startMethod2, methodParams2);
```

其中 isConnected 表示是否连通，如果为 true 表示当使用 resolver 多次寻找不同方法调用链时，最终会自动把多个独立连通的 dag 组合成一个大的连通 dag（前提是多个独立 dag 中有共同的方法）。如果为 false 则表示每个方法的调用链都是独立的，不会被组合成一个大的连通 dag，如下：

<center>
<img src="https://github.com/abcnull/Image-Resources/blob/master/precise-testing/connected_dag.jpg" alt="connected_dag.jpg" />
</center>

<center>
<img src="https://github.com/abcnull/Image-Resources/blob/master/precise-testing/unconnected_dag.jpg" alt="unconnected_dag.jpg" />
</center>

若 isConnected = true，我们希望其最后连通，很多时候当我们遍历这个 dag 找到某个中间节点时，我们希望能从中间节点快速往上查找，来找到父节点的内容，因此这个 dag 可能需要拥有指向父节点的指针：

<center>
<img src="https://github.com/abcnull/Image-Resources/blob/master/precise-testing/direction_dag.jpg" alt="direction_dag.jpg" width="30%" />
</center>

但它并不是环，因为当为了查找方法调用链时，只能往下一个方向去查找，且碰到循环重复出现的方法时会作为叶子节点特殊处理

最终我们构造的方法调用 dag 有类似如下的结构，即对应 `src/main/java/org/example/node/DagNode.java` 结构

<center>
<img src="https://github.com/abcnull/Image-Resources/blob/master/precise-testing/final_dag.jpg" alt="final_dag.jpg" />
</center>

## DagNode 节点解释

`src/main/java/org/example/node/DagNode.java` 以方法为节点核心，每个节点表示一个方法

包含 6 大部分信息

### 包信息

- 方法所属声明类的包名
- 方法所属实现类的包名

注意这里的声明类和实现类，是在多态场景下，比如：

```java
User user = new Student();
user.getName();
```

`getName()` 方法在 User 类和 Student 中都含有，但实际执行时执行的是 Student 逻辑，那么 `getName()` 方法所属的声明类是 `User` 类，所属的实现类是 `Student` 类

### 类信息

- ClassOrigin：类来源，项目/jdk/依赖
- 声明类的简单类名：不带包名，不包含泛型，若是内部类则为 `Aaa$Bbb` 的形式
- 实现类的简单类名：不带包名，不包含泛型，若是内部类则为 `Aaa$Bbb` 的形式
- `Map<String, Map<String, Object>>`：类的注解，数据如:
  ```json
  {
    "注解名1": {
      "参数名1": "String",
      "参数名2": "int"
    },
    "注解名2": {}
  }
  ```
- `List<Keyword>`：类的修饰符，如 public, final, abstract 等，修饰符枚举使用 javaparser 中的 `com.github.javaparser.ast.Modifier.Keyword` 类
- ClassDeclaration：类声明，如 class/interface/enum/annotation/record
- MethodBelongs2Class：推测的所找到的方法属于的实现类或者声明类，还是属于某个祖先类，亦或者无法判定
- 类的其他属性等

### 方法信息

- 同类注解一样，也有方法注解
- FuncCate：方法的分类，如普通方法/构造方法/main方法
- `List<Keyword>` 和类修饰符类似
- 方法名
- 方法参数类型以及方法参数所属包名：方法参数类型仅仅是简单类型，不包含泛型
- 方法参数返回值类型及返回值所属包名：方法参数返回值类型仅仅是简单类型，不包含泛型
- 方法等其他属性等

### 是否循环调用

一般的节点循环调用都会标记为 false，当出现了循环调用，此字段为 true

### 孩子节点集合

`List<DagNode> children`

### 父亲节点集合

`List<DagNode> parents`

## 详细功能

### Dag 的连通构造和独立构造

`src/main/java/org/example/resolver/CallChainResolver.java` 内：

```java
CallChainResolver resolver = new CallChainResolver(sourceRoot, symbolSolverPaths, preciseRule, isConnected);
DagNode A1 = resolver.resolveCallChain(startClass, startMethod, methodParams);
DagNode A2 = resolver.resolveCallChain(startClass2, startMethod2, methodParams2);
```

当 isConnected == true 表示 A1，A2 如果有相同的节点，则二者能连通；当 isConnected == false 表示 A1，A2 是独立的，不会被组合成一个大的连通 dag。

如果你使用默认的构造器，则默认不连通：

```java
CallChainResolver resolver = new CallChainResolver(sourceRoot);
```

### IPreciseRule：控制调用链查找规则

#### 使用方式

在创建解析器 `CallChainResolver resolver` 时，你可以传入一个 `IPreciseRule` 实现类，来控制调用链查找规则。

```java
IPreciseRule normalRule = new NormalRule();
CallChainResolver resolver = new CallChainResolver(sourceRoot, symbolSolverPaths, normalRule, isConnected);
```

其中 preciseRule 你需要自己创建，有如下 4 种方式

```java
// 走默认规则，最简单直接的方式
IPreciseRule normalRule = new NormalRule();

// 走 warnMod 规则
IPreciseRule warnModRule = new WarnModRule();

// 走 dangerMod 规则
IPreciseRule dangerModRule = new DangerModRule();

// 走全部自定义规则，MyCustomRule 是你自己编写的，需要实现 CustomRule 类
IPreciseRule myCustomRule = new MyCustomRule();
```

#### IPreciseRule 的 4 种过滤规则

项目中已有的四种规则分别表示的限制：

```
interface IPreciseRule 规则接口
    ├── class NormalRule：限制最强，只允许项目中的类中的方法被构造到 Dag，最大遍历深度 20 层
    ├── class WarnModRule：限制减弱，允许项目中和第三方依赖类中的方法被构造到 Dag，最大遍历深度 20 层。内存可能出现风险
    ├── class DangerModRule：限制更弱，允许所有方法被构造到 Dag 中，包括项目，第三方依赖，jdk 中的类中的方法，无最大遍历深度。内存更可能出现风险
    └── abstract class CustomRule：自定义规则，其中明确的更细致的规则标准，用户如果像用自定义规则，需要实现此类
```

#### 自定义规则来实现 CustomRule

如果需要自定义构造过滤节点的规则，你需要实现 `CustomRule` 类，并且 Override 其中的 `setMaxLayer()/setPreciseModel()/setFilterClasses()/setThrownClasses()` 方法，分别表示：

- `setMaxLayer()`：自行设置方法调用最大层数限制，必须赋值给 `super.maxLayer`
- `setPreciseModel()`：自行设置项目中的，第三方依赖，jdk 中的类中的方法是否被构造到 Dag 中，必须赋值给 `super.preciseModel`
- `setFilterClasses()`：自行设置过滤的类白名单，即如果白名单存在某些类的全限定名，则这些类才允许被构造进 Dag，特殊场景，如果白名单为空则表示所有类都允许被构造进 Dag，最后必须赋值给 `super.filterClasses`
- `setThrownClasses()`：自行设置过滤的类黑名单，即如果黑名单存在某些类的全限定名，则这些类不会允许被构造进 Dag，黑名单比白名单优先级更高，最后必须赋值给 `super.thrownClasses`

如下是自定义过滤规则，实现了 CustomRule 抽象类，其中自定义了各种细致规则

```java
public class MyCustomRule extends CustomRule {
    // 自定义规则：调用最大层数限制
    @Override
    public void setMaxLayer() {
        super.maxLayer = 20;
    }

    // 自定义规则：项目中的，第三方依赖，jdk
    @Override
    public void setPreciseModel() {
        super.preciseModel = PreciseModel.DANGER_MOD;
    }

    // 自定义规则：设置过滤的类白名单
    @Override
    public void setFilterClasses() {
        super.filterClasses = new ArrayList<>();
    }

    // 自定义规则：设置过滤的类黑名单
    @Override
    public void setThrownClasses() {
        super.thrownClasses = new ArrayList<>();
    }
}
```

#### 自定义规则细节

IPreciseRule 中的过滤规则中

`src/main/java/org/example/rule/PreciseModel.java` 有 3 种枚举：

- `PreciseModel.NORMAL_MOD`：只允许项目中的类中的方法被构造到 Dag
- `PreciseModel.WARN_MOD`：允许项目中和第三方依赖类中的方法被构造到 Dag
- `PreciseModel.DANGER_MOD`：允许所有方法被构造到 Dag 中，包括项目，第三方依赖，jdk 中的类中的方法

而过滤黑名单 `thrownClasses` 和白名单 `filterClasses` 的写法则比较丰富，支持尾部通配符的写法：

- `com.abc.*`：表示 com.abc 包下的所有类
- `com.abc.User`：表示 User 类
- `com.abc.Student*`：表示 com.abc.Student\* 这样的类

### AnnotationEntrance：找到指定方法

实际项目中，往往需要通过注解来定位到要进行方法调用链分析的入口方法处，可以使用 `src/main/java/org/example/resolver/entrance/AnnotationEntrance.java` 中的能力，如下所示：

```java
String classAnn = "@RestController"; // 写成 "RestController" 亦可
String methodAnn = "@RequestMapping"; // 写成 "RequestMapping" 亦可
String path = "/user/xxx/yyy/project/src/main/java/com/exapmle/name/controller" // 在指定路径下递归遍历其下所有包

AnnotationEntrance ann = new AnnotationEntrance();
List<MethodCallInfo> methodCallInfos = ann.findEntranceMethod(path, classAnn, methodAnn);
```

目前 `classAnn` 和 `methodAnn` 必须要存在这样的注解，不能为 null 或 ""

返回的 methodCallInfos 含有找到的所有入口方法（其中 declClassName 和 realClassName 相同）

### TraverseDag：遍历 Dag 图

`src/main/java/org/example/resolver/find/TraverseDag.java` 中专门用来遍历 Dag

#### 从指定节点向下查找特定目标节点

```java
// 指定节点
DagNode root = ...; // 伪代码
// 一批目标节点
List<MethodCallInfo> methodCallInfos = ...; // 伪代码

TraverseDag traverseDag = new TraverseDag();
Set<DagNode> nodes = traverseDag.findDownSpecificNodes(root, methodCallInfos);
```

查找方式是通过类名+方法名+参数完全匹配去寻找

#### 从指定节点向上找到 Dag 的所有根节点

```java
// 指定节点
DagNode specificNode = ...; // 伪代码

TraverseDag traverseDag = new TraverseDag();
Set<DagNode> roots = traverseDag.findUpRootsFromSpecificNode(specificNode);
```

#### 从指定节点向下查找特定目标节点，然后与 Dag 根节点建立 Map 关联

```java
// 指定节点
DagNode root = ...; // 伪代码
// 一批目标节点
List<MethodCallInfo> methodCallInfos = ...; // 伪代码

TraverseDag traverseDag = new TraverseDag();
Map<DagNode, Set<DagNode>> map = traverseDag.findDownSpecific2RootMap(root, methodCallInfos);
```

返回的 map 中：

- map.key = 目标被查找的节点
- map.value = 目标被查找的节点的所有在 Dag 的最顶层的根父节点集合

#### 从指定节点出发找到 Dag 所有根节点

```java
// 指定节点
DagNode specificNode = ...; // 伪代码

TraverseDag traverseDag = new TraverseDag();
Set<DagNode> roots = traverseDag.findAllRoots(specificNode);
```

本质上通过 specificNode 会向上和向下遍历，最终能遍历完所有的节点，来找到 Dag 中所有的根节点

#### 自行实现

当然 `src/main/java/org/example/resolver/find/TraverseDag.java` 中也提供了 `preOrderRecursive()/postOrderRecursive()` 先序/后序遍历的基础写法，完全也可以按照自己的方式来遍历 DagNode

### PrintDag：打印 Dag 图

`src/main/java/org/example/resolver/print/PrintDag.java` 中专门用来打印 Dag 图

```java
// 指定节点
DagNode dagNode = ...; // 伪代码

// 从上到下打印整个 Dag 方法调用
PrintDag printDag = new PrintDag();
printDag.printSimpleCallChains(dagNode);
```

打印出的每处方法的结构如 `User#func2(String, Object, int)`，不带有包名

## 各种调用场景打印演示

【jdk 中的方法场景】
入口方法 `org.example.demo.callchain.Level1#level1_func1`，打印结果：

```
Level1#level1_func1()
    └── PrintStream#println(String)
```

【第三方依赖方法场景】
入口方法 `org.example.demo.callchain.Level1#level1_func2`，打印结果：

```
Level1#level1_func2()
    └── StringUtils#isBlank(String)
```

【项目中的方法场景】
入口方法 `org.example.demo.callchain.Level1#level1_func3`，打印结果：

```
Level1#level1_func3()
    └── Level2#Level2()
```

【循环调用场景】
入口方法 `org.example.demo.callchain.Level1#level1_func4`，打印结果：

```
Level1#level1_func4()
    ├── Level2#Level2()
    └── Level2#level2_func4()
        ├── Level1#Level1()
        └── Level1#level1_func4() [循环调用]
```

【检测泛型是否会含有】
入口方法 `org.example.demo.callchain.Level1#level1_func5`，打印结果：

```
Level1#level1_func5()
    ├── ArrayList#ArrayList()
    └── List#add(Object)
```

【多态场景】
入口方法 `org.example.demo.callchain.Level1#level1_func6`，打印结果：

```
Level1#level1_func6()
    ├── Level2#Level2()
    └── ILevel2#level2_func6()
        └── PrintStream#println(String)
```

打印出的`类名#方法名`中类名其实对应是方法所属的声明类名，那如果是 `Level2()` 这样的构造方法呢，该命名的构造方法因为只有实现类有，因此即使这样声明 `ILevel2 level2 = new Level2();`，构造方法对应的声明类和实现类都是 `Level2`，于是上方展现 `Level2#Level2()`

【类/方法中各种信息内容展示场景】
可以断点查看 DagNode 中各种信息是否包含
入口方法 `org.example.demo.callchain.Level1#level1_func7`，打印结果：

```
Level1#level1_func7()
    ├── Level2#Level2()
    └── Level2#level2_func7(String, int)
```

【混合场景】
入口方法 `org.example.demo.callchain.Level1#level1_func8`，打印结果：

```
Level1#level1_func8(String, int)
    ├── Level2#Level2()
    └── Level2#level2_func8()
        ├── Level3#Level3()
        ├── PrintStream#println(String)
        └── Level3#level3_func8(boolean)
            └── StringUtils#isBlank(null)
```

## 额外注意

### 路径规范

创建类解析器时，指定的路径要求是绝对路径，而是末尾一般是 `src/main/java` 结尾的路径，因找类时候会从 sourceRoot 后拼接类的全限定名来查找类

```java
String sourceRoot = "/aaa/bbb/ccc/src/main/java";
CallChainResolver resolver = new CallChainResolver(sourceRoot, symbolSolverPaths, preciseRule, isConnected);
```

这也意味着，如果你想要分析 test 测试包下某些方法的调用链目前是不支持的，比如填写 sourceRoot 是 `/aaa/bbb/ccc/test/main/java`

另外符号解析 symbolSolverPaths 路径一般也是到 `src/main/java` 结尾

### 循环调用

单个 Dag 中会存在方法复用的情况，如果该方法循环出现了，那么该叶子节点为整个循环调用的方法，其被标记成“出现循环”的标志，如果不同方法往下调用，都有该方法发生循环调用，则被标记的“出现循环”标志的循环调用方法也无法复用。具体看下面结构

```
A1
├── B1
|   └── C1
|       └── C1 [出现循环调用]
└── B2
    └── C1
        └── C1.bak [出现循环调用]
```

这里的 2 个 C1 是复用的，这里的 `C1 [出现循环调用]` 和 C1 是不同节点，这里的 `C1 [出现循环调用]` 不是复用的，即 `C1 [出现循环调用]` 和 `C1.bak [出现循环调用]` 其实是不同节点

### 关于泛型

DagNode 结构中所有的类都是不带有泛型信息的

### 内部类

DagNode 中所有的简单类名，如果是内部类的情况，则为 `Aaa$Bbb` 类似的形式表示，以 `$` 拼接

### 内存溢出

创建解析器时，尽量选择 NormalRule 或者 WarnModRule，因为他们限制了解析的范围和深度

```java
IPreciseRule normalRule = new NormalRule();
CallChainResolver resolver = new CallChainResolver(sourceRoot, symbolSolverPaths, normalRule, isConnected);
```

```java
IPreciseRule warnModRule = new WarnModRule();
CallChainResolver resolver = new CallChainResolver(sourceRoot, symbolSolverPaths, warnModRule, isConnected);
```

或者使用自定义的解析规则，注意设置最大深度，以及过滤范围，和具体 PreciseModel 模式，上方 `[自定义规则细节]` 已提到

如果解析复杂超大项目时，如果查找过多的方法时

```java
CallChainResolver resolver = new CallChainResolver(sourceRoot, symbolSolverPaths, preciseRule, isConnected);
DagNode rootNode1 = resolver.resolveCallChain(startClass1, startMethod1, methodParams1);
DagNode rootNode2 = resolver.resolveCallChain(startClass2, startMethod2, methodParams2);
DagNode rootNode3 = resolver.resolveCallChain(startClass3, startMethod3, methodParams3);
...
DagNode rootNodeN = resolver.resolveCallChain(startClassN, startMethodN, methodParamsN);
```

- 如果这样的 rootNode1, rootNode2, rootNode3, ..., rootNodeN 是在同一作用域中，未来他们可能会同时释放，那么建议 isConnected 可设置为 true，增进节点的复用，减少内存占用
- 如果这样的 rootNode1, rootNode2, rootNode3, ..., rootNodeN 是在比如 for 循环中，每次循环拿到一个 rootNode，但每次循环后这个 rootNode 不会再被使用，它会失去引用，未来被 jvm 回收，那么建议 isConnected 可设置为 false，让 Dag 独立，使 Dag 和 Dag 之间节点不能复用，来调控好内存，若 isConnected 设置为 true，可能会导致 for 循环中历史迭代中的 rootNode 没有失去引用，jvm 无法回收，从而容易造成内容溢出

### 解析能力

专门分析 `.java` 源代码文件的 javaparser 可以解析 AST 出来，但是由于纯 java 代码是静态代码，没有动态信息，纯文本内容很难猜测分析出多态场景，以及目前 lambda 表达式的识别能力有限，并且对于第三方依赖，是不能很好的分析的，除非第三方的依赖的 `.java` 文件都被下载下来，添加进入符号解析路径中去（但不可能把所有的第三方依赖的源码文件都下载下来）

相比，ASM 专门用来分析 `.class` 字节码文件，可以分析的更深，但是操作起来难度也更大

## 总结

查找要做方法调用分析的入口方法：

```java
String classAnn = "@RestController"; // 写成 "RestController" 亦可
String methodAnn = "@RequestMapping"; // 写成 "RequestMapping" 亦可
String path = "/user/xxx/yyy/project/src/main/java/com/exapmle/name/controller" // 在指定路径下递归遍历其下所有包

AnnotationEntrance ann = new AnnotationEntrance();
List<MethodCallInfo> methodCallInfos = ann.findEntranceMethod(path, classAnn, methodAnn);
```

找到后，解析 methodCallInfos 中每个方法，构造出 `startClass/startMethod/methodParams`

然后用自定义 MyCustomRule 类，其实现了 CustomRule 抽象类

```java
IPreciseRule preciseRule = new MyCustomRule();
```

构造解析器，并开始解析入口方法，来构造 Dag

```java
CallChainResolver resolver = new CallChainResolver(sourceRoot, symbolSolverPaths, preciseRule, isConnected);
DagNode rootNode = resolver.resolveCallChain(startClass, startMethod, methodParams);
```

打印 Dag

```java
PrintDag printDag = new PrintDag();
printDag.printSimpleCallChains(rootNode);
```

你也可以使用 `src/main/java/org/example/resolver/find/TraverseDag.java` 中的各种 public 方法来遍历 Dag，或者自行编写 Dag 遍历方法
