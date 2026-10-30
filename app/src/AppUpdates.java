import javax.swing.*;
import java.awt.*;
import java.awt.Desktop;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

/** Optional update checks; cropping never depends on internet access. */
public final class AppUpdates {
    public static final String VERSION = "0.1.0";
    private static final String API = "https://api.github.com/repos/mubumubashir925-star/Passport-size-Face-croper/releases/latest";
    private static final String RELEASES = "https://github.com/mubumubashir925-star/Passport-size-Face-croper/releases";
    private AppUpdates() {}

    public static void check(Component parent) {
        JDialog waiting = new JDialog(SwingUtilities.getWindowAncestor(parent), "Checking for updates",
            Dialog.ModalityType.MODELESS);
        waiting.add(new JLabel("  Checking GitHub releases...  "));
        waiting.pack();
        waiting.setLocationRelativeTo(parent);
        SwingWorker<String[], Void> worker = new SwingWorker<String[], Void>() {
            protected String[] doInBackground() throws Exception {
                HttpURLConnection connection = (HttpURLConnection)new URL(API).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "FaceCropPro-" + VERSION);
                try {
                    if (connection.getResponseCode() == 404)
                        return new String[]{"NONE", "", ""};
                    if (connection.getResponseCode() != 200)
                        throw new IOException("GitHub HTTP " + connection.getResponseCode());
                    try (InputStream stream = connection.getInputStream()) {
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        byte[] bytes = new byte[4096];
                        int n;
                        while ((n = stream.read(bytes)) != -1) {
                            if (buffer.size() + n > 1024 * 1024) throw new IOException("Response too large");
                            buffer.write(bytes, 0, n);
                        }
                        String json = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                        String tag = field(json, "tag_name");
                        String url = field(json, "html_url");
                        if (tag == null || url == null || !url.startsWith(RELEASES + "/tag/"))
                            throw new IOException("Unexpected release metadata");
                        return new String[]{"FOUND", tag, url};
                    }
                } finally { connection.disconnect(); }
            }
            protected void done() {
                waiting.dispose();
                try {
                    String[] data = get();
                    if ("NONE".equals(data[0])) {
                        JOptionPane.showMessageDialog(parent,
                            "No published GitHub Release yet.\nCurrent version: " + VERSION +
                            "\nGitHub Actions build artifacts are not public app releases.",
                            "Face Crop Pro Updates", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    int compare = compareVersions(data[1], VERSION);
                    if (compare <= 0) {
                        JOptionPane.showMessageDialog(parent,
                            "You have the latest published version (" + VERSION + ").",
                            "Face Crop Pro Updates", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    int answer = JOptionPane.showConfirmDialog(parent,
                        "Update available: " + data[1] + "\nInstalled version: " + VERSION +
                        "\nOpen official GitHub release page to download the installer?\n" +
                        "Your current app will not be changed automatically.",
                        "Face Crop Pro Updates", JOptionPane.YES_NO_OPTION);
                    if (answer == JOptionPane.YES_OPTION && Desktop.isDesktopSupported())
                        Desktop.getDesktop().browse(new URI(data[2]));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent,
                        "Could not check for updates.\nInternet/TLS access may be unavailable on Windows 7.\n" +
                        "Cropping continues to work offline.\n" + ex.getMessage(),
                        "Update check unavailable", JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        worker.execute();
        waiting.setVisible(true);
    }
    private static String field(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"\\\\]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }
    private static int compareVersions(String a, String b) {
        String[] x = a.replaceFirst("^[vV]", "").split("\\.");
        String[] y = b.replaceFirst("^[vV]", "").split("\\.");
        for (int i = 0; i < 3; i++) {
            int left = i < x.length ? parse(x[i]) : 0;
            int right = i < y.length ? parse(y[i]) : 0;
            if (left != right) return Integer.compare(left, right);
        }
        return 0;
    }
    private static int parse(String value) {
        Matcher m = Pattern.compile("^(\\d+)").matcher(value);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}
