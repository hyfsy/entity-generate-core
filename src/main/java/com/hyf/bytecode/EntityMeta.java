package com.hyf.bytecode;

import java.util.Objects;

/**
 * 注解元数据信息
 *
 * @author baB_hyf
 * @date 2021/05/04
 */
public class EntityMeta {

    private String tableName;
    private String packageName;
    private boolean camelCase;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public boolean getCamelCase() {
        return camelCase;
    }

    public void setCamelCase(boolean camelCase) {
        this.camelCase = camelCase;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EntityMeta that = (EntityMeta) o;
        return tableName.equals(that.tableName) && Objects.equals(packageName, that.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableName, packageName);
    }

    @Override
    public String toString() {
        return "{tableName='" + tableName + "', packageName='" + packageName + "', camelCase='" + camelCase + "'}";
    }
}
