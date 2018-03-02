package gs.autopojo.processor.tasks;

import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Generated;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import gs.autopojo.ExtraAnnotation;
import gs.autopojo.ExtraAnnotations;
import gs.autopojo.POJO;

import static com.google.auto.common.MoreElements.asExecutable;
import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreElements.asVariable;
import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.auto.common.MoreElements.isType;
import static com.google.auto.common.MoreTypes.isTypeOf;
import static gs.autopojo.processor.ElementsUtils.getFieldInitExpression;
import static gs.autopojo.processor.ElementsUtils.getPOJO;
import static gs.autopojo.processor.tasks.NamesHelper.getName;
import static gs.autopojo.processor.tasks.NamesHelper.getQualifiedName;
import static gs.autopojo.processor.tasks.NamesHelper.resolve;

public class ProcessClassTask implements Callable<GenClass> {
    private final Types types;
    private final Elements elements;
    private final TypeElement element;
    private final POJO pojo;
    private ClassName className;
    private TypeName classType;
    private TypeName classSuper;
    private TypeElement classSuperElement;
    private TypeSpec.Builder classSpec;
    private List<TypeVariableName> classVariables;
    private ClassName builderClassName;
    private TypeSpec.Builder builderSpec;
    private CodeBlock.Builder builderFillInstance;

    public ProcessClassTask(Types types, Elements elements, TypeElement element) {
        this.types = types;
        this.elements = elements;
        this.element = element;
        this.pojo = getPOJO(elements, element);
    }

    @Override
    public GenClass call() {
        Consumer<Element> throwIfMissing = $ -> {
            throw new IllegalArgumentException("Missing " + POJO.class + " annotation on " + $);
        };

        if (pojo == null) {
            throwIfMissing.accept(element);
        }
        for (Element parent = element.getEnclosingElement(); isType(parent); parent = parent.getEnclosingElement()) {
            if (parent.getAnnotation(POJO.class) == null) {
                throwIfMissing.accept(parent);
            }
        }

        className = getName(elements, element);
        buildClassSpec();
        buildBuilderClassSpec();

        processElements();

        if (builderSpec != null) {
            classSpec.addType(builderSpec
                    .addMethod(MethodSpec.methodBuilder("fillInstance")
                            .addModifiers(Modifier.PROTECTED)
                            .addParameter(classType, "instance")
                            .addCode(builderFillInstance.build())
                            .build())
                    .addMethod(MethodSpec.methodBuilder("build")
                            .addModifiers(Modifier.PUBLIC)
                            .returns(classType)
                            .addCode("$1T instance = new $1T();\nfillInstance(instance);\nreturn instance;\n", classType)
                            .build())
                    .build());
        }
        return new GenClass(className, element, classSpec);
    }

    private void buildClassSpec() {
        classVariables = collectTypeVariables(element);

        classSpec = TypeSpec.classBuilder(className)
                .addOriginatingElement(element)
                .addModifiers(collectModifiers(element, Modifier.ABSTRACT))
                .addAnnotations(collectAnnotations(element, ExtraAnnotation.ApplyOn.CLASS))
                .addTypeVariables(classVariables)
                .addSuperinterfaces(collectInterfaces(element));

        classType = classVariables.isEmpty() ? className :
                ParameterizedTypeName.get(className, element.getTypeParameters().stream()
                        .map($ -> TypeName.get($.asType()))
                        .toArray(TypeName[]::new));

        TypeElement[] classSupers = Stream.concat(Stream.of(element.getSuperclass()), element.getInterfaces().stream())
                .filter($ -> $.getKind() == TypeKind.DECLARED)
                .map(MoreTypes::asTypeElement)
                .filter($ -> getPOJO(elements, $) != null)
                .toArray(TypeElement[]::new);

        switch (classSupers.length) {
            case 0:
                break;

            case 1:
                classSuperElement = classSupers[0];
                classSpec.superclass(classSuper = resolve(elements, ClassName.get(classSuperElement)));
                break;

            default:
                throw new IllegalArgumentException("More than 1 " + POJO.class + " as superclass on " + element);
        }
    }

