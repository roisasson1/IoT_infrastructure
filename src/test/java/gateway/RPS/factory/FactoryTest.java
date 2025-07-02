package gateway.RPS.factory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FactoryTest {
    private Factory<String, String, Integer> stringFactory;
    private Factory<String, Integer[], Integer> IntegerArrFactory;

    @BeforeEach
    void setUp() {
        stringFactory = new Factory<>();
        IntegerArrFactory = new Factory<>();
    }

    @Test
    void addLambdaTest() {
        stringFactory.add("length", String::length);
        Integer result = stringFactory.create("length", "hello");
        assertEquals(5, result);

        Integer[] arr = {4, 1};
        IntegerArrFactory.add("add", s-> s[0] + s[1]);
        Integer result1 = IntegerArrFactory.create("add", arr);
        assertEquals(5, result1);

        IntegerArrFactory.add("multiply", s-> s[0] * s[1]);
        Integer result2 = IntegerArrFactory.create("multiply", arr);
        assertEquals(4, result2);
    }

    @Test
    void addStaticMethodTest1() {
        stringFactory.add("parse", Integer::parseInt);
        Integer result = stringFactory.create("parse", "65");
        assertEquals(65, result);
    }

    @Test
    void addStaticMethodTest2() {
        stringFactory.add("parse", FactoryTest::calcLength);
        Integer result = stringFactory.create("parse", "hello");
        assertEquals(5, result);
    }

    @Test
    void addInstanceMethodTest() {
        StringLength len = new StringLength();
        stringFactory.add("length", len::length);
        Integer result = stringFactory.create("length", "hello");
        assertEquals(5, result);
    }

    private static Integer calcLength(String input) {
        return (input != null) ? input.length() : 0;
    }

    private static class StringLength {
        public Integer length(String input) {
            return input.length();
        }
    }
}
