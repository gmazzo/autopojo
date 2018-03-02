package gs.autopojo.example.model;

import java.util.List;

import gs.autopojo.POJO;

@POJO(value = "Food", builder = true)
public interface FoodModel {

    String name();

    List<String> tastes();

    float quality();

}
