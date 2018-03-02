# autopojo
A Plain-Old-Java-Object annotation processing tool that generates code from a describing model interface

## How it works
This project is an **annotation processor** that takes any **@POJO interface** (classes are not allowed)
describing its properties with simple *no-args* methods, and it generate concrete classes
with private **fields**, and public **getter**s and **setter**s. Optionally it can also generate a **builder**.

Like *[Dagger](https://github.com/google/dagger)'s modules*, those interfaces should not be used for anything else than code generation. 
You should relay in some *ProGuard* solution to strip them out from your final code.

## Import
On your `build.gradle` add:
```groovy
dependencies {
    annotationProcessor 'com.github.gmazzo.autopojo:autopojo-processor:0.1'

    implementation 'com.github.gmazzo.autopojo:autopojo-annotations:0.1'
}
```
[![Download](https://api.bintray.com/packages/gmazzo/maven/autopojo/images/download.svg) ](https://bintray.com/gmazzo/maven/autopojo/_latestVersion)

## Usage
Given an **interface** like:

```java
@POJO
public interface PersonPOJO {

    int id();

    String name();

}
```

AutoPOJO will generate:

```java
import java.lang.String;
import javax.annotation.Generated;

@Generated("gs.autopojo.processor.POJOProcessor")
public class Person {
  private int id;

  private String name;

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public static class Builder {
    private int id;

    private String name;

    public int id() {
      return id;
    }

    public Builder id(int id) {
      this.id = id;
      return this;
    }

    public String name() {
      return name;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    protected void fillInstance(Person instance) {
      instance.id = id;
      instance.name = name;
    }

    public Person build() {
      Person instance = new Person();
      fillInstance(instance);
      return instance;
    }
  }
}
```
