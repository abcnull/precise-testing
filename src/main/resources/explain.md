# `parseOutMethodCalls` 方法分析

## 方法功能概述

`parseOutMethodCalls(MethodDeclaration method)` 方法是 `MethodParser` 类中的一个核心方法，其主要功能是：**使用 JavaParser 库分析指定方法体内的所有方法调用和构造方法调用，将这些调用关系解析为** **`MethodCallInfo`** **对象列表并返回**。

## 方法实现详解

### 1. 初始化与边界处理

```java
if (method == null) {
    // 方法声明为空，说明是 jdk/第三方依赖，直接返回结束，表示里层再无调用关系
    return new ArrayList<>();
}
List<MethodCallInfo> calls = new ArrayList<>();
final MethodDeclaration finalMethod = method;
```

- **边界处理**：如果传入的方法声明为 `null`，说明是 JDK 或第三方依赖中的方法，直接返回空列表，表示没有内部调用关系。
- **初始化**：创建一个空的 `MethodCallInfo` 列表，用于存储解析出的方法调用信息。
- **final 变量**：将方法声明赋值给 `final` 变量，以便在 lambda 表达式中使用。

### 2. 遍历方法体内的所有表达式

```java
method.findAll(Expression.class).forEach(expr -> {
    // 处理方法调用表达式和对象创建表达式
});
```

- 使用 JavaParser 的 `findAll` 方法遍历方法体内的所有表达式。
- 对每个表达式进行判断，区分是方法调用表达式还是构造方法调用表达式。

### 3. 处理方法调用表达式

#### 3.1 成功解析的情况

当表达式是 `MethodCallExpr` 且能够成功解析时：

```java
if (expr.isMethodCallExpr()) {
    // 方法调用表达式
    MethodCallExpr callExpr = expr.asMethodCallExpr();
    try {
        // 解析方法调用表达式，获取方法声明
        ResolvedMethodDeclaration resolvedMethod = callExpr.resolve();
        String packageName = resolvedMethod.getPackageName(); // 方法所属包名
        String fullDeclaringClass = resolvedMethod.getClassName();
        if (StringUtils.isNotEmpty(packageName)) {
            fullDeclaringClass = packageName + PathConstant.POINT + fullDeclaringClass; // 方法所属类名，包含包名
        }
        String methodName = resolvedMethod.getName(); // 方法名

        String[] realFullDeclaringClass = { fullDeclaringClass };

        // 尝试解析真实的类名（考虑多态）
        try {
            if (callExpr.getScope().isPresent()) {
                // 解析作用域表达式，获取真实的类名
                // ... 代码省略 ...
            }
        } catch (Exception e) {
        }

        List<String> paramTypes = new ArrayList<>();
        for (int i = 0; i < resolvedMethod.getNumberOfParams(); i++) {
            String paramType = resolvedMethod.getParam(i).getType().describe();
            paramTypes.add(paramType);
        }

        calls.add(
                new MethodCallInfo(fullDeclaringClass, realFullDeclaringClass[0], methodName, paramTypes));
    } catch (Exception e) {
        // 解析失败的情况
        // ... 代码省略 ...
    }
}
```

- `MethodCallExpr`**解析方法调用**：使用 `callExpr.resolve()` 获取解析后的方法声明。
- **获取方法信息**：从解析结果中获取包名、类名和方法名。
- **处理多态**：尝试通过分析作用域表达式，获取真实的类名（考虑多态情况）。
  - 当方法调用有作用域（如 `obj.method()`）时，分析作用域表达式
  - 如果作用域是变量名（如 `list.add()` 中的 `list`），查找该变量的声明
  - 分析变量的初始化表达式，如果是对象创建表达式（如 `new ArrayList<>()`），获取真实的类名
  - 这样可以处理多态情况，例如 `List<String> list = new ArrayList<>()` 中，声明类型是 `List`，但真实类型是 `ArrayList`
- **获取参数类型**：遍历方法参数，获取每个参数的类型。
  - 使用 `resolvedMethod.getParam(i).getType().describe()` 获取参数的完整类型描述
  - 例如，对于 `void method(String name, int age)`，会获取到 `["java.lang.String", "int"]`
- **创建 MethodCallInfo**：将解析结果封装为 `MethodCallInfo` 对象并添加到列表中。
  - 构造 `MethodCallInfo` 时，传入声明类名、真实类名、方法名和参数类型列表

#### 3.2 解析失败的情况

