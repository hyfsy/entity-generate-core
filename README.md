
# 使用介绍

1. 在resources路径下放置一个jdbc.properties文件

2. 写入以下属性

```properties
# 暂时仅支持MySQL数据库
jdbc.generate.mysql.driver=xxx
jdbc.generate.mysql.url=xxx
jdbc.generate.mysql.username=xxx
jdbc.generate.mysql.password=xxx
```

3. 使用 `@GenerateEntity` 或 `@GenerateEntities` 注解注释在类上，指定编译的表名称即可。

```java
@GenerateEntity(
        value = "tableName",            // 表名
        packageName = "package.path",   // 类所在包名
        camelCase = true)               // 数据库字段用_分割的，是否转为驼峰
@GenerateEntity(value = "*table*Name*") // 表名支持通配符
@GenerateEntity("*")                    // 所有表生成
public class AutoTrigger {
}
```

4. 如果需要，也可在系统运行时手动生成
```java
public class HandTrigger {
    public static void main(String[] args) {
        EntityMeta entityMeta = new EntityMeta();
        entityMeta.setTableName("tableName");
        entityMeta.setPackageName("com.hyf.generate.entity");
        entityMeta.setCamelCase(true);
        Gem.generate(entityMeta);
    }
}
```

5. 模块依赖关系

test 依赖 core 依赖 third



6. 暂不支持

- mysql以外的数据库

- 热刷新




# FAQ

**1、编译报错**

需要clean core包

