package gs.autopojo.processor.tasks;

import com.google.auto.common.MoreTypes;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Generated;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
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

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
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

    public ProcessClassTask(Elements elements, TypeElement element) {
        this.elements = elements;
        this.element = element;
    }

    @Override
    public GenClass call() {
        ClassName name = getName(element);

        TypeSpec.Builder classSpec = buildClassSpec(name);

        processElements(classSpec,
                collectModifiers(element, Modifier.ABSTRACT, Modifier.STATIC), element);

        return new GenClass(name, element, classSpec);
    }

    private TypeSpec.Builder buildClassSpec(ClassName name) {
        return TypeSpec.classBuilder(name)
                .addOriginatingElement(element)
                .addModifiers(collectModifiers(element, Modifier.ABSTRACT))
                .addAnnotations(collectAnnotations(element))
                .addTypeVariables(element.getTypeParameters().stream()
                        .map(TypeVariableName::get)
                        .map($ -> resolve(elements, $))
                        .collect(Collectors.toList()))
                .addSuperinterfaces(collectInterfaces(element));
    }

    private void processElements(TypeSpec.Builder classSpec, Modifier[] modifiers, TypeElement element) {
        for (Element member : element.getEnclosedElements()) {
            String name = member.getSimpleName().toString();

            switch (member.getKind()) {
                case METHOD:
                    ExecutableElement method = (ExecutableElement) member;
                    if (method.getParameters().isEmpty()) {
                        addField(classSpec, member, name, modifiers, method.getReturnType());
                        continue;
                    }
                    break;

                case FIELD:
                    if (member.getModifiers().contains(Modifier.STATIC)) {
                        addConstant(classSpec, asVariable(member));
                        continue;
                    }
                    break;

                case ENUM:
                    addEnum(classSpec, asType(member));
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

    private void addField(TypeSpec.Builder classSpec, Element member, String name, Modifier[] modifiers, TypeMirror type) {
        final String methodSuffix = name.substring(0, 1).toUpperCase() + name.substring(1);

        TypeName typeName = resolve(elements, TypeName.get(type));

        classSpec
                .addField(FieldSpec.builder(typeName, name, Modifier.PRIVATE)
                        .addAnnotations(collectAnnotations(member))
                        .build())
                .addMethod(MethodSpec.methodBuilder("get" + methodSuffix)
                        .addModifiers(modifiers)
                        .returns(typeName)
                        .addCode("return $N;\n", name)
                        .build())
                .addMethod(MethodSpec.methodBuilder("set" + methodSuffix)
                        .addModifiers(modifiers)
                        .addParameter(typeName, name)
                        .addCode("this.$1N = $1N;\n", name)
                        .build());
    }

    private void addConstant(TypeSpec.Builder classSpec, VariableElement element) {
        TypeName typeName = resolve(elements, TypeName.get(element.asType()));
        String name = element.getSimpleName().toString();
        Object value = getFieldInitExpression(elements, element);

        classSpec.addField(FieldSpec.builder(typeName, name, collectModifiers(element))
                .initializer("$L", value)
                .build());
    }

    private void addEnum(TypeSpec.Builder classSpec, TypeElement element) {
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

    private List<AnnotationSpec> collectAnnotations(Element element) {
        List<TypeName> toRemove = Arrays.asList(TypeName.get(POJO.class), TypeName.get(Generated.class));

        return element.getAnnotationMirrors().stream()
                .flatMap(this::expandExtraAnnotations)
                .filter($ -> !toRemove.contains($.type))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private Stream<AnnotationSpec> expandExtraAnnotations(AnnotationMirror mirror) {
        if (isTypeOf(ExtraAnnotations.class, mirror.getAnnotationType())) {
            List<AnnotationValue> value = (List<AnnotationValue>) getAnnotationValue(mirror, "value").getValue();

            return value.stream()
                    .map($ -> (AnnotationMirror) $.getValue())
                    .flatMap(this::expandExtraAnnotations);

        } else if (isTypeOf(ExtraAnnotation.class, mirror.getAnnotationType())) {
            String className = (String) getAnnotationValue(mirror, "value").getValue();
            List<AnnotationValue> members = (List<AnnotationValue>) getAnnotationValue(mirror, "members").getValue();

            AnnotationSpec.Builder builder = AnnotationSpec.builder(ClassName.bestGuess(className));
            for (AnnotationValue member : members) {
                AnnotationMirror memberMirror = (AnnotationMirror) member.getValue();
                String name = (String) getAnnotationValue(memberMirror, "name").getValue();
                String format = (String) getAnnotationValue(memberMirror, "format").getValue();
                String value = (String) getAnnotationValue(memberMirror, "value").getValue();

                builder.addMember(name, format, value);
            }
            return Stream.of(builder.build());
        }
        return Stream.of(AnnotationSpec.get(mirror));
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
