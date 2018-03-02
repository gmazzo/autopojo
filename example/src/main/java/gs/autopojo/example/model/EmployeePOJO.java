package gs.autopojo.example.model;

import java.util.List;

@MyPOJOWithBuilder
public interface EmployeePOJO extends PersonPOJO {

    String area();

    List<EmployeePOJO> subordinates();

}
