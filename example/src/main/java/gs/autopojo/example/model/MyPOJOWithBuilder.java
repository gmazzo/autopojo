package gs.autopojo.example.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import gs.autopojo.POJO;

@Inherited
@POJO(builder = true)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@interface MyPOJOWithBuilder {
}