当表达式是 `MethodCallExpr` 但解析失败时：

```java
catch (Exception e) {
    String methodName = callExpr.getNameAsString();
    String[] inferredClass = { inferClassNameFromCall(callExpr, finalMethod) };
    String[] realInferredClass = { inferredClass[0] };

    try {
        if (callExpr.getScope().isPresent()) {
            // 尝试从作用域表达式中推断真实的类名
            // ... 代码省略 ...
        }
    } catch (Exception ex) {
    }

    List<String> paramTypes = new ArrayList<>();
    callExpr.getArguments().forEach(arg -> {
        try {
            paramTypes.add(arg.calculateResolvedType().describe());
        } catch (Exception ex) {
            paramTypes.add(arg.toString());
        }
    });

    calls.add(new MethodCallInfo(inferredClass[0], realInferredClass[0], methodName, paramTypes));
}
```

- **获取方法名**：直接从表达式中获取方法名。
  - 使用 `callExpr.getNameAsString()` 获取方法名，例如 `add`、`println` 等
- **推断类名**：使用 `inferClassNameFromCall` 方法推断方法所属的类名。
  - 当无法解析方法声明时，需要推断方法所属的类
  - 例如，对于 `list.add()`，可能推断出类名为 `List`
- **处理多态**：尝试从作用域表达式中推断真实的类名。
  - 类似于成功解析的情况，分析作用域表达式
  - 查找变量声明和初始化表达式，获取真实的类名
  - 例如，对于 `List<String> list = new ArrayList<>()`，推断出真实类名为 `ArrayList`
- **获取参数类型**：尝试解析每个参数的类型，如果失败则使用参数的字符串表示。
  - 尝试使用 `arg.calculateResolvedType().describe()` 解析参数类型
  - 如果解析失败，使用 `arg.toString()` 作为参数类型的字符串表示
  - 例如，对于 `add("test")`，会获取到 `["java.lang.String"]`
- **创建 MethodCallInfo**：将推断结果封装为 `MethodCallInfo` 对象并添加到列表中。
  - 构造 `MethodCallInfo` 时，传入推断的类名、真实类名、方法名和参数类型列表

### 4. 处理构造方法调用表达式

#### 4.1 成功解析的情况

当表达式是 `ObjectCreationExpr` 且能够成功解析时：

```java
else if (expr.isObjectCreationExpr()) {
    // 构造方法调用表达式
    ObjectCreationExpr creationExpr = expr.asObjectCreationExpr();
    try {
        ResolvedConstructorDeclaration resolvedConstructor = creationExpr.resolve();
        String declaringClass = resolvedConstructor.getClassName();
        String methodName = declaringClass;

        String fullDeclaringClass = resolvedConstructor.getClassName();
        String packageName = resolvedConstructor.getPackageName();
        if (!packageName.isEmpty()) {
            fullDeclaringClass = packageName + PathConstant.POINT + fullDeclaringClass;
        }

        List<String> paramTypes = new ArrayList<>();
        for (int i = 0; i < resolvedConstructor.getNumberOfParams(); i++) {
            String paramType = resolvedConstructor.getParam(i).getType().describe();
            paramTypes.add(paramType);
        }

        calls.add(new MethodCallInfo(fullDeclaringClass, fullDeclaringClass, methodName, paramTypes));
    } catch (Exception e) {
        // 解析失败的情况
        // ... 代码省略 ...
    }
}
```

- **解析构造方法**：使用 `creationExpr.resolve()` 获取解析后的构造方法声明。
- **获取类名和方法名**：从解析结果中获取类名，构造方法的方法名就是类名。
- **获取参数类型**：遍历构造方法参数，获取每个参数的类型。
- **创建 MethodCallInfo**：将解析结果封装为 `MethodCallInfo` 对象并添加到列表中。

#### 4.2 解析失败的情况

当表达式是 `ObjectCreationExpr` 但解析失败时：

