package gs.autopojo.processor.tasks;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.stream.Stream;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;

import static com.google.auto.common.MoreTypes.isTypeOf;

final class ElementsHelper {

    public static boolean targetsAnnotationOnly(AnnotationMirror mirror) {
        Target target = mirror.getAnnotationType().asElement().getAnnotation(Target.class);
        return target != null && Arrays.equals(target.value(), new ElementType[]{ElementType.ANNOTATION_TYPE});
    }

    public static Stream<AnnotationMirror> getIndirectAnnotations(Elements elements, AnnotationMirror mirror) {
        return Stream.concat(
                Stream.of(mirror),
                elements.getAllAnnotationMirrors(mirror.getAnnotationType().asElement()).stream());
    }

    public static boolean isOrHasAnnotation(Elements elements, AnnotationMirror mirror, Class<? extends Annotation> annotation) {
        return getIndirectAnnotations(elements, mirror)
                .anyMatch($ -> isTypeOf(annotation, $.getAnnotationType()));
    }

    @SuppressWarnings("unchecked")
    public static <A extends Annotation> A coerceAnnotation(AnnotationMirror mirror, Class<A> annotationClass) {
        // hacky part
        try {
            Class<?> makerClass = Class.forName("com.sun.tools.javac.model.AnnotationProxyMaker");
            return (A) makerClass.getMethod("generateAnnotation", mirror.getClass(), Class.class)
                    .invoke(null, mirror, annotationClass);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getFieldInitExpression(Elements elements, VariableElement field) {
        Object value = field.getConstantValue();
        if (value != null) {
            return elements.getConstantExpression(value);
        }

        // hacky part
        try {
            Object var = elements.getClass()
                    .getMethod("getTree", Element.class)
                    .invoke(elements, field);
            value = var.getClass()
                    .getMethod("getInitializer")
                    .invoke(var);

            if (value != null) {
                return value.toString();
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }

    private ElementsHelper() {
    }

}
