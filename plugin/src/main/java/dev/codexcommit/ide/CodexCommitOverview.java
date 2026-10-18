package dev.codexcommit.ide;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Enhances only the marked CodexCommit description; all image data stays in the plugin. */
public final class CodexCommitOverview implements Disposable {
    private static final String MARKER = "codexcommit-overview";
    private static final String PREVIEW = "codexcommit-button-preview";
    private static final String PLUGIN_ID = "Plugin ID: local.codex.commitmessage";
    private static final int MAX_SCAN = 20_000;
    private static final int MAX_PANES = 512;
    private final Map<JEditorPane, PaneWatch> panes = new WeakHashMap<>();
    private final Map<JLabel, LabelWatch> labels = new WeakHashMap<>();
    private final Set<Component> pending = Collections.newSetFromMap(new IdentityHashMap<>());
    private final AWTEventListener events = this::event;
    private volatile boolean disposed;
    private boolean started;
    private boolean scheduled;
    private boolean changing;

    public CodexCommitOverview() {
        if (!GraphicsEnvironment.isHeadless()) SwingUtilities.invokeLater(this::start);
    }

    /** Normal startup and dynamic installation both use the same lazy application service. */
    public static final class Startup implements AppLifecycleListener, DynamicPluginListener {
        @Override public void appFrameCreated(java.util.List<String> arguments) { initialize(); }
        @Override public void welcomeScreenDisplayed() { initialize(); }
        @Override public void pluginLoaded(IdeaPluginDescriptor plugin) {
            if (plugin.getPluginId().getIdString().equals("local.codex.commitmessage")) initialize();
        }
        private static void initialize() {
            var application = ApplicationManager.getApplication();
            if (application != null && !application.isDisposed() && !GraphicsEnvironment.isHeadless())
                application.getService(CodexCommitOverview.class);
        }
    }

    private void start() {
        if (disposed || started) return;
        started = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(events,
                AWTEvent.HIERARCHY_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK);
        for (Window window : Window.getWindows()) if (window.isDisplayable()) enqueue(window);
    }

    private void event(AWTEvent event) {
        if (disposed || changing) return;
        if (event instanceof ContainerEvent item && item.getID() == ContainerEvent.COMPONENT_ADDED) {
            enqueue(item.getChild());
        } else if (event instanceof HierarchyEvent item
                && (item.getChangeFlags() & (HierarchyEvent.DISPLAYABILITY_CHANGED | HierarchyEvent.SHOWING_CHANGED)) != 0) {
            Component component = item.getComponent();
            if (component instanceof JEditorPane || component instanceof JLabel || component instanceof Window) enqueue(component);
        }
    }

