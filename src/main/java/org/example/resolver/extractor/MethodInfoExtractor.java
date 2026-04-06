package org.example.resolver.extractor;

import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.example.node.field.FuncCate;
import org.example.node.field.FuncInfo;
import org.example.resolver.extractor.ParameterInfoExtractor;
import org.example.resolver.model.ParameterInfo;
import org.example.resolver.model.ReturnTypeInfo;
import org.example.resolver.util.ParserUtil;
import org.example.resolver.util.StringUtil;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class MethodInfoExtractor implements InfoExtractor<FuncInfo, Object[]> {

    private final AnnotationExtractor annotationExtractor = new AnnotationExtractor();

    @Override
    public FuncInfo extract(Object[] source) {
        // CompilationUnit cu = (CompilationUnit) source[0];
        MethodDeclaration method = (MethodDeclaration) source[1];

        FuncInfo info = new FuncInfo();

        // 设置方法分类
        if (isMainMethod(method)) {
            info.setFuncCate(FuncCate.MAIN);
        } else {
            info.setFuncCate(FuncCate.DEFAULT);
        }

        // 方法修饰符 - 从 Modifier 列表转换为 Keyword 列表
        List<Keyword> keywords = method.getModifiers().stream()
                .map(modifier -> modifier.getKeyword())
                .collect(Collectors.toList());
        info.setMethodModifiers(keywords);

        // 方法注解
        info.setAnnotations(annotationExtractor.extract(method));

        // 方法名
        info.setFuncName(method.getNameAsString());

        // 参数类型（简单类型名）和参数包名
        ParameterInfo paramInfo = ParameterInfoExtractor.extractParameterInfo(ParserUtil.extractFullParamTypes(method));
        info.setFuncParams(paramInfo.getSimpleTypes());
        info.setFuncParamsPackageName(paramInfo.getPackageNames());

        // 返回值类型和返回值包名
        ReturnTypeInfo returnTypeInfo = extractReturnTypeInfo(method);
        info.setFuncReturnType(returnTypeInfo.getSimpleType());
        info.setFuncReturnPackageName(returnTypeInfo.getPackageName());

        // 方法注释
        info.setFuncComment(extractMethodComment(method));

        return info;
    }

    /**
     * 判断是否是 main 方法
     */
    private boolean isMainMethod(MethodDeclaration method) {
        return method.getNameAsString().equals("main") &&
                method.getParameters().size() == 1 &&
                method.getParameters().get(0).getType().toString().equals("String[]") &&
                method.getType().toString().equals("void") &&
                method.getModifiers().stream().anyMatch(m -> m.getKeyword() == Keyword.PUBLIC) &&
                method.getModifiers().stream().anyMatch(m -> m.getKeyword() == Keyword.STATIC);
    }

    /**
     * 提取方法注释
     */
    private String extractMethodComment(MethodDeclaration method) {
        Optional<String> commentOpt = method.getJavadocComment().map(javadoc -> javadoc.getContent());
        if (commentOpt.isPresent()) {
            return commentOpt.get();
        }

        return method.getComment().map(comment -> comment.getContent()).orElse("");
    }

    /**
     * 提取返回值信息（简单类型名和包名）
     */
    private ReturnTypeInfo extractReturnTypeInfo(MethodDeclaration method) {
        try {
            String fullReturnType = method.getType().resolve().describe();
            String simpleReturnType = StringUtil.getSimpleClassName(fullReturnType);
            String returnPackageName = StringUtil.getPackageName(fullReturnType);
            return new ReturnTypeInfo(simpleReturnType, returnPackageName);
        } catch (Exception e) {
            String returnType = method.getType().asString();
            String simpleReturnType = StringUtil.getSimpleClassName(returnType);
            String returnPackageName = StringUtil.getPackageName(returnType);
            return new ReturnTypeInfo(simpleReturnType, returnPackageName);
        }
    }
}