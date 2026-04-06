package org.example.node;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.node.field.ClassInfo;
import org.example.node.field.FuncInfo;
import org.example.node.field.PackageInfo;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AstNode {
    // 包信息，包含类名和真实类名对应的包路径
    private PackageInfo packageInfo;

    // 类信息
    private ClassInfo classInfo;

    // 方法信息
    private FuncInfo funcInfo;

    // 该节点是否循环调用了，一般是 false
    private boolean isLoopCall;

    // 子节点
    private List<AstNode> children;

    // 父节点
    private List<AstNode> parents;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AstNode astNode = (AstNode) o;
        return isLoopCall == astNode.isLoopCall &&
                java.util.Objects.equals(packageInfo, astNode.packageInfo) &&
                java.util.Objects.equals(classInfo, astNode.classInfo) &&
                java.util.Objects.equals(funcInfo, astNode.funcInfo);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(packageInfo, classInfo, funcInfo, isLoopCall);
    }
}
