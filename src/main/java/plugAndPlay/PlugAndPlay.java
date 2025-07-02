package plugAndPlay;

import mediator.Mediator;

import java.io.File;
import java.io.IOException;

public class PlugAndPlay {
    private final JarLoader jarLoader = new JarLoader("gateway.RPS.command.Command");
    private final Mediator mediator;

    // throws if file cannot open or jar file cannot be loaded
    public PlugAndPlay(Mediator mediator, String watchDirectory) throws IOException {
        this.mediator = mediator;
        DirWatcher dirWatcher = new DirWatcher(watchDirectory, this::onNewJarEvent);
        loadExistingJars(watchDirectory);
        dirWatcher.startWatching();
    }

    private void onNewJarEvent(String path) {
        mediator.add(jarLoader.loadClass(path));
    }

    private void loadExistingJars(String watchDirectory) {
        File directory = new File(watchDirectory);
        File[] jarFiles = directory.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
        if (jarFiles != null) {
            for (File jarFile : jarFiles) {
                onNewJarEvent(jarFile.getAbsolutePath());
            }
        }
    }
}
