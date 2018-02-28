package gs.autopojo.processor;

import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;

public final class ElementsUtils {

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
