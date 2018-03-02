package gs.autopojo.processor.tasks;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;

import gs.autopojo.POJO;

import static com.google.auto.common.MoreTypes.isTypeOf;

final class POJOHelper {

    public static POJO getPOJO(Elements elements, Element element) {
        Supplier<Stream<Element>> annotations = () -> elements.getAllAnnotationMirrors(element).stream()
                .map(AnnotationMirror::getAnnotationType)
                .map(DeclaredType::asElement);

        POJO pojo = Stream.concat(Stream.of(element), annotations.get())
                .map($ -> $.getAnnotation(POJO.class))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (pojo != null) {
            Boolean builderOverride = annotations.get()
                    .flatMap($ -> $.getAnnotationMirrors().stream())
                    .filter($ -> isTypeOf(POJO.class, $.getAnnotationType()))
                    .flatMap($ -> $.getElementValues().entrySet().stream())
                    .filter($ -> $.getKey().getSimpleName().contentEquals("builder"))
                    .map($ -> (Boolean) $.getValue().getValue())
                    .findFirst()
                    .orElse(null);

            if (builderOverride != null) {
                pojo = new POJOImpl(pojo.value(), builderOverride);
            }
        }
        return pojo;
    }

    private POJOHelper() {
    }

}
