package gs.autopojo.example.model;

import java.util.List;

import gs.autopojo.POJO;

@POJO
public interface EmployeePOJO extends PersonPOJO {

    String area();

    List<EmployeePOJO> subordinates();

}
