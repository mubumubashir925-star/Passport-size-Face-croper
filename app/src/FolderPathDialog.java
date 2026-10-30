import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.io.File;

/** Explorer-style directory chooser with editable/pasteable folder path. */
public final class FolderPathDialog {
    private FolderPathDialog() {}
    public static File choose(Component parent, String title, File initial) {
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
    private static File parse(String raw) {
        String s = raw.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\""))
            s = s.substring(1, s.length() - 1);
        return new File(s);
    }
}
