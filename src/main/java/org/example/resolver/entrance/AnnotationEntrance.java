package org.example.resolver.entrance;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.example.constant.PathConstant;
import org.example.resolver.model.MethodCallInfo;

/**
 * 通过类注解寻找要分析的入口方法调用链
 */
public class AnnotationEntrance {

    /**
     * 通过类注解和方法注解寻找要分析的入口方法调用链
     * 
     * @param underPath 某个路径，比如 /user/xxx/project/某路径
     * @param classAnn  类注解，比如 @AbcClass 或者写 AbcClass
     * @param methodAnn 方法注解，比如 @AbcMethod 或者写 AbcMethod
     * @return 入口方法调用信息
     */
    public List<MethodCallInfo> findEntranceMethod(String underPath, String classAnn, String methodAnn) {
        if (StringUtils.isBlank(underPath) || StringUtils.isBlank(classAnn) || StringUtils.isBlank(methodAnn)) {
            return null;
        }
        classAnn = classAnn.trim().startsWith(PathConstant.AT) ? classAnn.trim() : PathConstant.AT + classAnn.trim();
        methodAnn = methodAnn.trim().startsWith(PathConstant.AT) ? methodAnn.trim()
                : PathConstant.AT + methodAnn.trim();

        /*
         * 先找到这样的 java 文件
         * 再从 java 文件中，通过正则去匹配的找这样的文件
         * 从这样的文件中，利用正则找到这个方法的方法名，方法参数，以及这个类的全限定名
         */

        // 1. 拼写匹配类注解和方法注解的正则表达式
        // 匹配形如 @ClassAnn 或 @ClassAnn(...) 的类注解，以及 @MethodAnn 或 @MethodAnn(...) 的方法注解
        String classAnnPattern = Pattern.quote(classAnn) + "(?:\\s*\\([^)]*\\))?";
        String methodAnnPattern = Pattern.quote(methodAnn) + "(?:\\s*\\([^)]*\\))?";
        String combinedRegex = "(?s)" + classAnnPattern + ".*?" + methodAnnPattern;

        // 2. 遍历 underPath 路径下的所有 .java 文件
        File rootDir = new File(underPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            return null;
        }

        // 3. 提取信息的正则表达式
        // 匹配 package 声明，例如 "package org.example.resolver.entrance;"
        Pattern packagePattern = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
        // 匹配类/枚举/接口声明（获取类名），例如 "public class AnnotationEntrance"
        Pattern classPattern = Pattern.compile(
                "(?:(?:public|private|protected|abstract|final|static|sealed|non-sealed)\\s+)*" +
                        "(?:class|interface|enum|@interface|record)\\s+(\\w+)",
                Pattern.MULTILINE);
        // 匹配带有方法注解的方法声明（获取方法名和参数）
        Pattern methodPattern = Pattern.compile(
                methodAnnPattern
                        + "\\s*(?:(?:public|private|protected|abstract|final|static|native|synchronized|strictfp)\\s+)*"
                        +
                        "(?:[\\w<>\\[\\],\\s]+?)\\s+(\\w+)\\s*\\(([^)]*)\\)",
                Pattern.MULTILINE | Pattern.DOTALL);

        try {
            List<File> javaFiles = collectJavaFiles(rootDir);

            List<MethodCallInfo> result = new ArrayList<>();
            for (File javaFile : javaFiles) {
                String content = new String(Files.readAllBytes(javaFile.toPath()));
                // 移除注释，避免匹配被注释掉的代码
                String cleanContent = removeComments(content);

                // 利用组合正则判断文件是否同时包含类注解和方法注解
                if (!Pattern.compile(combinedRegex).matcher(cleanContent).find()) {
                    continue;
                }

                // 提取 package
                String packageName = extractGroup(cleanContent, packagePattern, 1);
                if (StringUtils.isBlank(packageName)) {
                    continue;
                }

                // 提取类名
                String className = extractGroup(cleanContent, classPattern, 1);
                if (StringUtils.isBlank(className)) {
                    continue;
                }
                className = className.contains(PathConstant.LEFT_ANGLE_BRACKET)
                        ? className.substring(0, className.indexOf(PathConstant.LEFT_ANGLE_BRACKET))
                        : className;

                // 提取方法名和参数
                Matcher methodMatcher = methodPattern.matcher(cleanContent);
                while (methodMatcher.find()) {
                    String methodName = methodMatcher.group(1).trim();
                    String paramsStr = methodMatcher.group(2).trim();

                    // 解析参数列表，按逗号切割
                    List<String> paramTypes = parseParamTypes(paramsStr);
                    if (paramTypes == null) {
                        return null;
                    }

                    // 构建全限定类名
                    String fullClassName = packageName + PathConstant.DOT + className;

                    // 创建 MethodCallInfo
                    result.add(new MethodCallInfo(fullClassName, methodName, paramTypes));
                }
            }

            return result;
        } catch (IOException e) {
        }

        return null;
    }

    /**
     * 递归收集目录下所有 .java 文件
     */
    private List<File> collectJavaFiles(File dir) throws IOException {
        List<File> javaFiles = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    javaFiles.addAll(collectJavaFiles(file));
                } else if (file.getName().endsWith(PathConstant.DOT_JAVA_SUFFIX)) {
                    javaFiles.add(file);
                }
            }
        }
        return javaFiles;
    }

    /**
     * 移除Java代码中的注释
     */
    private String removeComments(String content) {
        return content.replaceAll("//.*|/\\*[\\s\\S]*?\\*/", "");
    }

    /**
     * 从内容中提取正则匹配的第一个分组
     */
    private String extractGroup(String content, Pattern pattern, int groupIndex) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(groupIndex);
        }
        return null;
    }

    /**
     * 解析参数类型列表
     */
    private List<String> parseParamTypes(String paramsStr) {
        List<String> paramTypes = new ArrayList<>();
        if (StringUtils.isBlank(paramsStr)) {
            return paramTypes;
        }

        String[] params = paramsStr.split(PathConstant.HYP_PARAM_SEPARATOR2);
        for (String param : params) {
            if (param.contains(PathConstant.DOT_DOT_DOT)) {
                String[] paramSegs = param.trim().split(PathConstant.ESCAPE_DOT_DOT_DOT);
                if (paramSegs.length != 2) {
                    return null;
                }
                String paramType = (paramSegs[0].contains(PathConstant.LEFT_ANGLE_BRACKET)
                        ? paramSegs[0].trim().substring(0,
                                paramSegs[0].trim().indexOf(PathConstant.LEFT_ANGLE_BRACKET))
                        : paramSegs[0].trim())
                        + PathConstant.ARR_BRACKETS;
                paramTypes.add(paramType);
            } else {
                String[] paramSegs = param.trim().split(" ");
                if (paramSegs.length != 2) {
                    return null;
                }
                String paramType = paramSegs[0].contains(PathConstant.LEFT_ANGLE_BRACKET)
                        ? paramSegs[0].trim().substring(0,
                                paramSegs[0].trim().indexOf(PathConstant.LEFT_ANGLE_BRACKET))
                        : paramSegs[0].trim();
                paramTypes.add(paramType);
            }
        }
        return paramTypes;
    }

}
