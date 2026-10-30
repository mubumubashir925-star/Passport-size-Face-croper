import javax.swing.*;
import java.awt.*;
import java.io.File;

/** Paste a folder path or browse. Java 8 / Windows 7 compatible. */
public final class FolderPathDialog {
    private FolderPathDialog() {}
    public static File choose(Component parent, String title, File initial) {
        JTextField path = new JTextField(initial == null ? "" : initial.getAbsolutePath(), 36);
        JButton browse = new JButton("Browse...");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(title);
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (!path.getText().trim().isEmpty()) {
                File current = new File(path.getText().trim());
                chooser.setCurrentDirectory(current.isDirectory() ? current : current.getParentFile());
            }
            if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION)
                path.setText(chooser.getSelectedFile().getAbsolutePath());
        });
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JLabel("Paste folder path (Ctrl+V) or browse:"), BorderLayout.NORTH);
        panel.add(path, BorderLayout.CENTER);
        panel.add(browse, BorderLayout.EAST);
        while (true) {
            if (JOptionPane.showConfirmDialog(parent, panel, title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
                return null;
            String entered = path.getText().trim();
            if (entered.startsWith("\"") && entered.endsWith("\"") && entered.length() > 1)
                entered = entered.substring(1, entered.length() - 1);
            File folder = new File(entered);
            if (entered.isEmpty() || !folder.isDirectory()) {
                JOptionPane.showMessageDialog(parent, "Folder does not exist:\n" + entered,
                    "Invalid folder", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            return folder;
        }
    }
}
