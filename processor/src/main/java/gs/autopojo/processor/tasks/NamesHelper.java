package gs.autopojo.processor.tasks;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import gs.autopojo.POJO;

import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreElements.getPackage;
import static com.google.auto.common.MoreElements.isType;
import static gs.autopojo.processor.tasks.POJOHelper.getPOJO;

final class NamesHelper {
    private static final String SUFFIX_POJO = "POJO";

    public static ClassName getName(Elements elements, TypeElement element) {
        if (element == null) {
            return null;
        }

        ClassName parent = isType(element.getEnclosingElement()) ?
                getName(elements, asType(element.getEnclosingElement())) : null;

        POJO pojo = getPOJO(elements, element);
        String name = pojo == null ? "" : pojo.value();
        if (name.matches("\\s*")) {
            name = getDefaultName(element, parent == null && pojo != null);
        }

        return parent != null ? parent.nestedClass(name) :
                ClassName.get(getPackage(element).toString(), name);
    }

    private static String getDefaultName(TypeElement element, boolean appendPOJO) {
        String name = element.getSimpleName().toString();

        if (appendPOJO) {
            int len = name.length();
            int sufLen = SUFFIX_POJO.length();
            return len > sufLen && name.endsWith(SUFFIX_POJO) ? name.substring(0, len - sufLen) : name + SUFFIX_POJO;
        }
        return name;
    }

    public static String getQualifiedName(ClassName element) {
        return element == null ? null :
                Stream.concat(Stream.of(element.packageName()), element.simpleNames().stream())
                        .collect(Collectors.joining("."));
    }

    @SuppressWarnings("unchecked")
    public static <T extends TypeName> T resolve(Elements elements, T name) {
        if (name instanceof ClassName) {
            String qualifiedName = getQualifiedName((ClassName) name);
            TypeElement element = elements.getTypeElement(qualifiedName);
            return (T) getName(elements, element);

        } else if (name instanceof TypeVariableName) {
            TypeVariableName tvName = (TypeVariableName) name;

            return (T) TypeVariableName.get(tvName.name, tvName.bounds.stream()
                    .map($ -> resolve(elements, $))
                    .toArray(TypeName[]::new));

        } else if (name instanceof WildcardTypeName) {
            WildcardTypeName wName = (WildcardTypeName) name;

            return (T) (wName.lowerBounds.isEmpty() ?
                    WildcardTypeName.subtypeOf(resolve(elements, wName.upperBounds.get(0))) :
                    WildcardTypeName.supertypeOf(resolve(elements, wName.lowerBounds.get(0))));

        } else if (name instanceof ParameterizedTypeName) {
            ParameterizedTypeName ptName = (ParameterizedTypeName) name;

            return (T) ParameterizedTypeName.get(
                    resolve(elements, ptName.rawType),
                    ptName.typeArguments.stream()
                            .map($ -> resolve(elements, $))
                            .toArray(TypeName[]::new));

        } else if (name instanceof ArrayTypeName) {
            ArrayTypeName aName = (ArrayTypeName) name;

            return (T) ArrayTypeName.of(resolve(elements, aName.componentType));
        }
        return name;
    }

}
