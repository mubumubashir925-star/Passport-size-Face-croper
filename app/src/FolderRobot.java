import java.io.File;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Java 8 folder watcher. Only queues stable image files, never deletes originals. */
public final class FolderRobot implements Runnable {
    private final Path input;
    private final Consumer<File> onImage;
    private volatile boolean running = true;
    public FolderRobot(File folder, Consumer<File> callback) {
        if (!folder.isDirectory()) throw new IllegalArgumentException("Folder not found");
        input = folder.toPath();
        onImage = callback;
    }
    public void stop() { running = false; }
    public void run() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            input.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY);
            while (running) {
                WatchKey key = watcher.poll(1, TimeUnit.SECONDS);
                if (key == null) continue;
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                    Path relative = (Path)event.context();
                    File file = input.resolve(relative).toFile();
                    if (!file.isFile() || !file.getName().matches("(?i).*\\.(jpg|jpeg|png|bmp)")) continue;
                    long length = file.length();
                    try { Thread.sleep(500); } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt(); running = false; break;
                    }
                    if (file.isFile() && length > 0 && file.length() == length) onImage.accept(file);
                }
                if (!key.reset()) break;
            }
        } catch (Exception ex) {
            System.err.println("Folder Robot stopped: " + ex.getMessage());
        }
    }
}
