package plugAndPlay;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ClassLoaderTest {
    @Test
    void testLoadAndInvokeClass() {
        String jarPath = "/home/roi-sasson/java/jar_exercise/animals.jar";
        String interfaceName = "Animal";

        JarLoader jarLoader = new JarLoader(interfaceName);
        List<Class<?>> loadedClasses;
        loadedClasses = jarLoader.loadClass(jarPath);

        assertNotNull(loadedClasses, "Loaded class list should not be null");
        assertFalse(loadedClasses.isEmpty(), "Loaded class list should not be empty");

        for (Class<?> clazz : loadedClasses) {
            try {
                Constructor<?> constructor = clazz.getDeclaredConstructor(String.class);
                Object obj = constructor.newInstance("Label");

                // check that the class name is not null
                assertNotNull(clazz.getName(), "Class name should not be null");
                System.out.println("Loaded class: " + clazz.getName());

                Method makeSoundMethod = clazz.getMethod("makeSound");
                assertNotNull(makeSoundMethod, "makeSound method should exist");
                makeSoundMethod.invoke(obj);

            } catch (Exception e) {
                fail("Exception during class instantiation or method invocation: " + e.getMessage() + " for class: " + clazz.getName());
            }
        }
    }
}