```java
catch (Exception e) {
    String className = creationExpr.getType().toString();
    String methodName = StringUtil.getSimpleClassName(className);
    String fullClassName;
    if (className.contains("")) {
        fullClassName = className;
    } else {
        String fullClassNameFromImports = this.findFullClassNameFromImports(className,
                finalMethod);
        if (fullClassNameFromImports != null) {
            fullClassName = fullClassNameFromImports;
        } else {
            String initPackageName = finalMethod.findCompilationUnit()
                    .flatMap(CompilationUnit::getPackageDeclaration)
                    .map(pd -> pd.getNameAsString())
                    .orElse("");
            fullClassName = initPackageName.isEmpty() ? className : initPackageName + "." + className;
        }
    }

    List<String> paramTypes = new ArrayList<>();
    creationExpr.getArguments().forEach(arg -> {
        try {
            paramTypes.add(arg.calculateResolvedType().describe());
        } catch (Exception ex) {
            paramTypes.add(arg.toString());
        }
    });

    calls.add(new MethodCallInfo(fullClassName, fullClassName, methodName, paramTypes));
}
```

- **获取类名和方法名**：直接从表达式中获取类名，方法名就是类名的简单名称。
- **获取完整类名**：如果类名不包含包名，则尝试从导入语句中查找完整类名，或者使用当前类的包名。
- **获取参数类型**：尝试解析每个参数的类型，如果失败则使用参数的字符串表示。
- **创建 MethodCallInfo**：将推断结果封装为 `MethodCallInfo` 对象并添加到列表中。

## 核心技术点分析

### 1. JavaParser 的使用

- **表达式遍历**：使用 `method.findAll(Expression.class)` 遍历方法体内的所有表达式。
- **类型解析**：使用 `resolve()` 方法解析方法和构造方法的声明。
- **类型计算**：使用 `calculateResolvedType()` 计算表达式的类型。

### 2. 多态处理

- **作用域分析**：通过分析方法调用的作用域表达式，尝试获取真实的类名。
- **变量初始化分析**：通过分析变量的初始化表达式，获取变量的真实类型。

### 3. 异常处理

- **解析异常**：当无法解析方法或构造方法时，采用推断的方式获取类名和参数类型。
- **优雅降级**：当无法解析参数类型时，使用参数的字符串表示作为替代。

### 4. 类名推断

- **导入语句分析**：使用 `findFullClassNameFromImports` 方法从导入语句中查找完整类名。
- **包名推断**：当无法从导入语句中找到类名时，使用当前类的包名作为默认包名。

## 方法执行流程

1. **边界检查**：如果方法声明为 `null`，返回空列表。
2. **初始化**：创建空的 `MethodCallInfo` 列表。
3. **遍历表达式**：遍历方法体内的所有表达式。
4. **处理方法调用**：
   - 尝试解析方法调用，获取方法信息和参数类型。
   - 处理多态情况，获取真实的类名。
   - 如果解析失败，采用推断的方式获取类名和参数类型。
   - 创建 `MethodCallInfo` 对象并添加到列表中。
5. **处理构造方法调用**：
   - 尝试解析构造方法调用，获取类名和参数类型。
   - 如果解析失败，采用推断的方式获取类名和参数类型。
   - 创建 `MethodCallInfo` 对象并添加到列表中。
6. **返回结果**：返回包含所有方法调用信息的列表。

## 输入输出示例

### 输入

假设有以下 Java 方法：

```java
public void process() {
    List<String> list = new ArrayList<>();
    list.add("test");
    System.out.println(list.size());
}
```

### 输出

方法将返回以下 `MethodCallInfo` 列表：

1. **构造方法调用**：`new ArrayList<>()`
   - `className`: `java.util.ArrayList`
   - `realClassName`: `java.util.ArrayList`
   - `methodName`: `ArrayList`
   - `paramTypes`: `[]`
2. **方法调用**：`list.add("test")`
   - `className`: `java.util.List`
   - `realClassName`: `java.util.ArrayList`（通过分析变量初始化获取）
   - `methodName`: `add`
   - `paramTypes`: `["java.lang.String"]`
3. **方法调用**：`System.out.println(list.size())`
   - **子调用**：`list.size()`
     - `className`: `java.util.List`
     - `realClassName`: `java.util.ArrayList`（通过分析变量初始化获取）
     - `methodName`: `size`
     - `paramTypes`: `[]`
   - **主调用**：`System.out.println(...)`
     - `className`: `java.io.PrintStream`
     - `realClassName`: `java.io.PrintStream`
     - `methodName`: `println`
     - `paramTypes`: `["int"]`

## 代码优化建议

1. **异常处理改进**：
   - 当前代码中存在大量空的 `catch` 块，建议添加日志记录，便于调试和问题定位。
2. **代码结构优化**：
   - 方法体过长（超过 400 行），建议将处理方法调用和构造方法调用的逻辑拆分为单独的方法。
   - 解析失败时的处理逻辑与成功时的处理逻辑可以进一步分离，提高代码可读性。
