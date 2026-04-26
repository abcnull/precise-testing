package org.example.resolver.parser;

import org.example.constant.PathConstant;

import com.github.javaparser.ast.body.Parameter;

public class ParameterParser {
    /**
     * 从参数声明器中解析出类型字符串，带有包名，带有泛型
     * 比如 a = ""，解析出 "" 对应 java.lang.String
     * 
     * @param param 参数声明器
     * @return 类型字符串
     */
    public String parseOutTypeStr(Parameter param) {
        if (param == null) {
            return null;
        }
        return param.getType().resolve().describe();
    }

    /**
     * 从参数声明器中解析出类型字符串，带有包名，去掉泛型
     * 比如 a = ""，解析出 "" 对应 java.lang.String
     * 
     * @param param 参数声明器
     * @return 类型字符串
     */
    public String parseOutTypeStrWithNoGeneric(Parameter param) {
        if (param == null) {
            return null;
        }
        String typeStr = param.getType().resolve().describe();
        if (typeStr != null && typeStr.contains(PathConstant.LEFT_ANGLE_BRACKET)) {
            typeStr = typeStr.substring(0, typeStr.indexOf(PathConstant.LEFT_ANGLE_BRACKET));
        }
        return typeStr;
    }

    /**
     * 从参数声明器中解析出基础类型字符串，无包名，带有泛型
     * 
     * @param param 参数声明器
     * @return 基础类型字符串
     */
    public String parseOutBaseTypeStr(Parameter param) {
        if (param == null) {
            return null;
        }
        return param.getType().asString();
    }

    /**
     * 从参数声明器中解析出基础类型字符串，无包名，去掉泛型
     * 
     * @param param 参数声明器
     * @return 基础类型字符串
     */
    public String parseOutBaseTypeStrWithNoGeneric(Parameter param) {
        if (param == null) {
            return null;
        }
        
        String typeStr = param.getType().asString(); // 不带有包名
        if (typeStr != null && typeStr.contains(PathConstant.LEFT_ANGLE_BRACKET)) {
            typeStr = typeStr.substring(0, typeStr.indexOf(PathConstant.LEFT_ANGLE_BRACKET));
        }
        return typeStr;
    }

}
