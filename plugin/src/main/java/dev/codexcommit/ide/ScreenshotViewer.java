package dev.codexcommit.ide;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/** Displays only the bundled public screenshot. It does not read project or account data. */
public final class ScreenshotViewer {
    private static final Set<JDialog> OPEN = new HashSet<>();

    private ScreenshotViewer() {}

    private static final class Picture {
        private static final BufferedImage IMAGE = load();

        private static BufferedImage load() {
            try (InputStream input = ScreenshotViewer.class.getResourceAsStream("/images/generate-button.png")) {
                if (input == null) throw new IOException("Missing bundled screenshot.");
                return decode(input);
            } catch (IOException error) {
                throw new IllegalStateException("The CodexCommit screenshot is unavailable. Reinstall the plugin.", error);
            }
        }
    }

    public static BufferedImage image() { return Picture.IMAGE; }

    static BufferedImage decode(InputStream input) throws IOException {
        try (var stream = new MemoryCacheImageInputStream(input)) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IOException("Invalid bundled screenshot.");
            var reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 8_000_000)
                    throw new IOException("Invalid bundled screenshot dimensions.");
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }

    public static void show(Component owner) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> show(owner));
            return;
        }
        Window parent = owner instanceof Window window ? window : SwingUtilities.getWindowAncestor(owner);
        JDialog dialog = new JDialog(parent, "CodexCommit — Button", Dialog.ModalityType.DOCUMENT_MODAL);
        dialog.setName("codexcommit-picture-viewer");
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setUndecorated(true);
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(new Color(25, 26, 28));
        JPanel toolbar = new JPanel(new BorderLayout(12, 0));
        toolbar.setOpaque(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        JLabel title = new JLabel("CodexCommit button");
        title.setForeground(new Color(225, 228, 233));
        toolbar.add(title, BorderLayout.WEST);
        JButton close = new JButton("Close (Esc)");
        close.addActionListener(event -> dialog.dispose());
        toolbar.add(close, BorderLayout.EAST);
        content.add(toolbar, BorderLayout.NORTH);
        content.add(new ImagePanel(image()), BorderLayout.CENTER);
        dialog.setContentPane(content);
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-picture");
        dialog.getRootPane().getActionMap().put("close-picture", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { dialog.dispose(); }
        });
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { OPEN.remove(dialog); }
        });
        GraphicsConfiguration configuration = parent == null ? null : parent.getGraphicsConfiguration();
        if (configuration == null) configuration = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        dialog.setBounds(configuration.getBounds());
        OPEN.add(dialog);
        dialog.setVisible(true);
    }

    /** Called by the owning application service on the event dispatch thread. */
    public static void closeAll() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(ScreenshotViewer::closeAll);
            return;
        }
        for (JDialog dialog : Set.copyOf(OPEN)) dialog.dispose();
        OPEN.clear();
    }

    static Rectangle fit(int width, int height, int pictureWidth, int pictureHeight) {
        if (width <= 0 || height <= 0 || pictureWidth <= 0 || pictureHeight <= 0) return new Rectangle();
        double scale = Math.min((double) width / pictureWidth, (double) height / pictureHeight);
        int drawWidth = Math.max(1, Math.min(width, (int) Math.round(pictureWidth * scale)));
        int drawHeight = Math.max(1, Math.min(height, (int) Math.round(pictureHeight * scale)));
        return new Rectangle((width - drawWidth) / 2, (height - drawHeight) / 2, drawWidth, drawHeight);
    }

    static final class ImagePanel extends JPanel {
        private final BufferedImage picture;

        ImagePanel(BufferedImage picture) {
            this.picture = picture;
            setName("codexcommit-full-picture");
            setBackground(new Color(25, 26, 28));
            setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            getAccessibleContext().setAccessibleName("CodexCommit button screenshot");
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Insets insets = getInsets();
            Rectangle bounds = fit(getWidth() - insets.left - insets.right,
                    getHeight() - insets.top - insets.bottom, picture.getWidth(), picture.getHeight());
            Graphics2D copy = (Graphics2D) graphics.create();
            try {
                copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                copy.drawImage(picture, bounds.x + insets.left, bounds.y + insets.top,
                        bounds.width, bounds.height, null);
            } finally { copy.dispose(); }
        }
    }
}
