import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Explorer-style directory chooser with editable/pasteable folder path. */
public final class FolderPathDialog {
    private FolderPathDialog() {}
    public static File choose(Component parent, String title, File initial) {
        File nativeResult = nativeFolder(parent, title);
        if (nativeResult != null) return nativeResult.isDirectory() ? nativeResult : null;
        JFileChooser chooser = new JFileChooser(FileSystemView.getFileSystemView());
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setApproveButtonText("Select Folder");
        chooser.setPreferredSize(new Dimension(900, 570));
        if (initial != null && initial.isDirectory()) chooser.setCurrentDirectory(initial);

        JPanel accessory = new JPanel(new BorderLayout(4, 6));
        accessory.setPreferredSize(new Dimension(260, 90));
        accessory.add(new JLabel("Folder path (paste here):"), BorderLayout.NORTH);
        JTextField path = new JTextField();
        accessory.add(path, BorderLayout.CENTER);
        JButton go = new JButton("Go to pasted path");
        go.addActionListener(e -> {
            File folder = parse(path.getText());
            if (folder.isDirectory()) {
                chooser.setCurrentDirectory(folder);
                chooser.setSelectedFile(folder);
            } else {
                JOptionPane.showMessageDialog(chooser, "Folder not found:\n" + path.getText());
            }
        });
        accessory.add(go, BorderLayout.SOUTH);
        chooser.setAccessory(accessory);
        chooser.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY, e -> {
            File current = chooser.getCurrentDirectory();
            if (current != null) path.setText(current.getAbsolutePath());
        });
        if (chooser.getCurrentDirectory() != null)
            path.setText(chooser.getCurrentDirectory().getAbsolutePath());

        while (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            File typed = parse(path.getText());
            File selected = chooser.getSelectedFile();
            // Explicitly pasted path takes precedence when it differs from browsing location.
            File chosen = typed.isDirectory() && (selected == null ||
                !typed.equals(chooser.getCurrentDirectory())) ? typed : selected;
            if (chosen != null && chosen.isDirectory()) return chosen;
            JOptionPane.showMessageDialog(parent, "Please select an existing folder.",
                "Invalid folder", JOptionPane.WARNING_MESSAGE);
        }
        return null;
    }
    private static File nativeFolder(Component parent, String title) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) return null;
        File script = new File("native-folder-picker.ps1");
        if (!script.isFile()) return null;
        try {
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-STA",
                "-ExecutionPolicy", "Bypass", "-File", script.getAbsolutePath())
                .redirectErrorStream(true).start();
            String selected = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null)
                    if (new File(line.trim()).isDirectory()) selected = line.trim();
            }
            if (process.waitFor() == 0) return selected == null ? new File("") : new File(selected);
        } catch (Exception ignored) {
            // Fall back to the Java directory chooser when PowerShell is unavailable.
        }
        return null;
    }

    private static File parse(String raw) {
        String s = raw.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\""))
            s = s.substring(1, s.length() - 1);
        return new File(s);
    }
}
