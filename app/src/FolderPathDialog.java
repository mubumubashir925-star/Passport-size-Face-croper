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
        if (nativeResult != null && nativeResult.isDirectory()) return nativeResult;
        if (nativeResult != null) return null;
        if (!SwingUtilities.isEventDispatchThread()) {
            final File[] result = new File[1];
            try { SwingUtilities.invokeAndWait(() -> result[0] = javaFolder(parent, title, initial)); }
            catch (Exception ex) { return null; }
            return result[0];
        }
        return javaFolder(parent, title, initial);
    }
    private static File javaFolder(Component parent, String title, File initial) {
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
        if (!script.isFile()) {
            JOptionPane.showMessageDialog(parent, "Windows folder picker helper is missing: " + script.getAbsolutePath(),
                "Folder picker unavailable", JOptionPane.ERROR_MESSAGE);
            return new File("INVALID_FOLDER_PICKER_CANCELLED_DO_NOT_CREATE");
        }
        File resultFile = null;
        try {
            resultFile = File.createTempFile("facecrop-folder-", ".txt");
            if (!resultFile.delete()) throw new java.io.IOException("Cannot prepare picker result file");
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-STA",
                "-ExecutionPolicy", "Bypass", "-File", script.getAbsolutePath(),
                resultFile.getAbsolutePath()).redirectErrorStream(true).start();
            StringBuilder diagnostics = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) diagnostics.append(line).append("\\n");
            }
            int exit = process.waitFor();
            if (exit == 0) {
                if (!resultFile.isFile()) return new File("INVALID_FOLDER_PICKER_CANCELLED_DO_NOT_CREATE");
                byte[] bytes = java.nio.file.Files.readAllBytes(resultFile.toPath());
                String selected = new String(bytes, java.nio.charset.StandardCharsets.UTF_16LE);
                if (!selected.isEmpty() && selected.charAt(0) == 65279) selected = selected.substring(1);
                File folder = new File(selected.trim());
                if (folder.isDirectory()) return folder;
                throw new java.io.IOException("Selected folder not found: " + selected);
            }
            throw new java.io.IOException("PowerShell exited with code " + exit + ": " + diagnostics);
        } catch (Exception error) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parent,
                "Native Windows picker failed: " + error.toString(),
                "Folder picker error", JOptionPane.ERROR_MESSAGE));
            return new File("INVALID_FOLDER_PICKER_CANCELLED_DO_NOT_CREATE");
        } finally {
            if (resultFile != null) resultFile.delete();
        }
    }

    private static File parse(String raw) {
        String s = raw.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\""))
            s = s.substring(1, s.length() - 1);
        return new File(s);
    }
}