    private void buildBuilderClassSpec() {
        if (pojo.builder()) {
            builderClassName = className.nestedClass("Builder");
            builderSpec = TypeSpec.classBuilder(builderClassName.simpleName())
                    .addOriginatingElement(element)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addTypeVariables(classVariables);
            builderFillInstance = CodeBlock.builder();

            if (classSuper != null) {
                builderFillInstance.add("super.fillInstance(instance);\n");

                TypeName superClass;
                if (classSuper instanceof ParameterizedTypeName) {
                    ParameterizedTypeName ptName = (ParameterizedTypeName) classSuper;

                    superClass = ParameterizedTypeName.get(
                            ptName.rawType.nestedClass(builderClassName.simpleName()),
                            ptName.typeArguments.toArray(new TypeName[ptName.typeArguments.size()]));
                } else {
                    superClass = ((ClassName) classSuper).nestedClass(builderClassName.simpleName());
                }
                builderSpec.superclass(superClass);

                // adds superclass overrides
                getLocalAndInheritedMethods(classSuperElement, types, elements).stream()
                        .filter($ -> $.getKind() == ElementKind.METHOD)
                        .map($ -> {
                            String name = $.getSimpleName().toString();
                            TypeName typeName = TypeName.get($.getReturnType());

                            return MethodSpec.methodBuilder(name)
                                    .addModifiers(Modifier.PUBLIC)
                                    .addAnnotation(Override.class)
                                    .addParameter(typeName, name)
                                    .returns(builderClassName)
                                    .addCode("super.$1N($1N);\nreturn this;\n", name)
                                    .build();
                        })
                        .forEachOrdered(builderSpec::addMethod);
            }
        }
    }

    private void processElements() {
        Modifier[] modifiers = collectModifiers(element, Modifier.ABSTRACT, Modifier.STATIC);

        for (Element member : element.getEnclosedElements()) {
            String name = member.getSimpleName().toString();

            switch (member.getKind()) {
                case METHOD:
                    ExecutableElement method = (ExecutableElement) member;
                    if (method.getParameters().isEmpty()) {
                        addField(member, name, modifiers, method.getReturnType());
                        continue;
                    }
                    break;

                case FIELD:
                    if (member.getModifiers().contains(Modifier.STATIC)) {
                        addConstant(asVariable(member));
                        continue;
                    }
                    break;

                case ENUM:
                    addEnum(asType(member));
                    continue;
            }

            if (isType(member)) {
                // TODO fork this?
                classSpec.addType(new ProcessClassTask(types, this.elements, asType(member)).call().typeSpec.build());

            } else {
                throw new IllegalArgumentException("unsupported " + member.getKind() + ": " + member);
            }
        }
    }

    private void addField(Element member, String name, Modifier[] modifiers, TypeMirror type) {
        final String methodSuffix = name.substring(0, 1).toUpperCase() + name.substring(1);

        TypeName typeName = resolve(elements, TypeName.get(type));

        classSpec
                .addField(FieldSpec.builder(typeName, name, Modifier.PRIVATE)
                        .addAnnotations(collectAnnotations(member, ExtraAnnotation.ApplyOn.FIELD))
                        .build())
                .addMethod(MethodSpec.methodBuilder("get" + methodSuffix)
                        .addModifiers(modifiers)
                        .addAnnotations(collectAnnotations(member, ExtraAnnotation.ApplyOn.GETTER))
                        .returns(typeName)
                        .addCode("return $N;\n", name)
                        .build())
                .addMethod(MethodSpec.methodBuilder("set" + methodSuffix)
                        .addModifiers(modifiers)
                        .addAnnotations(collectAnnotations(member, ExtraAnnotation.ApplyOn.SETTER))
                        .addParameter(typeName, name)
                        .addCode("this.$1N = $1N;\n", name)
                        .build());

        if (builderSpec != null) {
            builderSpec
                    .addField(FieldSpec.builder(typeName, name, Modifier.PRIVATE)
                            .build())
                    .addMethod(MethodSpec.methodBuilder(name)
                            .addModifiers(Modifier.PUBLIC)
                            .returns(typeName)
                            .addCode("return $1N;\n", name)
                            .build())
                    .addMethod(MethodSpec.methodBuilder(name)
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(typeName, name)
                            .returns(builderClassName)
                            .addCode("this.$1N = $1N;\nreturn this;\n", name)
                            .build());

            builderFillInstance.add("instance.$1N = $1N;\n", name);
        }
    }

    private void addConstant(VariableElement element) {
        TypeName typeName = resolve(elements, TypeName.get(element.asType()));
        String name = element.getSimpleName().toString();
        Object value = getFieldInitExpression(elements, element);

        classSpec.addField(FieldSpec.builder(typeName, name, collectModifiers(element))
                .initializer("$L", value)
                .build());
    }

