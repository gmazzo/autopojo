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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Generated;
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

import gs.autopojo.ExtraAnnotation;
import gs.autopojo.ExtraAnnotations;
import gs.autopojo.POJO;

import static com.google.auto.common.MoreElements.asExecutable;
import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreElements.asVariable;
import static com.google.auto.common.MoreElements.isType;
import static com.google.auto.common.MoreTypes.isTypeOf;
import static gs.autopojo.processor.ElementsUtils.getFieldInitExpression;
import static gs.autopojo.processor.tasks.NamesHelper.getName;
import static gs.autopojo.processor.tasks.NamesHelper.getQualifiedName;
import static gs.autopojo.processor.tasks.NamesHelper.resolve;

public class ProcessClassTask implements Callable<GenClass> {
    private final Elements elements;
    private final TypeElement element;
    private final POJO pojo;
    private ClassName className;
    private TypeSpec.Builder classSpec;
    private ClassName builderClassName;
    private List<TypeVariableName> classVariables;
    private TypeSpec.Builder builderSpec;
    private CodeBlock.Builder builderBuildCode;
    private TypeName builderType;

    public ProcessClassTask(Elements elements, TypeElement element) {
        this.elements = elements;
        this.element = element;
        this.pojo = element.getAnnotation(POJO.class);
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

        className = getName(element);
        buildClassSpec();
        buildBuilderClassSpec();

        processElements();

        if (builderSpec != null) {
            builderBuildCode.add("return instance;\n");

            classSpec.addType(builderSpec
                    .addMethod(MethodSpec.methodBuilder("build")
                            .addModifiers(Modifier.PUBLIC)
                            .returns(builderType)
                            .addCode(builderBuildCode.build())
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
                .addAnnotations(collectAnnotations(element, null))
                .addTypeVariables(classVariables)
                .addSuperinterfaces(collectInterfaces(element));
    }

    private void buildBuilderClassSpec() {
        if (pojo.builder()) {

            builderClassName = className.nestedClass("Builder");
            builderSpec = TypeSpec.classBuilder(builderClassName.simpleName())
                    .addOriginatingElement(element)
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addTypeVariables(classVariables);
            builderType = classVariables.isEmpty() ? className :
                    ParameterizedTypeName.get(className, element.getTypeParameters().stream()
                            .map($ -> TypeName.get($.asType()))
                            .toArray(TypeName[]::new));
            builderBuildCode = CodeBlock.builder()
                    .add("$1T instance = new $1T();\n", builderType);
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
                classSpec.addType(new ProcessClassTask(this.elements, asType(member)).call().typeSpec.build());

            } else {
                throw new IllegalArgumentException("unsupported " + member.getKind() + ": " + member);
            }
        }

        Stream.concat(Stream.of(element.getSuperclass()), element.getInterfaces().stream())
                .filter($ -> $.getKind() == TypeKind.DECLARED)
                .map(MoreTypes::asTypeElement)
                .filter($ -> $.getAnnotation(POJO.class) != null)
                .map(ClassName::get)
                .map($ -> resolve(elements, $))
                .forEachOrdered(classSpec::superclass);
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

            builderBuildCode.add("instance.$1N = $1N;\n", name);
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
        List<TypeName> toRemove = Arrays.asList(TypeName.get(POJO.class), TypeName.get(Generated.class));

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
    private Stream<AnnotationSpec> expandExtraAnnotations(Element element, AnnotationMirror mirror, ExtraAnnotation.ApplyOn applyOn) {
        if (isTypeOf(ExtraAnnotations.class, mirror.getAnnotationType())) {
            ExtraAnnotations annotations = element.getAnnotation(ExtraAnnotations.class);

            return Stream.of(annotations.value())
                    .flatMap($ -> buildExtraAnnotationSpec($, applyOn));

        } else if (isTypeOf(ExtraAnnotation.class, mirror.getAnnotationType())) {
            ExtraAnnotation annotation = element.getAnnotation(ExtraAnnotation.class);

            return buildExtraAnnotationSpec(annotation, applyOn);
        }
        return Stream.of(AnnotationSpec.get(mirror));
    }

    private Stream<AnnotationSpec> buildExtraAnnotationSpec(ExtraAnnotation annotation, ExtraAnnotation.ApplyOn applyOn) {
        if (applyOn == null || Arrays.asList(annotation.applyOn()).contains(applyOn)) {
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
