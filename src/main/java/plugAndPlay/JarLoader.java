/*****************************************
 *   Author:     Roi Sasson              *
 *   Date:       14.04.2025              *
 *   Approver:   Tal Hacham              *
 *****************************************/
package plugAndPlay;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarLoader {
    private final String interfaceName;

    public static final String CLASS_FILE_ENDING = ".class";

    public JarLoader(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public List<Class<?>> loadClass(String jarPath) {
        List<Class<?>> classList = new ArrayList<>();
        File file = new File(jarPath);

        try (JarFile jarFile = new JarFile(jarPath);
             URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{file.toURI().toURL()})) {
            Class<?> interfaceClass = null;
            try {
                interfaceClass = Class.forName(interfaceName, true, classLoader);
            } catch (ClassNotFoundException e) {
                System.err.println("interface not found: " + interfaceName);
            }

            Iterator<JarEntry> entryIterator = jarFile.entries().asIterator();
            while (entryIterator.hasNext()) {
                JarEntry jarEntry = entryIterator.next();
                String entryName = jarEntry.getName();

                if (!jarEntry.isDirectory() && entryName.endsWith(CLASS_FILE_ENDING)) {
                    try {
                        String className = getClassNameFromEntry(entryName);
                        Class<?> loadedClass = classLoader.loadClass(className);

                        if (isImplementingClass(loadedClass, interfaceClass)) {
                            classList.add(loadedClass);
                        }
                    } catch (ClassNotFoundException e) {
                        System.err.println("Could not load class: " + entryName);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return classList;
    }

    private String getClassNameFromEntry(String entryName) {
        return entryName.replace('/', '.').replace(CLASS_FILE_ENDING, "");
    }

    private boolean isImplementingClass(Class<?> candidate, Class<?> interfaceClass) {
        return interfaceClass != null
                && interfaceClass.isAssignableFrom(candidate)
                && !interfaceClass.equals(candidate)
                && !candidate.isInterface();
    }
}