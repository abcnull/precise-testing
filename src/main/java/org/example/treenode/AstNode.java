package org.example.treenode;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AstNode<T, U, V> {
    // 包信息，包含类名和真实类名对应的包路径
    private T packageInfo;

    // 类信息
    private U classInfo;

    // 方法信息
    private V funcInfo;

    // 该节点是否循环调用了，一般是 false
    private boolean isLoopCall;

    // 子节点
    private List<AstNode<T, U, V>> children;
}
