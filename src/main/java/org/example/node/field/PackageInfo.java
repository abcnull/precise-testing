package org.example.node.field;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 包信息
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PackageInfo {
    // className 对应的包路径
    private String packageName;

    // realClassName 对应的包路径
    private String realPackageName;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PackageInfo that = (PackageInfo) o;
        return java.util.Objects.equals(packageName, that.packageName) &&
                java.util.Objects.equals(realPackageName, that.realPackageName);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(packageName, realPackageName);
    }
}
