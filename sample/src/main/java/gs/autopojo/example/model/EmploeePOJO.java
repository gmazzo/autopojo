package gs.autopojo.example.model;

import java.util.List;

import gs.autopojo.POJO;

@POJO
public interface EmploeePOJO extends PersonPOJO {

    String area();

    List<EmploeePOJO> subordinates();

}
