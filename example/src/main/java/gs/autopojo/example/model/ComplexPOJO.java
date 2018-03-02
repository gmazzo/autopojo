package gs.autopojo.example.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.inject.Singleton;

import gs.autopojo.ExtraAnnotation;
import gs.autopojo.POJO;

@POJO("ComplexEntity")
@Singleton
public interface ComplexPOJO<T extends ComplexPOJO.Models.Item> extends Cloneable {

    String CONSTANT1 = "aConstant";

    int CONSTANT2 = 4;

    Pattern CONSTANT3 = Pattern.compile("a*\\D{2}b+");

    long id();

    String name();

    List<Pair<T, ? super Models.Item>> values();

    @ExtraAnnotation(value = "java.lang.Deprecated", applyOn = {ExtraAnnotation.ApplyOn.GETTER, ExtraAnnotation.ApplyOn.SETTER})
    @ExtraAnnotation("javax.inject.Singleton")
    Status status();

    @MyPOJO
    @ExtraAnnotation(value = "javax.inject.Named",
            members = @ExtraAnnotation.Member(format = "$S", value = "aPair"))
    interface Pair<A, B> {

        A a();

        B b();

    }

    @POJO(builder = false)
    interface Models {

        @POJO
        interface Item<K extends CharSequence & Serializable, V> extends FoodModel {

            Map.Entry<K, V> id();

        }

    }

    enum Status {

        PENDING, DONE

    }

}
