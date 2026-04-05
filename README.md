# precise-testing

javaparser 的能力，CompilationUnit 是编译单元，根节点。整个类的 AST
```txt
Node (所有 AST 节点的基类)
├── CompilationUnit (编译单元，根节点)
├── TypeDeclaration (类型声明)
│   ├── ClassOrInterfaceDeclaration (类/接口声明)
│   └── EnumDeclaration (枚举声明)
├── BodyDeclaration (成员声明)
│   ├── MethodDeclaration (方法声明)
│   ├── FieldDeclaration (字段声明)
│   └── ConstructorDeclaration (构造器声明)
├── Statement (语句)
│   ├── ExpressionStmt (表达式语句)
│   ├── IfStmt (if 语句)
│   ├── ForStmt (for 循环)
│   └── ...
└── Expression (表达式)
    ├── MethodCallExpr (方法调用)
    ├── BinaryExpr (二元表达式)
    └── ...
```