    private void enqueue(Component component) {
        if (disposed || component == null) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> enqueue(component));
            return;
        }
        if (pending.size() < MAX_PANES) pending.add(component);
        if (scheduled) return;
        scheduled = true;
        SwingUtilities.invokeLater(this::scanPending);
    }

    private void scanPending() {
        scheduled = false;
        if (disposed) { pending.clear(); return; }
        ArrayDeque<Component> queue = new ArrayDeque<>(pending);
        pending.clear();
        Set<Component> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (!queue.isEmpty() && seen.size() < MAX_SCAN) {
            Component component = queue.removeFirst();
            if (!seen.add(component)) continue;
            if (component instanceof JEditorPane pane) {
                PaneWatch existing = panes.get(pane);
                if (existing != null) existing.refresh();
                else if (pane.getDocument() instanceof HTMLDocument) watch(pane);
            }
            if (component instanceof JLabel label) {
                LabelWatch existing = labels.get(label);
                if (existing != null) existing.refresh();
                else if (PLUGIN_ID.equals(label.getText())) watch(label);
            }
            if (component instanceof Container container) Collections.addAll(queue, container.getComponents());
        }
    }

    private void watch(JEditorPane pane) {
        if (pane.isDisplayable() && !panes.containsKey(pane) && panes.size() < MAX_PANES) {
            PaneWatch watch = new PaneWatch(pane);
            panes.put(pane, watch);
            watch.observe(pane.getDocument());
            pane.addPropertyChangeListener("document", watch.properties);
            pane.addPropertyChangeListener("editorKit", watch.properties);
            watch.refresh();
        }
    }

    private void watch(JLabel label) {
        if (!labels.containsKey(label)) {
            LabelWatch watch = new LabelWatch(label);
            labels.put(label, watch);
            label.addPropertyChangeListener("text", watch.properties);
            watch.refresh();
        }
    }

    private static boolean ownDocument(Document document) {
        if (!(document instanceof HTMLDocument html)) return false;
        boolean[] matches = {false};
        html.render(() -> {
            Element marker = html.getElement(MARKER);
            Element preview = html.getElement(PREVIEW);
            if (marker == null || preview == null || preview.getAttributes().getAttribute(StyleConstants.NameAttribute) != HTML.Tag.IMG) return;
            for (Element parent = preview.getParentElement(); parent != null; parent = parent.getParentElement()) {
                if (parent == marker) { matches[0] = true; return; }
            }
        });
        return matches[0];
    }

    private final class PaneWatch {
        private final WeakReference<JEditorPane> reference;
        private WeakReference<Document> document = new WeakReference<>(null);
        private HTMLEditorKit original;
        private ResponsiveKit replacement;
        private boolean applying;
        private boolean queued;
        private boolean recognized;
        private final Set<PreviewView> views = Collections.newSetFromMap(new IdentityHashMap<>());
        private final DocumentListener changes = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { later(); }
            @Override public void removeUpdate(DocumentEvent event) { later(); }
            @Override public void changedUpdate(DocumentEvent event) { later(); }
        };
        private final PropertyChangeListener properties;

        PaneWatch(JEditorPane pane) {
            reference = new WeakReference<>(pane);
            properties = event -> { if (!applying) later(); };
        }

        private void observe(Document current) {
            Document previous = document.get();
            if (previous == current) return;
            if (previous != null) previous.removeDocumentListener(changes);
            document = new WeakReference<>(current);
            if (current instanceof HTMLDocument) current.addDocumentListener(changes);
        }

        private void later() {
            if (!SwingUtilities.isEventDispatchThread()) { SwingUtilities.invokeLater(this::later); return; }
            if (disposed || applying || queued) return;
            queued = true;
            SwingUtilities.invokeLater(() -> { queued = false; if (!disposed) refresh(); });
        }

        private void refresh() {
            JEditorPane pane = reference.get();
            if (pane == null) { observe(null); return; }
            if (applying) return;
            observe(pane.getDocument());
            if (disposed || !pane.isDisplayable()) { remove(); return; }
            boolean own = ownDocument(pane.getDocument());
            if (own && !recognized) {
                Window window = SwingUtilities.getWindowAncestor(pane);
                if (window != null) enqueue(window);
            }
            recognized = own;
            if (!pane.isShowing() || !own) { restore(); return; }
            if (replacement != null && pane.getEditorKit() != replacement) {
                clearViews();
                replacement = null;
                original = null;
            }
            if (pane.getEditorKit() instanceof HTMLEditorKit kit && pane.getEditorKit() != replacement) {
                original = kit;
                replacement = new ResponsiveKit(kit, this);
                swap(replacement);
            }
        }

        private void swap(HTMLEditorKit kit) {
            JEditorPane pane = reference.get();
            if (pane == null) return;
            Document current = pane.getDocument();
            applying = true;
            changing = true;
            try {
                pane.setEditorKit(kit);
                pane.setDocument(current);
            } finally {
                changing = false;
                applying = false;
                observe(pane.getDocument());
            }
            pane.revalidate();
            pane.repaint();
        }

        private void clearViews() {
            for (PreviewView view : new ArrayList<>(views)) view.release();
            views.clear();
        }

        private void restore() {
            JEditorPane pane = reference.get();
            if (replacement == null) return;
            clearViews();
            if (pane != null && pane.getEditorKit() == replacement && original != null) swap(original);
            replacement = null;
            original = null;
        }

        private void remove() {
            JEditorPane pane = reference.get();
            restore();
            observe(null);
            if (pane != null) {
                pane.removePropertyChangeListener("document", properties);
                pane.removePropertyChangeListener("editorKit", properties);
                panes.remove(pane);
            }
        }
    }

    private final class ResponsiveKit extends HTMLEditorKit {
        private final HTMLEditorKit delegate;
        private final PaneWatch watch;
        ResponsiveKit(HTMLEditorKit delegate, PaneWatch watch) { this.delegate = delegate; this.watch = watch; }
        @Override public ViewFactory getViewFactory() {
            return element -> {
                Object id = element.getAttributes().getAttribute(HTML.Attribute.ID);
                if (PREVIEW.equals(id) && ownDocument(element.getDocument())) return new PreviewView(element, watch);
                return delegate.getViewFactory().create(element);
            };
        }
        @Override public Document createDefaultDocument() { return delegate.createDefaultDocument(); }
        @Override public javax.swing.text.html.StyleSheet getStyleSheet() { return delegate.getStyleSheet(); }
        @Override public void install(JEditorPane pane) { delegate.install(pane); }
        @Override public void deinstall(JEditorPane pane) { delegate.deinstall(pane); }
        @Override public void read(Reader input, Document document, int position) throws IOException, BadLocationException { delegate.read(input, document, position); }
        @Override public void write(Writer output, Document document, int position, int length) throws IOException, BadLocationException { delegate.write(output, document, position, length); }
    }

    private final class PreviewView extends ComponentView {
        private final PaneWatch watch;
        private JButton button;
        private JViewport viewport;
        private final ActionListener open;
        private final ComponentListener resize = new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) { resized(); }
        };
        PreviewView(Element element, PaneWatch watch) {
            super(element);
            this.watch = watch;
            open = event -> {
                JEditorPane pane = watch.reference.get();
                if (!disposed && pane != null && ownDocument(pane.getDocument())) ScreenshotViewer.show(pane);
            };
            watch.views.add(this);
        }

        private Dimension dimensions() {
            BufferedImage image = ScreenshotViewer.image();
            JEditorPane pane = watch.reference.get();
            int width = pane == null ? 1 : pane.getWidth();
            if (pane != null) {
                JViewport parent = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, pane);
                if (parent != null && parent.getExtentSize().width > 0) width = parent.getExtentSize().width;
                Insets insets = pane.getInsets();
                width -= insets.left + insets.right + 32;
            }
            width = Math.max(1, Math.min(width, image.getWidth()));
            return new Dimension(width, Math.max(1, (int) Math.round((double) width * image.getHeight() / image.getWidth())));
        }

        @Override protected Component createComponent() {
            button = new JButton() {
                @Override public Dimension getPreferredSize() { return dimensions(); }
                @Override public Dimension getMinimumSize() { return new Dimension(1, 1); }
                @Override protected void paintComponent(Graphics graphics) {
                    Graphics2D copy = (Graphics2D) graphics.create();
                    try {
                        copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        copy.drawImage(ScreenshotViewer.image(), 0, 0, getWidth(), getHeight(), null);
                        if (hasFocus()) { copy.setColor(getForeground()); copy.drawRect(1, 1, Math.max(0, getWidth() - 3), Math.max(0, getHeight() - 3)); }
                    } finally { copy.dispose(); }
                }
            };
            button.setBorder(BorderFactory.createEmptyBorder());
            button.setContentAreaFilled(false);
            button.setFocusPainted(false);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.setToolTipText("Open the full-size button image");
            button.getAccessibleContext().setAccessibleName("View the CodexCommit button image");
            attach();
            return button;
        }

        private void attach() {
            watch.views.add(this);
            if (button != null) {
                button.setToolTipText("Open the full-size button image");
                if (!java.util.Arrays.asList(button.getActionListeners()).contains(open)) button.addActionListener(open);
            }
            JEditorPane pane = watch.reference.get();
            if (pane != null) {
                if (!java.util.Arrays.asList(pane.getComponentListeners()).contains(resize)) pane.addComponentListener(resize);
                JViewport current = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, pane);
                if (viewport != current && viewport != null) viewport.removeComponentListener(resize);
                viewport = current;
                if (viewport != null && !java.util.Arrays.asList(viewport.getComponentListeners()).contains(resize)) viewport.addComponentListener(resize);
            }
        }

        @Override public float getPreferredSpan(int axis) { Dimension size = dimensions(); return axis == View.X_AXIS ? size.width : size.height; }
        @Override public float getMinimumSpan(int axis) { return axis == View.X_AXIS ? 1 : getPreferredSpan(axis); }
        @Override public float getMaximumSpan(int axis) { return getPreferredSpan(axis); }
        @Override public int getResizeWeight(int axis) { return axis == View.X_AXIS ? 1 : 0; }
        @Override public void setParent(View parent) {
            super.setParent(parent);
            if (parent == null) release();
            else if (button != null) attach();
        }

        private void resized() {
            if (disposed) return;
            if (button != null) { button.invalidate(); if (button.getParent() != null) button.getParent().invalidate(); }
            preferenceChanged(null, true, true);
            JEditorPane pane = watch.reference.get();
            if (pane != null) { pane.revalidate(); pane.repaint(); }
        }

        private void release() {
            JEditorPane pane = watch.reference.get();
            if (pane != null) pane.removeComponentListener(resize);
            if (viewport != null) viewport.removeComponentListener(resize);
            viewport = null;
            if (button != null) { button.removeActionListener(open); button.setToolTipText(null); }
            watch.views.remove(this);
        }
    }

    private final class LabelWatch {
        private final WeakReference<JLabel> reference;
        private JPanel links;
        private Container parent;
        private final PropertyChangeListener properties;
        LabelWatch(JLabel label) {
            reference = new WeakReference<>(label);
            properties = event -> enqueue(reference.get());
        }
        private void refresh() {
            JLabel label = reference.get();
            if (label == null) { removePanel(); return; }
            if (disposed || !label.isDisplayable()) { remove(); return; }
            if (!PLUGIN_ID.equals(label.getText())) { removePanel(); return; }
            if (links != null || !(label.getParent() instanceof JPanel panel) || !vertical(panel.getLayout())) return;
            parent = panel;
            links = new JPanel();
            links.setOpaque(false);
            links.setLayout(new BoxLayout(links, BoxLayout.Y_AXIS));
            links.add(link("GitHub repository", "https://github.com/nvxraM/CodexCommit"));
            links.add(link("User guide", "https://github.com/nvxraM/CodexCommit/blob/main/plugin/docs/USAGE.md"));
            links.add(link("MIT license", "https://github.com/nvxraM/CodexCommit/blob/main/LICENSE"));
            links.add(link("Privacy", "https://github.com/nvxraM/nvxraM/blob/main/docs/codexcommit/PRIVACY.md"));
            changing = true;
            try { panel.add(links, panel.getComponentZOrder(label) + 1); }
            finally { changing = false; }
            panel.revalidate();
            panel.repaint();
        }
        private void removePanel() {
            if (links != null && parent != null) {
                parent.remove(links);
                parent.revalidate();
                parent.repaint();
            }
            links = null;
            parent = null;
        }
        private void remove() {
            JLabel label = reference.get();
            removePanel();
            if (label != null) { label.removePropertyChangeListener("text", properties); labels.remove(label); }
        }
    }

    private static boolean vertical(LayoutManager layout) {
        if (layout instanceof BoxLayout box) return box.getAxis() == BoxLayout.Y_AXIS || box.getAxis() == BoxLayout.PAGE_AXIS;
        // SDK 262 uses this vertical LayoutManager for Additional Info. Do not import or call its internal API.
        return layout != null && layout.getClass().getName().equals("com.intellij.ide.plugins.newui.VerticalLayout");
    }

    private static JButton link(String text, String url) {
        JButton button = new JButton("<html><a href=\"\">" + text + "</a></html>");
        button.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        button.setContentAreaFilled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addActionListener(event -> BrowserUtil.browse(url));
        return button;
    }

    @Override public void dispose() {
        disposed = true;
        Runnable cleanup = () -> {
            if (started) Toolkit.getDefaultToolkit().removeAWTEventListener(events);
            pending.clear();
            for (PaneWatch watch : new ArrayList<>(panes.values())) watch.remove();
            for (LabelWatch watch : new ArrayList<>(labels.values())) watch.remove();
            panes.clear();
            labels.clear();
            ScreenshotViewer.closeAll();
        };
        if (SwingUtilities.isEventDispatchThread()) cleanup.run();
        else SwingUtilities.invokeLater(cleanup);
    }
}
