package gs.autopojo.example.model;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class FoodTest {
    private Food food;

    @Before
    public void setup() {
        food = new Food();
        food.setName("aName");
        food.setQuality(1.4f);
        food.setTastes(Arrays.asList("yummy", "good"));
    }

    @Test
    public void test() {
        assertEquals("aName", food.getName());
        assertEquals(1.4f, food.getQuality(), .001f);
        assertEquals(Arrays.asList("yummy", "good"), food.getTastes());
    }

}
