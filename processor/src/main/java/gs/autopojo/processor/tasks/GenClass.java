package gs.autopojo.processor.tasks;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.TypeElement;

public class GenClass {
    public final ClassName name;
    public final TypeElement element;
    public final TypeSpec.Builder typeSpec;

    GenClass(ClassName name, TypeElement element, TypeSpec.Builder typeSpec) {
        this.name = name;
        this.element = element;
        this.typeSpec = typeSpec;
    }

}
