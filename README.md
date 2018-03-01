# gs.autopojo
A Plain-Old-Java-Object annotation processing tool that generates code from a describing model interface

Example
-------

Say you have:

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