3. **性能优化**：
   - 对于频繁使用的操作，如获取编译单元和包声明，可以考虑缓存结果，减少重复计算。
   - 对于大型方法，遍历所有表达式可能会影响性能，可以考虑使用更有针对性的选择器。
4. **代码可读性**：
   - 添加更多的注释，解释关键逻辑和算法。
   - 使用更有描述性的变量名，提高代码的可维护性。

## 详细分析：多态处理逻辑

### 代码片段分析（MethodParser.java#L190-244）

以下是对 `parseOutMethodCalls` 方法中处理多态情况的核心代码的详细分析：

```java
if (callExpr.getScope().isPresent()) {
    Expression scopeExpr = callExpr.getScope().get();
    if (scopeExpr.isNameExpr()) {
        NameExpr nameExpr = scopeExpr.asNameExpr();
        String variableName = nameExpr.getNameAsString();

        Optional<VariableDeclarationExpr> varDeclOpt = finalMethod
                .findAll(VariableDeclarationExpr.class)
                .stream()
                .filter(varDecl -> varDecl.getVariables().stream()
                        .anyMatch(var -> var.getNameAsString().equals(variableName)))
                .findFirst();

        if (varDeclOpt.isPresent()) {
            VariableDeclarationExpr varDecl = varDeclOpt.get();
            varDecl.getVariables().stream()
                    .filter(var -> var.getNameAsString().equals(variableName))
                    .findFirst()
                    .ifPresent(var -> {
                        if (var.getInitializer().isPresent()) {
                            Expression initializer = var.getInitializer().get();
                            if (initializer.isObjectCreationExpr()) {
                                ObjectCreationExpr creationExpr = initializer
                                        .asObjectCreationExpr();
                                String actualType = creationExpr.getType().toString();
                                if (actualType.contains("<")) {
                                    actualType = actualType.substring(0,
                                            actualType.indexOf("<"));
                                }
                                String fullActualType;
                                if (actualType.contains(".")) {
                                    fullActualType = actualType;
                                } else {
                                    String fullClassName = findFullClassNameFromImports(
                                            actualType,
                                            finalMethod);
                                    if (fullClassName != null) {
                                        fullActualType = fullClassName;
                                    } else {
                                        String initPackageName = finalMethod
                                                .findCompilationUnit()
                                                .flatMap(CompilationUnit::getPackageDeclaration)
                                                .map(pd -> pd.getNameAsString())
                                                .orElse("");
                                        fullActualType = initPackageName.isEmpty() ? actualType
                                                : initPackageName + "." + actualType;
                                    }
                                }
                                realFullDeclaringClass[0] = fullActualType;
                            }
                        }
                    });
        }
    }
}
```

### 代码逻辑拆解

#### 1. 检查作用域表达式

```java
if (callExpr.getScope().isPresent()) {
    Expression scopeExpr = callExpr.getScope().get();
    if (scopeExpr.isNameExpr()) {
        NameExpr nameExpr = scopeExpr.asNameExpr();
        String variableName = nameExpr.getNameAsString();
        // ...
    }
}
```

- **作用域检查**：首先检查方法调用是否有作用域表达式（如 `list.add()` 中的 `list`）
- **类型判断**：如果作用域是一个名称表达式（`NameExpr`），则获取变量名
- **变量名提取**：使用 `nameExpr.getNameAsString()` 获取变量名，例如 `list`

#### 2. 查找变量声明

```java
Optional<VariableDeclarationExpr> varDeclOpt = finalMethod
        .findAll(VariableDeclarationExpr.class)
        .stream()
        .filter(varDecl -> varDecl.getVariables().stream()
                .anyMatch(var -> var.getNameAsString().equals(variableName)))
        .findFirst();
```

- **查找变量**：在当前方法中查找所有变量声明表达式（`VariableDeclarationExpr`）
- **过滤筛选**：使用流式操作筛选出变量名匹配的变量声明
- **获取第一个匹配**：使用 `findFirst()` 获取第一个匹配的变量声明

#### 3. 分析变量初始化表达式

```java
if (varDeclOpt.isPresent()) {
    VariableDeclarationExpr varDecl = varDeclOpt.get();
    varDecl.getVariables().stream()
            .filter(var -> var.getNameAsString().equals(variableName))
            .findFirst()
            .ifPresent(var -> {
                if (var.getInitializer().isPresent()) {
                    Expression initializer = var.getInitializer().get();
                    if (initializer.isObjectCreationExpr()) {
                        // ...
                    }
                }
            });
}
```

