package com.hyf.bytecode.annotation;

import java.lang.annotation.*;

/**
 * 生成注解
 *
 * @author baB_hyf
 * @date 2021/05/03
 */
@Repeatable(GenerateEntities.class)
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface GenerateEntity {

    /**
     * generate tableName
     */
    String value() default "";

    /**
     * generated class package path
     */
    String packageName() default "";

    /**
     * map table_name field to the tableName
     */
    boolean camelCase() default true;
}
