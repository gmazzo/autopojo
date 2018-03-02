package gs.autopojo.processor;

import java.util.Objects;
import java.util.stream.Stream;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;

import gs.autopojo.POJO;

public final class ElementsUtils {

    public static POJO getPOJO(Elements elements, Element element) {
        return Stream.concat(
                Stream.of(element),
                elements.getAllAnnotationMirrors(element).stream()
                        .map(AnnotationMirror::getAnnotationType)
                        .map(DeclaredType::asElement))
                .map($ -> $.getAnnotation(POJO.class))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
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

    private ElementsUtils() {
    }

}
