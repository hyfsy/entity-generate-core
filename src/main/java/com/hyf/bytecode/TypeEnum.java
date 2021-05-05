package com.hyf.bytecode;

import java.util.Date;
import java.util.Locale;

/**
 * 数据库字段类型映射的java类型
 *
 * @author baB_hyf
 * @date 2021/05/04
 */
enum TypeEnum {

    TINYINT(Integer.class),
    SMALLINT(Integer.class),
    INT(Integer.class),
    NUMBER(Integer.class),
    CHAR(String.class),
    VARCHAR(String.class),
    TEXT(String.class),
    DATE(Date.class),
    DATETIME(Date.class),
    TIME(Date.class),
    TIMESTAMP(Date.class),
    BOOLEAN(Boolean.class),
    FLOAT(Float.class),
    DOUBLE(Double.class),
    DECIMAL(Double.class),
    ;

    private final Class<?> clazz;

    TypeEnum(Class<?> clazz) {
        this.clazz = clazz;
    }

    public static TypeEnum getType(String type) {
        for (TypeEnum typeEnum : values()) {
            if (type.toUpperCase(Locale.ROOT).contains(typeEnum.name())) {
                return typeEnum;
            }
        }
        throw new RuntimeException("cannot find type: " + type);
    }

    public Class<?> getClazz() {
        return clazz;
    }
}
