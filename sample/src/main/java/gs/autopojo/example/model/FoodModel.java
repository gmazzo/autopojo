package gs.autopojo.example.model;

import java.util.List;

import gs.autopojo.POJO;

@POJO("Food")
public interface FoodModel {

    String name();

    List<String> tastes();

    float quality();

}
