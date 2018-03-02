package gs.autopojo.example.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.inject.Singleton;

import gs.autopojo.ExtraAnnotation;
import gs.autopojo.POJO;

@POJO
@Singleton
@ExtraAnnotation(value = "java.lang.SuppressWarnings", members = @ExtraAnnotation.Member(format = "$S", value = "something"))
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@interface MyPOJO {
}
