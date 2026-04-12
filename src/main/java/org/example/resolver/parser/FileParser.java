package org.example.resolver.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.example.constant.PathConstant;

/**
 * 文件解析器
 * 专门去解析文件
 */
public class FileParser {

    private JavaParser javaParser; // 用于解析 Java 源码的解析器
    private String sourceRootPath; // 源码根目录

    public FileParser(JavaParser javaParser, String sourceRootPath) {
        this.javaParser = javaParser;
        this.sourceRootPath = sourceRootPath;
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

        String relativePath = className.replace(PathConstant.POINT, PathConstant.SLASH) + ".java";

        Path filePath = Paths.get(sourceRootPath, relativePath);
        File file = filePath.toFile();

        if (!file.exists() && !sourceRootPath.endsWith(PathConstant.JAVA_SOURCE_DIR)) {
            Path altPath = Paths.get(sourceRootPath, PathConstant.JAVA_SOURCE_DIR, relativePath);
            file = altPath.toFile();
        }

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