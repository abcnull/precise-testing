package org.example.resolver.print;

import java.util.List;

import org.example.constant.WordConstant;
import org.example.node.DagNode;
import org.example.util.SignatureUtil;

/**
 * 打印 dag
 */
public class PrintDag {
    /**
     * 打印 dag 中调用链信息，从上往下查找打印整个树形结构
     * User#func(String, Object, int)
     * └── User#func2(String, Object, int)
     * 
     * @param anyNode 任意节点，用于触发从上到下递归打印
     */
    public void printSimpleCallChains(DagNode anyNode) {
        printSimpleCallChainsRecursive(anyNode, 0, true, "");
    }

    /**
     * 递归打印调用链
     */
    private void printSimpleCallChainsRecursive(DagNode node, int depth, boolean isLast, String prefix) {
        if (node == null || node.getFuncInfo() == null || node.getClassInfo() == null) {
            return;
        }

        String connector = depth == 0 ? "" : (isLast ? "└── " : "├── ");
        String currentPrefix = prefix + connector;

        String nodeStr = formatNodeToString(node);
        System.out.println(currentPrefix + nodeStr);

        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            int childCount = node.getChildren().size();
            for (int i = 0; i < childCount; i++) {
                boolean childIsLast = (i == childCount - 1);
                String childPrefix = prefix + (isLast ? "    " : "│   ");
                printSimpleCallChainsRecursive(node.getChildren().get(i), depth + 1, childIsLast, childPrefix);
            }
        }
    }

    /**
     * 格式化节点为字符串
     */
    private String formatNodeToString(DagNode node) {
        if (node.getFuncInfo() == null || node.getClassInfo() == null) {
            return "";
        }

        String className = node.getClassInfo().getDeclClassName();
        String methodName = node.getFuncInfo().getFuncName();
        List<String> params = node.getFuncInfo().getFuncParams();

        // User#func(String, Object, int)
        StringBuilder sb = new StringBuilder(SignatureUtil.buildSimpleMethodSignature(className, methodName, params));
        if (node.isLoopCall()) {
            sb.append(" " + WordConstant.LOOP_CALL);
        }

        return sb.toString();
    }

}
