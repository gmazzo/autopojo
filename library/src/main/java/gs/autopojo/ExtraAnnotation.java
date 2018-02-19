package gs.autopojo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Repeatable(ExtraAnnotations.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface ExtraAnnotation {

    String value();

    Member[] members() default {};

    public @interface Member {

        String name();

        String format() default "$L";

        String value();

    }

}