- **存在检查**：检查是否找到变量声明
- **变量筛选**：在变量声明中找到具体的变量（一个声明可能包含多个变量）
- **初始化检查**：检查变量是否有初始化表达式
- **类型判断**：如果初始化表达式是对象创建表达式（`ObjectCreationExpr`），则进一步处理

#### 4. 获取真实类型

```java
ObjectCreationExpr creationExpr = initializer.asObjectCreationExpr();
String actualType = creationExpr.getType().toString();
if (actualType.contains("<")) {
    actualType = actualType.substring(0, actualType.indexOf("<"));
}
```

- **类型转换**：将初始化表达式转换为对象创建表达式
- **获取类型**：获取创建对象的类型，例如 `ArrayList<String>`
- **处理泛型**：如果类型包含泛型参数（如 `<String>`），则去掉泛型部分，只保留基础类型 `ArrayList`

#### 5. 处理包名

```java
String fullActualType;
if (actualType.contains(".")) {
    fullActualType = actualType;
} else {
    String fullClassName = findFullClassNameFromImports(actualType, finalMethod);
    if (fullClassName != null) {
        fullActualType = fullClassName;
    } else {
        String initPackageName = finalMethod
                .findCompilationUnit()
                .flatMap(CompilationUnit::getPackageDeclaration)
                .map(pd -> pd.getNameAsString())
                .orElse("");
        fullActualType = initPackageName.isEmpty() ? actualType
                : initPackageName + "." + actualType;
    }
}
```

- **包名检查**：检查类型是否已经包含包名（如 `java.util.ArrayList`）
- **导入查找**：如果没有包名，尝试从导入语句中查找完整类名
- **包名推断**：如果导入语句中没有找到，使用当前类的包名作为默认包名

#### 6. 更新真实类名

```java
realFullDeclaringClass[0] = fullActualType;
```

- **更新值**：将获取到的真实类名更新到 `realFullDeclaringClass` 数组中
- **使用数组的原因**：因为在 lambda 表达式中需要修改外部变量，所以使用数组来实现

### 工作原理示例

以 `List<String> list = new ArrayList<>(); list.add("test");` 为例：

1. **方法调用分析**：分析 `list.add("test")` 时，发现作用域是 `list`
2. **变量查找**：在方法中查找 `list` 变量的声明
3. **初始化分析**：发现 `list` 变量的初始化表达式是 `new ArrayList<>()`
4. **类型提取**：从初始化表达式中提取类型 `ArrayList`
5. **包名处理**：通过导入语句或包名推断，获取完整类名 `java.util.ArrayList`
6. **更新类名**：将真实类名 `java.util.ArrayList` 存储起来
7. **创建对象**：最终创建 `MethodCallInfo` 时，使用 `List` 作为声明类名，`ArrayList` 作为真实类名

### 技术要点

1. **JavaParser 表达式处理**：使用 JavaParser 的 API 遍历和分析表达式
2. **流式操作**：使用 Java 8 流式操作进行数据筛选和处理
3. **可选类型处理**：使用 `Optional` 类型安全处理可能不存在的值
4. **多态识别**：通过分析变量初始化表达式，识别多态情况下的真实类型
5. **类型推断**：当类型信息不完整时，通过导入语句和包名推断完整类型

### 代码优化建议

1. **提取方法**：将多态处理逻辑提取为单独的方法，提高代码可读性
2. **缓存结果**：对于频繁使用的操作（如获取编译单元和包声明），可以缓存结果
3. **异常处理**：添加适当的异常处理和日志记录
4. **代码简化**：使用更简洁的方式处理类型提取和包名推断

## 详细分析：参数类型获取逻辑

### 代码片段分析（MethodParser.java#L254-259）

以下是对 `parseOutMethodCalls` 方法中获取参数类型的核心代码的详细分析：

```java
// 获取参数类型：遍历方法参数，获取每个参数的类型
List<String> paramTypes = new ArrayList<>();
for (int i = 0; i < resolvedMethod.getNumberOfParams(); i++) {
    String paramType = resolvedMethod.getParam(i).getType().describe();
    paramTypes.add(paramType);
}
```

### 代码逻辑拆解

#### 1. 初始化参数类型列表

```java
List<String> paramTypes = new ArrayList<>();
```

