package plugAndPlay;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DirWatcherTest {

    @Test
    void testPathCreation() {
        String directoryPath = "/home/roi-sasson/java/jar_exercise";
        Path dir = Paths.get(directoryPath);
        assertNotNull(dir, "Path object should not be null");
        System.out.println("Path created for: " + dir);
    }
}