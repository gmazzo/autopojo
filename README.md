# gs.autopojo
A Plain-Old-Java-Object annotation processing tool that generates code from a describing model interface

Example
-------

Say you have:

```java
@POJO
interface SomeClassPOJO {
  
  String propA();
  
  List<Integer> propB();

}
```

AutoPOJO will generate:

```java
import java.lang.Integer;
import java.lang.String;
import java.util.List;
import javax.annotation.Generated;

@Generated("gs.autopojo.processor.POJOProcessor")
class SomeClass {
  private String propA;

  private List<Integer> propB;

  String getPropA() {
    return propA;
  }

  void setPropA(String propA) {
    this.propA = propA;
  }

  List<Integer> getPropB() {
    return propB;
  }

  void setPropB(List<Integer> propB) {
    this.propB = propB;
  }

  public static class Builder {
    private String propA;

    private List<Integer> propB;

    public String propA() {
      return propA;
    }

    public Builder propA(String propA) {
      this.propA = propA;
      return this;
    }

    public List<Integer> propB() {
      return propB;
    }

    public Builder propB(List<Integer> propB) {
      this.propB = propB;
      return this;
    }

    public SomeClass build() {
      SomeClass instance = new SomeClass();
      instance.propA = propA;
      instance.propB = propB;
      return instance;
    }
  }
}
```