    private void addEnum(TypeElement element) {
        TypeSpec.Builder enumSpec = TypeSpec.enumBuilder(element.getSimpleName().toString())
                .addOriginatingElement(element)
                .addModifiers(collectModifiers(element, Modifier.FINAL));

        for (Element member : element.getEnclosedElements()) {
            switch (member.getKind()) {
                case CONSTRUCTOR:
                    if (((ExecutableElement) member).getParameters().isEmpty()) {
                        // skip default constructor
                        continue;
                    }

                case ENUM_CONSTANT:
                    enumSpec.addEnumConstant(asVariable(member).getSimpleName().toString());
                    break;

                case METHOD:
                    ExecutableElement method = asExecutable(member);
                    if ((method.getSimpleName().contentEquals("values") && method.getParameters().isEmpty()) ||
                            (method.getSimpleName().contentEquals("valueOf") && method.getParameters().size() == 1 && isTypeOf(String.class, method.getParameters().get(0).asType()))) {
                        continue;
                    }

                default:
                    throw new IllegalArgumentException("unsupported " + member.getKind() + ": " + member);

            }
        }

        classSpec.addType(enumSpec.build());
    }

    private Modifier[] collectModifiers(Element element, Modifier... ignore) {
        List<Modifier> toIgnore = Arrays.asList(ignore);
        return element.getModifiers().stream()
                .filter($ -> !toIgnore.contains($))
                .toArray(Modifier[]::new);
    }

    private List<AnnotationSpec> collectAnnotations(Element element, ExtraAnnotation.ApplyOn applyOn) {
        List<TypeName> toRemove = Stream.of(POJO.class, Generated.class, Target.class, Retention.class)
                .map(TypeName::get)
                .collect(Collectors.toList());

        return elements.getAllAnnotationMirrors(element).stream()
                .flatMap($ -> expandExtraAnnotations(element, $, applyOn))
                .filter($ -> !toRemove.contains($.type))
                .collect(Collectors.toList());
    }

    private List<TypeVariableName> collectTypeVariables(TypeElement element) {
        return element.getTypeParameters().stream()
                .map(TypeVariableName::get)
                .map($ -> resolve(elements, $))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private Stream<AnnotationSpec> expandExtraAnnotations(AnnotatedConstruct element, AnnotationMirror mirror, ExtraAnnotation.ApplyOn applyOn) {
        Stream<AnnotationSpec> r;

        if (isTypeOf(ExtraAnnotations.class, mirror.getAnnotationType())) {
            ExtraAnnotations annotations = element.getAnnotation(ExtraAnnotations.class);

            r = Stream.of(annotations.value())
                    .flatMap($ -> buildExtraAnnotationSpec($, applyOn));

        } else if (isTypeOf(ExtraAnnotation.class, mirror.getAnnotationType())) {
            ExtraAnnotation annotation = element.getAnnotation(ExtraAnnotation.class);

            r = buildExtraAnnotationSpec(annotation, applyOn);

        } else {
            r = Stream.of(AnnotationSpec.get(mirror));
        }

        Element mirrorElement = mirror.getAnnotationType().asElement();
        if (mirrorElement.getAnnotation(POJO.class) != null) {
            r = Stream.concat(r, elements.getAllAnnotationMirrors(mirrorElement).stream()
                    .flatMap($ -> expandExtraAnnotations(mirrorElement, $, applyOn)));
        }
        return r;
    }

    private Stream<AnnotationSpec> buildExtraAnnotationSpec(ExtraAnnotation annotation, ExtraAnnotation.ApplyOn applyOn) {
        if (annotation.applyOn().length == 0 || Arrays.asList(annotation.applyOn()).contains(applyOn)) {
            AnnotationSpec.Builder builder = AnnotationSpec.builder(ClassName.bestGuess(annotation.value()));
            for (ExtraAnnotation.Member member : annotation.members()) {
                builder.addMember(member.name(), member.format(), member.value());
            }
            return Stream.of(builder.build());
        }
        return Stream.empty();
    }

    private List<TypeName> collectInterfaces(TypeElement element) {
        return element.getInterfaces().stream()
                .map($ -> resolve(elements, ClassName.get($)))
                .filter(this::isInterface)
                .collect(Collectors.toList());
    }

    private boolean isInterface(TypeName name) {
        ClassName className = null;

        if (name instanceof ClassName) {
            className = (ClassName) name;

        } else if (name instanceof ParameterizedTypeName) {
            className = ((ParameterizedTypeName) name).rawType;
        }

        if (className != null) {
            className = resolve(elements, className);
            String qualifiedName = getQualifiedName(className);
            return qualifiedName != null && elements.getTypeElement(qualifiedName).getKind() == ElementKind.INTERFACE;
        }
        return false;
    }

}