- **创建列表**：创建一个空的 `ArrayList`，用于存储方法参数的类型
- **类型选择**：使用 `List<String>` 类型，因为我们需要存储每个参数的类型描述字符串

#### 2. 遍历方法参数

```java
for (int i = 0; i < resolvedMethod.getNumberOfParams(); i++) {
    // ...
}
```

- **循环遍历**：使用 for 循环遍历方法的所有参数
- **参数数量**：通过 `resolvedMethod.getNumberOfParams()` 获取方法的参数数量
- **索引访问**：使用索引 `i` 依次访问每个参数

#### 3. 获取参数类型描述

```java
String paramType = resolvedMethod.getParam(i).getType().describe();
```

- **获取参数**：使用 `resolvedMethod.getParam(i)` 获取第 `i` 个参数
- **获取类型**：使用 `.getType()` 获取参数的类型
- **生成描述**：使用 `.describe()` 方法生成类型的字符串描述
- **返回值**：`describe()` 方法返回类型的完整描述，包括包名和泛型信息

#### 4. 添加到列表

```java
paramTypes.add(paramType);
```

- **添加类型**：将获取到的参数类型描述添加到 `paramTypes` 列表中

### 工作原理示例

以 `void method(String name, int age, List<String> items)` 为例：

1. **初始化列表**：创建一个空的 `paramTypes` 列表
2. **遍历参数**：
   - 第一个参数：`String name`
     - 获取参数：`resolvedMethod.getParam(0)`
     - 获取类型：`getType()` 返回 `String` 类型
     - 生成描述：`describe()` 返回 `"java.lang.String"`
     - 添加到列表：`paramTypes` 变为 `["java.lang.String"]`
   - 第二个参数：`int age`
     - 获取参数：`resolvedMethod.getParam(1)`
     - 获取类型：`getType()` 返回 `int` 类型
     - 生成描述：`describe()` 返回 `"int"`
     - 添加到列表：`paramTypes` 变为 `["java.lang.String", "int"]`
   - 第三个参数：`List<String> items`
     - 获取参数：`resolvedMethod.getParam(2)`
     - 获取类型：`getType()` 返回 `List<String>` 类型
     - 生成描述：`describe()` 返回 `"java.util.List<java.lang.String>"`
     - 添加到列表：`paramTypes` 变为 `["java.lang.String", "int", "java.util.List<java.lang.String>"]`

### 技术要点

1. **JavaParser 类型解析**：使用 JavaParser 的 `ResolvedMethodDeclaration` 和相关 API 获取方法参数信息
2. **类型描述生成**：使用 `describe()` 方法生成类型的完整描述，包括包名和泛型信息
3. **列表存储**：使用 `ArrayList` 存储参数类型描述，便于后续处理
4. **索引访问**：使用索引依次访问每个参数，确保不遗漏任何参数

### 代码优化建议

1. **使用流式操作**：可以使用 Java 8 流式操作简化代码
   ```java
   List<String> paramTypes = IntStream.range(0, resolvedMethod.getNumberOfParams())
           .mapToObj(i -> resolvedMethod.getParam(i).getType().describe())
           .collect(Collectors.toList());
   ```

2. **添加参数名称**：如果需要，可以同时获取参数名称，构建更完整的参数信息
   ```java
   List<ParameterInfo> paramInfos = new ArrayList<>();
   for (int i = 0; i < resolvedMethod.getNumberOfParams(); i++) {
       ResolvedParameter param = resolvedMethod.getParam(i);
       String paramName = param.getName();
       String paramType = param.getType().describe();
       paramInfos.add(new ParameterInfo(paramName, paramType));
   }
   ```

3. **处理可变参数**：如果方法包含可变参数（varargs），可以添加特殊处理

## 总结

`parseOutMethodCalls` 方法是一个功能强大的方法，它使用 JavaParser 库解析方法体内的所有方法调用和构造方法调用，并将这些调用关系封装为 `MethodCallInfo` 对象列表。该方法不仅能够处理简单的方法调用，还能处理多态情况下的方法调用，以及解析失败时的优雅降级。

通过分析该方法，我们可以看到它是如何利用 JavaParser 的能力来构建方法调用链的，这对于理解代码的执行流程、分析代码依赖关系以及进行代码重构都非常有帮助。

特别是其处理多态的逻辑，通过分析变量的初始化表达式来获取真实的类名，以及其获取参数类型的逻辑，通过遍历方法参数并生成类型描述，这使得方法调用链的分析更加准确和深入。
