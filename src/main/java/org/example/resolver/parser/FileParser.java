package org.example.resolver.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.example.constant.PathConstant;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;

/**
 * 文件解析器
 * 专门去解析文件
 */
public class FileParser {
    private static volatile FileParser fileParser;
    private String sourceRootPath; // 源码根目录 ～/project/src/main/java
    private JavaParser javaParser; // 用于解析 Java 源码的解析器

    // 私有构造器
    private FileParser(String sourceRootPath, JavaParser javaParser) {
        this.sourceRootPath = sourceRootPath;
        this.javaParser = javaParser;
    }

    // 设置配置
    public static void init(String sourceRootPath, JavaParser javaParser) {
        fileParser = new FileParser(sourceRootPath, javaParser);
    }

    // 确定 sourceRootPath 和 javaParser 赋值后使用
    // 获取实例之前必先 init
    public static FileParser getInstance() {
        if (fileParser == null) {
            throw new IllegalStateException("FileParser 未初始化，请先调用 init() 方法");
        }
        return fileParser;
    }

    /**
     * 从指定的源码根目录解析类文件，返回对应的 CompilationUnit。
     * 支持在源码根目录直接查找或在src/main/java子目录查找。
     *
     * @param className 全限定类名
     * @return 解析后的 CompilationUnit，若文件不存在或解析失败则返回null
     */
    public CompilationUnit parseOutCompilationUnit(String className) {
        if (javaParser == null || sourceRootPath == null) {
            return null;
        }

        String relativePath = className.replace(PathConstant.DOT, PathConstant.SLASH) + PathConstant.DOT_JAVA_SUFFIX;

        Path filePath = Paths.get(sourceRootPath, relativePath);
        File file = filePath.toFile();
        if (!file.exists()) {
            return null;
        }

        try {
            return javaParser.parse(file).getResult().orElse(null);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

}