package plugAndPlay;

import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;

public class DirWatcher implements Runnable {
    private final WatchService watchService;
    private final Path dir;
    private final Consumer<String> newJarCallback;

    public DirWatcher(String dirPath, Consumer<String> newJarCallback) throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        dir = Paths.get(dirPath);
        dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
        this.newJarCallback = newJarCallback;
    }

    @Override
    public void run() {
        try {
            printWatchedDirectoryName();
            WatchKey key;
            while ((key = watchService.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path eventPath = pathEvent.context();

                    if (eventPath.toString().endsWith(".jar")) {
                        System.out.println("Event kind:" + event.kind() + ". File affected: " + eventPath + ".");
                        newJarCallback.accept(dir.resolve(eventPath).toString());
                    }
                }
                key.reset();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void startWatching() {
        Thread watcherThread = new Thread(this);
        watcherThread.start();
    }

    private void printWatchedDirectoryName() {
        String directoryName = dir.toString();
        int res = directoryName.lastIndexOf("/");
        String watchedDirectoryName = (res != -1) ? directoryName.substring(res + 1) : directoryName;
        System.out.println("Watching directory " + watchedDirectoryName + "...");
    }
}