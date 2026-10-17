package dev.codexcommit.ide;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.accessibility.ScreenReader;

import javax.swing.Icon;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Graphics;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** A small vector animation owned by one project. It never reads project or account content. */
final class ToolbarAnimation implements Icon, Disposable {
    static final int FRAME_DELAY_MS = 80;
    private static final long FRAME_DELAY_NANOS = FRAME_DELAY_MS * 1_000_000L;
    private final Icon idle;
    private final Icon[] frames;
    private final BooleanSupplier busy;
    private final BooleanSupplier motionEnabled;
    private final LongSupplier nanoTime;
    private final Set<Component> targets = Collections.newSetFromMap(new WeakHashMap<>());
    private final Timer timer;
    private volatile boolean disposed;
    private long startedNanos;
    private Disposable policyRegistration;

    static ToolbarAnimation create(BooleanSupplier busy) {
        ToolbarAnimation animation = new ToolbarAnimation(Assets.IDLE, Assets.FRAMES, busy,
                ToolbarAnimation::motionAllowed, System::nanoTime);
        animation.attachScreenReaderPolicyListener();
        return animation;
    }

    ToolbarAnimation(Icon idle, Icon[] frames, BooleanSupplier busy,
                     BooleanSupplier motionEnabled, LongSupplier nanoTime) {
        this.idle = Objects.requireNonNull(idle);
        this.frames = frames.clone();
        for (Icon frame : this.frames) Objects.requireNonNull(frame);
        this.busy = Objects.requireNonNull(busy);
        this.motionEnabled = Objects.requireNonNull(motionEnabled);
        this.nanoTime = Objects.requireNonNull(nanoTime);
        timer = new Timer(FRAME_DELAY_MS, event -> refresh());
        timer.setCoalesce(true);
    }

    private static final class Assets {
        static final Icon IDLE = IconLoader.getIcon("/icons/codexcommit.svg", ToolbarAnimation.class);
        static final Icon[] FRAMES = loadFrames();

        private static Icon[] loadFrames() {
            Icon[] result = new Icon[40];
            for (int index = 0; index < result.length; index++) {
                String path = String.format(java.util.Locale.ROOT, "/icons/loading/frame-%03d.svg", index);
                if (ToolbarAnimation.class.getResource(path) == null) return new Icon[0];
                result[index] = IconLoader.getIcon(path, ToolbarAnimation.class);
            }
            return result;
        }
    }

    private static boolean motionAllowed() {
        return !PowerSaveMode.isEnabled() && !ScreenReader.isActive();
    }

    /** Resume a stopped busy icon when the screen reader policy changes, without an idle timer. */
    synchronized void attachScreenReaderPolicyListener() {
        if (disposed || policyRegistration != null) return;
        Disposable registration = Disposer.newDisposable("CodexCommit toolbar motion policy");
        try {
            ScreenReader.addPropertyChangeListener(ScreenReader.SCREEN_READER_ACTIVE_PROPERTY,
                    registration, event -> { if (!disposed) refresh(); });
            policyRegistration = registration;
        } catch (RuntimeException | Error failure) {
            Disposer.dispose(registration);
            throw failure;
        }
    }

    @Override public int getIconWidth() { return idle.getIconWidth(); }
    @Override public int getIconHeight() { return idle.getIconHeight(); }

    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
        if (SwingUtilities.isEventDispatchThread()) {
            if (!disposed && component != null && component.isShowing()) targets.add(component);
            refreshOnEdt(false);
        }
        int index = SwingUtilities.isEventDispatchThread() ? frameIndex(nanoTime.getAsLong()) : -1;
        (index < 0 ? idle : frames[index]).paintIcon(component, graphics, x, y);
    }

    /** Synchronise the timer after a job starts, ends, or changes its motion preference. */
    void refresh() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }
        refreshOnEdt(true);
    }

    private void refreshOnEdt(boolean repaint) {
        targets.removeIf(component -> !component.isShowing());
        boolean animate = !disposed && frames.length > 0 && busy.getAsBoolean()
                && motionEnabled.getAsBoolean() && !targets.isEmpty();
        if (animate && !timer.isRunning()) {
            startedNanos = nanoTime.getAsLong();
            timer.start();
        } else if (!animate && timer.isRunning()) {
            timer.stop();
        }
        if (repaint) for (Component component : targets) component.repaint();
        if (disposed) targets.clear();
    }

    /** A negative result selects the static logo. Time is monotonic and independent of repaint speed. */
    int frameIndex(long nowNanos) {
        if (disposed || !timer.isRunning()) return -1;
        long elapsed = Math.max(0L, nowNanos - startedNanos);
        return (int) ((elapsed / FRAME_DELAY_NANOS) % frames.length);
    }

    boolean isRunning() { return timer.isRunning(); }

    @Override public void dispose() {
        Disposable registration;
        synchronized (this) {
            if (disposed) return;
            disposed = true;
            registration = policyRegistration;
            policyRegistration = null;
        }
        if (registration != null) Disposer.dispose(registration);
        refresh();
    }
}
