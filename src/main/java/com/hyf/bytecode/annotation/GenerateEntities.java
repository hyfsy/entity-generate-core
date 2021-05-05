package com.hyf.bytecode.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 生成注解
 *
 * @author baB_hyf
 * @date 2021/05/03
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface GenerateEntities {
    GenerateEntity[] value();
}
