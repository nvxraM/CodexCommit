package dev.codexcommit.ide;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.scale.JBUIScale;
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
    static final int FRAME_DELAY_MS = 40;
    static final int SETTLE_DURATION_MS = 720;
    private static final long SAMPLE_NANOS = 1_000_000L;
    private static final double RESUME_SECONDS = .24;
    enum State { IDLE, BUSY, SETTLING }
    private final CubeMotion.Renderer renderer = new CubeMotion.Renderer();
    private final BooleanSupplier busy;
    private final BooleanSupplier motionEnabled;
    private final LongSupplier nanoTime;
    private final Set<Component> targets = Collections.newSetFromMap(new WeakHashMap<>());
    private final Timer timer;
    private volatile boolean disposed;
    private State state = State.IDLE;
    private long phaseStartedNanos;
    private CubeMotion.Pose resumePose;
    private CubeMotion.Pose settleStart;
    private CubeMotion.Pose settleControl;
    private Disposable policyRegistration;

    static ToolbarAnimation create(BooleanSupplier busy) {
        ToolbarAnimation animation = new ToolbarAnimation(busy, ToolbarAnimation::motionAllowed, System::nanoTime);
        animation.attachScreenReaderPolicyListener();
        return animation;
    }

    ToolbarAnimation(BooleanSupplier busy, BooleanSupplier motionEnabled, LongSupplier nanoTime) {
        this.busy = Objects.requireNonNull(busy);
        this.motionEnabled = Objects.requireNonNull(motionEnabled);
        this.nanoTime = Objects.requireNonNull(nanoTime);
        timer = new Timer(FRAME_DELAY_MS, event -> refresh());
        timer.setCoalesce(true);
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

    @Override public int getIconWidth() { return JBUIScale.scale(16); }
    @Override public int getIconHeight() { return JBUIScale.scale(16); }

    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
        if (SwingUtilities.isEventDispatchThread()) {
            if (!disposed && component != null && component.isShowing()) targets.add(component);
            refreshOnEdt(false);
        }
        CubeMotion.Pose current = SwingUtilities.isEventDispatchThread() ? pose(nanoTime.getAsLong()) : CubeMotion.home();
        renderer.paint(graphics, x, y, getIconWidth(), getIconHeight(), current);
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
        boolean available = !disposed && !targets.isEmpty() && motionEnabled.getAsBoolean();
        long now = nanoTime.getAsLong();
        if (!available) {
            home();
        } else if (busy.getAsBoolean()) {
            if (state != State.BUSY) {
                CubeMotion.Pose from = state == State.SETTLING ? pose(now) : null;
                state = State.BUSY;
                phaseStartedNanos = now;
                resumePose = from;
                settleStart = null;
                settleControl = null;
            }
        } else if (state == State.BUSY) {
            settleStart = pose(now);
            settleControl = CubeMotion.returnControl(settleStart, pose(now + SAMPLE_NANOS), .001);
            state = State.SETTLING;
            phaseStartedNanos = now;
            resumePose = null;
        } else if (state == State.SETTLING && elapsed(now) >= CubeMotion.RETURN_SECONDS) {
            home();
        }
        if (state == State.IDLE) timer.stop(); else if (!timer.isRunning()) timer.start();
        if (repaint) for (Component component : targets) component.repaint();
        if (disposed) targets.clear();
    }

    private double elapsed(long now) { return Math.max(0L, now - phaseStartedNanos) / 1_000_000_000.0; }

    /** Time is monotonic and independent of repaint speed. The finish tail never delays a draft. */
    CubeMotion.Pose pose(long nowNanos) {
        if (disposed || state == State.IDLE) return CubeMotion.home();
        double seconds = elapsed(nowNanos);
        if (state == State.SETTLING)
            return CubeMotion.returnPose(settleStart, settleControl, seconds / CubeMotion.RETURN_SECONDS);
        CubeMotion.Pose current = CubeMotion.busyPose(seconds);
        return resumePose != null && seconds < RESUME_SECONDS
                ? CubeMotion.blend(resumePose, current, CubeMotion.smooth(seconds / RESUME_SECONDS)) : current;
    }

    private void home() {
        state = State.IDLE;
        resumePose = null;
        settleStart = null;
        settleControl = null;
    }

    State state() { return state; }

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
