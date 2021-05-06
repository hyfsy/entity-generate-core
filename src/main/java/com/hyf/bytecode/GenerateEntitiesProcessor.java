package com.hyf.bytecode;

import com.google.auto.service.AutoService;
import com.sun.tools.javac.code.Attribute;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 注解处理器触发实体class文件生成
 *
 * @author baB_hyf
 * @date 2021/05/03
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({"com.hyf.bytecode.annotation.GenerateEntities", "com.hyf.bytecode.annotation.GenerateEntity"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class GenerateEntitiesProcessor extends AbstractProcessor {

    public static final String ANNOTATION_CLASS_ENTITIES_NAME  = "com.hyf.bytecode.annotation.GenerateEntities";
    public static final String ANNOTATION_CLASS_ENTITY_NAME    = "com.hyf.bytecode.annotation.GenerateEntity";
    public static final String ANNOTATION_ELEMENT_TABLE_NAME   = "value";
    public static final String ANNOTATION_ELEMENT_PACKAGE_NAME = "packageName";
    public static final String ANNOTATION_ELEMENT_CAMEL_CASE_NAME = "camelCase";

    private static Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        if (annotations.isEmpty()) {
            return false;
        }

        List<EntityMeta> entityMetaList = processAnnotation(annotations, roundEnv);

        println("----------process table---------");
        generateTable(entityMetaList);
        println("---------------end--------------");

        return false;
    }

    private List<EntityMeta> processAnnotation(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        List<EntityMeta> entityMetaList = new ArrayList<>();

        for (TypeElement annotation : annotations) {

            boolean multiProcess = false;
            Name qualifiedName = annotation.getQualifiedName();
            if (qualifiedName.contentEquals(ANNOTATION_CLASS_ENTITIES_NAME)) {
                multiProcess = true;
            }

            // 获取注解注释的类元素
            Set<? extends Element> elementsAnnotatedWith = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : elementsAnnotatedWith) {
                // 获取类上的注解镜像（包含注解值）
                List<? extends AnnotationMirror> annotationMirrors = element.getAnnotationMirrors();
                for (AnnotationMirror annotationMirror : annotationMirrors) {
                    // k: method, v: value
                    Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = annotationMirror
                            .getElementValues();

                    if (multiProcess) {
                        processEntities(entityMetaList, elementValues);
                    }
                    else {
                        processEntity(entityMetaList, elementValues);
                    }
                }
            }
        }

        entityMetaList = entityMetaList.stream().distinct().collect(Collectors.toList());
        return entityMetaList;
    }

    private void processEntities(List<EntityMeta> entityMetaList,
                                 Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {
        elementValues.forEach((k, v) -> {
            if (k.getSimpleName().contentEquals("value")) {
                @SuppressWarnings("unchecked")
                List<AnnotationValue> value = (List<AnnotationValue>) v.getValue();
                for (AnnotationValue annotationValue : value) {
                    Map<? extends ExecutableElement, ? extends AnnotationValue> annotationElementValues = ((Attribute.Compound) annotationValue)
                            .getElementValues();
                    processEntity(entityMetaList, annotationElementValues);
                }
            }
        });
    }

    private void processEntity(List<EntityMeta> entityMetaList,
                               Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues) {

        EntityMeta entityMeta = new EntityMeta();
        entityMeta.setCamelCase(true);

        for (ExecutableElement executableElement : elementValues.keySet()) {
            String elementName = executableElement.getSimpleName().toString();
            String annotationValue = elementValues.get(executableElement).getValue().toString().trim();

            switch (elementName) {
                case ANNOTATION_ELEMENT_TABLE_NAME:
                    entityMeta.setTableName(annotationValue);
                    break;
                case ANNOTATION_ELEMENT_PACKAGE_NAME:
                    entityMeta.setPackageName(annotationValue);
                    break;
                case ANNOTATION_ELEMENT_CAMEL_CASE_NAME:
                    entityMeta.setCamelCase(Boolean.parseBoolean(annotationValue));
                    break;
            }
        }

        entityMetaList.add(entityMeta);
    }

    private void generateTable(List<EntityMeta> entityMetaList) {
        Gen.generate(entityMetaList);
    }

    static void println(Object msg) {
        messager.printMessage(Diagnostic.Kind.NOTE, msg.toString());
    }

    static void printWarn(Object msg) {
        messager.printMessage(Diagnostic.Kind.WARNING, msg.toString());
    }

}
