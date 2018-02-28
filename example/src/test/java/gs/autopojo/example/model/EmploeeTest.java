package gs.autopojo.example.model;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class EmploeeTest {
    private Employee employee;

    @Before
    public void setup() {
        employee = new Employee();
        employee.setId(101);
        employee.setName("aName");
        employee.setArea("anArea");
        employee.setSubordinates(Collections.emptyList());
    }

    @Test
    public void test() {
        assertEquals(101, employee.getId());
        assertEquals("aName", employee.getName());
        assertEquals("anArea", employee.getArea());
        assertEquals(Collections.emptyList(), employee.getSubordinates());
        assertEquals(employee.getClass().getSuperclass(), Person.class);
    }

}
