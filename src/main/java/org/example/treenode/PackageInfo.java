package org.example.treenode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackageInfo {
    // 类名对应的包路径
    private String packageName;
    
    // 真实类名对应的包路径
    private String realPackageName;
}
