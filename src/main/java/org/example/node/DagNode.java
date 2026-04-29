package org.example.node;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.node.field.ClassInfo;
import org.example.node.field.FuncInfo;
import org.example.node.field.PackageInfo;

/**
 * DAG 节点
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DagNode {
    // 包信息，包含类名和真实类名对应的包路径
    private PackageInfo packageInfo;

    // 类信息
    private ClassInfo classInfo;

    // 方法信息
    private FuncInfo funcInfo;

    // 该节点是否循环调用了，一般是 false
    private boolean isLoopCall;

    // 子节点
    private List<DagNode> children;

    // 父节点
    private List<DagNode> parents;

    /**
     * 比较两个节点是否相等（节点信息上是否匹配，不包括子节点和父节点）
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DagNode dagNode = (DagNode) o;
        
        // 检查isLoopCall
        if (isLoopCall != dagNode.isLoopCall)
            return false;

        // 检查PackageInfo
        PackageInfo pkg1 = this.packageInfo;
        PackageInfo pkg2 = dagNode.packageInfo;
        if (pkg1 == null || pkg2 == null)
            return false;
        if (!java.util.Objects.equals(pkg1.getPackageName(), pkg2.getPackageName()))
            return false;
        if (!java.util.Objects.equals(pkg1.getRealPackageName(), pkg2.getRealPackageName()))
            return false;

        // 检查ClassInfo
        ClassInfo cls1 = this.classInfo;
        ClassInfo cls2 = dagNode.classInfo;
        if (cls1 == null || cls2 == null)
            return false;
        if (!java.util.Objects.equals(cls1.getDeclClassName(), cls2.getDeclClassName()))
            return false;
        if (!java.util.Objects.equals(cls1.getRealClassName(), cls2.getRealClassName()))
            return false;

        // 检查FuncInfo
        FuncInfo func1 = this.funcInfo;
        FuncInfo func2 = dagNode.funcInfo;
        if (func1 == null || func2 == null)
            return false;
        if (!java.util.Objects.equals(func1.getFuncName(), func2.getFuncName()))
            return false;
        if (!java.util.Objects.equals(func1.getFuncParams(), func2.getFuncParams()))
            return false;
        if (!java.util.Objects.equals(func1.getFuncParamsPackageName(), func2.getFuncParamsPackageName()))
            return false;

        return true;
    }

    /**
     * 计算节点的哈希码（节点信息上的哈希码，不包括子节点和父节点）
     */
    @Override
    public int hashCode() {
        int result = java.util.Objects.hashCode(isLoopCall);
        if (packageInfo != null) {
            result = 31 * result + java.util.Objects.hashCode(packageInfo.getPackageName());
            result = 31 * result + java.util.Objects.hashCode(packageInfo.getRealPackageName());
        }
        if (classInfo != null) {
            result = 31 * result + java.util.Objects.hashCode(classInfo.getDeclClassName());
            result = 31 * result + java.util.Objects.hashCode(classInfo.getRealClassName());
        }
        if (funcInfo != null) {
            result = 31 * result + java.util.Objects.hashCode(funcInfo.getFuncName());
            result = 31 * result + java.util.Objects.hashCode(funcInfo.getFuncParams());
            result = 31 * result + java.util.Objects.hashCode(funcInfo.getFuncParamsPackageName());
        }
        return result;
    }
}
