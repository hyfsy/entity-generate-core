package com.hyf.bytecode;

import org.objectweb.asm.*;
import sun.tools.java.CompilerError;

import java.io.*;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 类文件生成工具
 *
 * @author baB_hyf
 * @date 2021/05/03
 */
public class Gen implements Opcodes {

    public static final  String       JDBC_PROPERTIES_NAME     = "jdbc.properties";
    public static final  String       PROPERTIES_NAME_DRIVER   = "jdbc.generate.mysql.driver";
    public static final  String       PROPERTIES_NAME_URL      = "jdbc.generate.mysql.url";
    public static final  String       PROPERTIES_NAME_USERNAME = "jdbc.generate.mysql.username";
    public static final  String       PROPERTIES_NAME_PASSWORD = "jdbc.generate.mysql.password";
    private static final List<String> cacheAllTableList        = new ArrayList<>();
    private static       String       driver;
    private static       String       url;
    private static       String       username;
    private static       String       password;
    private static       String       schema;

    static {
        try {
            InputStream resource = Gen.class.getResourceAsStream("/" + JDBC_PROPERTIES_NAME);
            ifNull(resource, "Cannot find the jdbc config file: " + Gen.class.getResource("/" + JDBC_PROPERTIES_NAME).getPath());

            Properties properties = new Properties();
            properties.load(resource);

            driver = properties.getProperty(PROPERTIES_NAME_DRIVER);
            url = properties.getProperty(PROPERTIES_NAME_URL);
            username = properties.getProperty(PROPERTIES_NAME_USERNAME);
            password = properties.getProperty(PROPERTIES_NAME_PASSWORD);
            ifNull(driver, "Required properties miss: " + PROPERTIES_NAME_DRIVER);
            ifNull(url, "Required properties miss: " + PROPERTIES_NAME_URL);
            ifNull(username, "Required properties miss: " + PROPERTIES_NAME_USERNAME);
            ifNull(password, "Required properties miss: " + PROPERTIES_NAME_PASSWORD);

            if (!url.contains("mysql")) {
                throw new UnsupportedOperationException("For the moment, only support the mysql database.");
            }

            schema = url.substring(0, url.lastIndexOf('?'));
            schema = schema.substring(url.lastIndexOf('/') + 1);

            Class.forName(driver);

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    static {
        executeSql("show tables", rs -> {
            while (rs.next()) {
                cacheAllTableList.add(rs.getString(1));
            }
            return null;
        });
    }

    public static void generate(String tableName) {
        generate(tableName, "");
    }

    public static void generate(String tableName, String packageName) {
        EntityMeta entityMeta = new EntityMeta();
        entityMeta.setTableName(tableName);
        entityMeta.setPackageName(packageName);
        entityMeta.setCamelCase(true);
        generate(Collections.singletonList(entityMeta));
    }

    public static void generate(EntityMeta entityMeta) {
        generate(Collections.singletonList(entityMeta));
    }

    public static void generate(List<EntityMeta> entityMetaList) {
        // 过滤表
        Map<String, EntityMeta> generateTableMap = getGenerateTableMap(entityMetaList);

        for (Map.Entry<String, EntityMeta> entry : generateTableMap.entrySet()) {
            String tableName = entry.getKey();
            EntityMeta entityMeta = entry.getValue();
            String packageName = entityMeta.getPackageName();
            boolean camelCase = entityMeta.getCamelCase();
            if (tableName == null || tableName.trim().length() <= 0) {
                return;
            }

            if (!checkTableExists(tableName)) {
                return;
            }

            Map<String, TypeEnum> columnMap = getTableColumnMap(tableName);
            if (columnMap.isEmpty()) {
                return;
            }

            println("tableName: " + tableName + ", entityMeta: " + entityMeta);

            String className = camelCaseUpper(tableName, "_");

            ClassWriter classWriter = new ClassWriter(0);

            // basic
            String fullClassName = className;
            if (packageName != null && packageName.trim().length() > 0) {
                fullClassName = packageName.replace('.', '/') + "/" + className;
            }
            String _fullClassName = "L" + fullClassName + ";";
            String superName = "com/epoint/core/BaseEntity";
            String[] interfaces = {"java/lang/Cloneable"};
            classWriter.visit(52, ACC_PUBLIC | ACC_SUPER, fullClassName, null, superName, interfaces);
            classWriter.visitSource(className + ".java", null);

            // @Entity
            String annotationSignature = "Lcom/epoint/core/annotation/Entity;";
            AnnotationVisitor annotationVisitor = classWriter.visitAnnotation(annotationSignature, true);
            annotationVisitor.visit("table", tableName);
            String idColumnName = getIdColumnName(tableName);
            if (idColumnName != null) {
                AnnotationVisitor idAnnotationVisitor = annotationVisitor.visitArray("id");
                idAnnotationVisitor.visit(null, idColumnName);
                idAnnotationVisitor.visitEnd();
            }
            annotationVisitor.visitEnd();

            // <init>
            String parentClassName = "com/epoint/core/BaseEntity";
            MethodVisitor initMethod = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            initMethod.visitCode();
            initMethod.visitVarInsn(ALOAD, 0);
            initMethod.visitMethodInsn(INVOKESPECIAL, parentClassName, "<init>", "()V", false);
            initMethod.visitInsn(RETURN);
            initMethod.visitMaxs(1, 1);
            initMethod.visitEnd();

            String[] fieldNameArray = columnMap.keySet().toArray(new String[0]);
            for (String fieldName : fieldNameArray) {
                TypeEnum typeEnum = columnMap.get(fieldName);

                if (camelCase) {
                    fieldName = camelCaseUpper(fieldName, "_");
                    fieldName = lower(fieldName);
                }

                Class<?> clazz = typeEnum.getClazz();
                String typeName = clazz.getTypeName();
                String fieldType = "L" + typeName.replace(".", "/") + ";";

                boolean checkNull = false;

                // 特殊处理
                if (clazz == Integer.class) {
                    checkNull = true;
                }

                // getter
                {
                    String getterMethodName = "get" + upper(fieldName);
                    String getterMethodDescriptor = "()" + fieldType;
                    String getMethodName = "get";
                    String getMethodDescriptor = "(Ljava/lang/String;)Ljava/lang/Object;";

                    MethodVisitor getterMethodVisitor = classWriter.visitMethod(ACC_PUBLIC, getterMethodName,
                            getterMethodDescriptor, null, null);
                    getterMethodVisitor.visitCode();
                    Label startLabel = new Label();
                    getterMethodVisitor.visitLabel(startLabel);
                    getterMethodVisitor.visitMaxs(2, 1);
                    getterMethodVisitor.visitVarInsn(ALOAD, 0);
                    getterMethodVisitor.visitLdcInsn(fieldName);
                    getterMethodVisitor.visitMethodInsn(INVOKESPECIAL, parentClassName, getMethodName, getMethodDescriptor,
                            false);
                    getterMethodVisitor.visitTypeInsn(CHECKCAST, fieldType);

                    Label storeLabel = new Label();

                    if (checkNull) {
                        getterMethodVisitor.visitVarInsn(ASTORE, 1);
                        getterMethodVisitor.visitLabel(storeLabel);
                        getterMethodVisitor.visitVarInsn(ALOAD, 1);
                        Label ifNonNull = new Label();
                        getterMethodVisitor.visitJumpInsn(IFNONNULL, ifNonNull);
                        getterMethodVisitor.visitInsn(ICONST_0);
                        Label gotoLabel = new Label();
                        getterMethodVisitor.visitJumpInsn(GOTO, gotoLabel);
                        getterMethodVisitor.visitLabel(ifNonNull);
                        getterMethodVisitor.visitFrame(Opcodes.F_APPEND, 1, new Object[]{"java/lang/Integer"}, 0, null);
                        getterMethodVisitor.visitVarInsn(ALOAD, 1);
                        getterMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                        getterMethodVisitor.visitLabel(gotoLabel);
                        getterMethodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{Opcodes.INTEGER});
                        getterMethodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                        getterMethodVisitor.visitMaxs(2, 2);
                    }

                    Label endLabel = new Label();
                    getterMethodVisitor.visitLabel(endLabel);
                    getterMethodVisitor.visitInsn(ARETURN);
                    getterMethodVisitor.visitLocalVariable("this", _fullClassName, null, startLabel, endLabel, 0);
                    if (checkNull) {
                        getterMethodVisitor.visitLocalVariable(fieldName, fieldType, null, storeLabel, endLabel, 1);
                    }
                    getterMethodVisitor.visitEnd();
                }
                // setter
                {
                    String setterMethodName = "set" + upper(fieldName);
                    String setterMethodDescriptor = "(" + fieldType + ")V";
                    String setMethodName = "set";
                    String setMethodDescriptor = "(Ljava/lang/String;Ljava/lang/Object;)Lcom/epoint/core/grammar/Record;";

                    MethodVisitor setterMethodVisitor = classWriter.visitMethod(ACC_PUBLIC, setterMethodName,
                            setterMethodDescriptor, null, null);
                    setterMethodVisitor.visitParameter(fieldName, 0);
                    setterMethodVisitor.visitCode();
                    Label startLabel = new Label();
                    setterMethodVisitor.visitLabel(startLabel);
                    setterMethodVisitor.visitVarInsn(ALOAD, 0);
                    setterMethodVisitor.visitLdcInsn(fieldName);
                    setterMethodVisitor.visitVarInsn(ALOAD, 1);
                    setterMethodVisitor.visitMethodInsn(INVOKESPECIAL, parentClassName, setMethodName, setMethodDescriptor,
                            false);
                    setterMethodVisitor.visitInsn(POP);
                    setterMethodVisitor.visitInsn(RETURN);
                    Label endLabel = new Label();
                    setterMethodVisitor.visitLabel(endLabel);
                    setterMethodVisitor.visitLocalVariable("this", _fullClassName, null, startLabel, endLabel, 0);
                    setterMethodVisitor.visitLocalVariable(fieldName, fieldType, null, startLabel, endLabel, 1);
                    setterMethodVisitor.visitMaxs(3, 2);
                    setterMethodVisitor.visitEnd();
                }
            }

            writeToFile(classWriter, fullClassName);
        }
    }

    /**
     * 通过表的元数据，获取数据库中所有匹配的表名与元数据对应的映射信息
     *
     * @param entityMetaList 表的元数据列表
     * @return 数据库中匹配的表名与元数据对应的映射信息
     */
    private static Map<String, EntityMeta> getGenerateTableMap(List<EntityMeta> entityMetaList) {
        println(entityMetaList);
        Map<String, EntityMeta> generateTableMap = new LinkedHashMap<>();
        List<String> allTableList = new ArrayList<>(cacheAllTableList);

        // 多个通配符的情况下，排序成具体数量的优先
        Map<String, EntityMeta> orderTreeMap = new TreeMap<>((o1, o2) -> {
            String r1 = o1.replace(" ", "").replace("*", "");
            String r2 = o2.replace(" ", "").replace("*", "");
            int i = r2.length() - r1.length();
            // TreeMap在put的时候，会通过 Comparator 比较，而不是equals...
            return i == 0 ? r1.compareTo(r2) : i;
        });

        Map<String, EntityMeta> tableNameMap = entityMetaList.stream().collect(
                Collectors.toMap(EntityMeta::getTableName, mata -> mata, (meta1, meta2) -> {
                    throw new RuntimeException("Same table name: " + meta1.getTableName());
                }, () -> orderTreeMap));

        for (String wildCardName : tableNameMap.keySet()) {
            Iterator<String> it = allTableList.iterator();
            while (it.hasNext()) {
                String tableName = it.next();
                boolean match = wildCardMatch(tableName, wildCardName);
                if (match) {
                    it.remove();
                    generateTableMap.put(tableName, tableNameMap.get(wildCardName));
                }
            }
        }

        return generateTableMap;
    }

    /**
     * 测试使用
     */
    @Deprecated
    private static List<String> getGenerateTableList(List<String> tableNameList) {
        List<String> generateTableList = new ArrayList<>();
        List<String> allTableList = new ArrayList<>(cacheAllTableList);

        for (String wildCardName : tableNameList) {
            Iterator<String> it = allTableList.iterator();
            while (it.hasNext()) {
                String tableName = it.next();
                boolean match = wildCardMatch(tableName, wildCardName);
                if (match) {
                    it.remove();
                    generateTableList.add(tableName);
                }
            }
        }

        return generateTableList;
    }

    /**
     * 匹配通配符
     *
     * @param originName   匹配的字符串
     * @param wildCardName 匹配的字符串，包含通配符
     * @return 匹配成功返回true，否则返回false
     */
    public static boolean wildCardMatch(String originName, String wildCardName) {
        originName = originName.trim();
        wildCardName = wildCardName.trim();

        if (originName.equals(wildCardName)) {
            return true;
        }
        if (!wildCardName.contains("*")) {
            return false;
        }

        String[] segment = wildCardName.split("\\*");


        // ***a***a***
        List<String> segmentList = Arrays.asList(segment);
        segmentList = new ArrayList<>(segmentList);
        segmentList.removeIf(String::isEmpty);

        // aa*a*
        if (wildCardName.endsWith("*")) {
            segmentList.add("");
        }

        segment = segmentList.toArray(new String[0]);
        int length = segment.length;

        // other one and "*"
        if (length == 1) {
            return originName.startsWith(segment[0]);
        }

        if (length >= 2) {
            if (originName.startsWith(segment[0]) && originName.endsWith(segment[length - 1])) {
                boolean match = true;
                if (length > 2) {
                    String remainString = originName.substring(segment[0].length() + 1, originName.length() - segment[length - 1].length());
                    for (int i = 1; i < segment.length - 1; i++) {
                        String s = segment[i];
                        int index;
                        if ((index = remainString.indexOf(s)) < 0) {
                            match = false;
                            break;
                        }
                        remainString = remainString.substring(index + 1);
                    }
                }

                return match;
            }
        }

        return false;
    }

    /**
     * 字符串转驼峰命名
     *
     * @param s     源字符串
     * @param split 字符串分割符
     * @return 驼峰歌格式的字符串
     */
    private static String camelCaseUpper(String s, String split) {
        String[] strArray = s.split(split);
        for (int i = 0; i < strArray.length; i++) {
            strArray[i] = upper(strArray[i]);
        }
        StringBuilder sb = new StringBuilder();
        for (String str : strArray) {
            sb.append(str);
        }
        return sb.toString();
    }

    /**
     * 通过表名获取所有列名和类型的映射
     *
     * @param tableName 表名
     * @return 列名和类型的映射
     */
    private static Map<String, TypeEnum> getTableColumnMap(String tableName) {
        Map<String, TypeEnum> column = new LinkedHashMap<>();

        // get table info
        executeSql("describe " + tableName, rs -> {
            while (rs.next()) {
                String name = rs.getString(1);
                String type = rs.getString(2);
                TypeEnum typeEnum = TypeEnum.getType(type);
                column.put(name, typeEnum);
            }
            return null;
        });
        return column;
    }

    /**
     * 封装执行sql的方法
     *
     * @param sql  sql语句
     * @param func Function计算，可消费ResultSet，逻辑由用户提供，需要返回结果
     * @return 用户返回的结果
     */
    private static Object executeSql(String sql, NoNeedDealExceptionFunction<ResultSet, Object> func) {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            try (ResultSet rs = statement.getResultSet()) {
                return func.call(rs);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 校验数据库中是否存在该表信息
     *
     * @param tableName 表名称
     * @return 数据库中存在该表则返回true，否则返回false
     */
    private static boolean checkTableExists(String tableName) {
        // check table exist
        String sqlFormat = "select count(*) from information_schema.TABLES t where t.TABLE_SCHEMA ='%s' and t.TABLE_NAME ='%s'";
        Object result = executeSql(String.format(sqlFormat, schema, tableName), rs -> {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        });

        boolean exists = result != null && Boolean.parseBoolean(result.toString());
        if (!exists) {
            GenerateEntitiesProcessor.printWarn("Table not exist: " + tableName);
        }
        return exists;
    }

    /**
     * 获取表对应的主键（PK）
     *
     * @param tableName 表名
     * @return 主键字段名称，没有则返回null
     */
    public static String getIdColumnName(String tableName) {
        String sqlFormat = "select column_name from information_schema.KEY_COLUMN_USAGE where CONSTRAINT_SCHEMA = '%s' and TABLE_NAME = '%s' and CONSTRAINT_NAME = 'PRIMARY'";
        Object idColumnName = executeSql(String.format(sqlFormat, schema, tableName), rs -> {
            if (rs.next()) {
                return rs.getString(1);
            }
            return null;
        });
        return idColumnName == null ? null : idColumnName.toString();
    }

    /**
     * class写入文件
     *
     * @param classWriter   classWriter对象
     * @param fullClassName 类文件路径，如 com/hyf/Hello
     */
    private static void writeToFile(ClassWriter classWriter, String fullClassName) {
        ClassLoader classLoader = Gen.class.getClassLoader();
        URL resource = classLoader.getResource("");
        if (resource != null) {
            String savePath = resource.getPath().substring(1);
            byte[] bytes = classWriter.toByteArray();
            File file = new File(savePath + fullClassName + ".class");
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));) {
                bos.write(bytes);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 字符串首字母大写
     *
     * @param s 字符串
     * @return 首字母大写的字符串
     */
    private static String upper(String s) {
        char[] chars = s.toCharArray();
        chars[0] -= 32;
        return String.valueOf(chars);
    }

    @Deprecated
    private static String lower(String s) {
        char[] chars = s.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private static void println(Object msg) {
        GenerateEntitiesProcessor.println(msg);
    }

    public static void ifNull(Object o, String msg) {
        if (o == null) {
            throw new CompilerError(msg);
        }
    }

    /**
     * 移除Function的异常处理情况
     */
    @FunctionalInterface
    interface NoNeedDealExceptionFunction<T, R> extends Function<T, R> {

        R call(T t) throws Exception;

        @Deprecated
        @Override
        default R apply(T t) {
            try {
                return call(t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
