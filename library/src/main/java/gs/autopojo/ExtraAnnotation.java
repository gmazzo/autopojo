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

    ApplyOn[] applyOn() default {ApplyOn.CLASS, ApplyOn.FIELD};

    @interface Member {

        String name() default "value";

        /**
         * @see <a href="https://github.com/square/javapoet#l-for-literals">JavaPoet formats</a>
         */
        String format() default "$L";

        String value();

    }

    enum ApplyOn {

        CLASS, FIELD, GETTER, SETTER

    }

}
