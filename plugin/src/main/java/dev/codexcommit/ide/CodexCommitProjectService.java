package dev.codexcommit.ide;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.commit.CommitWorkflowUi;
import dev.codexcommit.core.CodexExecutableLocator;
import dev.codexcommit.core.CodexRunner;
import dev.codexcommit.core.ContentPolicy;
import dev.codexcommit.core.PromptBuilder;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** One cancellable job per project, owned by the project/plugin lifetime. */
@Service(Service.Level.PROJECT)
public final class CodexCommitProjectService implements Disposable {
    private final Project project;
    private final Backend backend;
    private volatile boolean disposed;
    private volatile Job active;
    private volatile ToolbarAnimation toolbarAnimation;

    private static final class Job {
        final AtomicBoolean cancelled = new AtomicBoolean();
        volatile ProgressIndicator indicator;
        volatile CountDownLatch workerStopped = new CountDownLatch(0);
        volatile Thread worker;
        ChangeSnapshot snapshot;
        Path executable;
        String prompt;
        String model;
        String draft;
        String failure;
        boolean fresh;
    }

    interface Backend {
        String resolveModel(Path executable, BooleanSupplier cancelled) throws Exception;
        String generate(Path executable, String prompt, String model, BooleanSupplier cancelled) throws Exception;
    }

    public CodexCommitProjectService(Project project) {
        this.project = project;
        this.backend = new Backend() {
            private final CodexRunner runner = new CodexRunner();
            @Override public String resolveModel(Path executable, BooleanSupplier cancelled) throws Exception {
                return runner.resolveModel(executable, cancelled);
            }
            @Override public String generate(Path executable, String prompt, String model, BooleanSupplier cancelled) throws Exception {
                return runner.generate(executable, prompt, model, cancelled);
            }
        };
    }

    public boolean isBusy() { return active != null; }

    javax.swing.Icon actionIcon() {
        if (toolbarAnimation == null) toolbarAnimation = ToolbarAnimation.create(this::isBusy);
        if (isBusy() && !toolbarAnimation.isRunning()) toolbarAnimation.refresh();
        return toolbarAnimation;
    }

    public void start(CommitWorkflowUi ui, Document messageDocument) {
        if (disposed || project.isDisposed() || active != null) return;
        Job job = new Job();
        active = job;
        if (toolbarAnimation != null) toolbarAnimation.refresh();
        try { job.snapshot = ChangeSnapshot.capture(project, ui, messageDocument); }
        catch (ChangeSnapshot.SelectionException exception) {
            finish(job);
            notice(exception.getMessage() + " No request was sent.");
            return;
        } catch (Exception ignored) {
            finish(job);
            notice("Cannot read the selected changes. Check that the files and Git repository are available. No request was sent.");
            return;
        }
        task(job, "CodexCommit: prepare selected changes", () -> {
            String patch = job.snapshot.build(() -> cancelled(job));
            if (job.snapshot.workspaceOnly()) {
                job.draft = "Update IDE workspace settings\n\n- Update the saved workspace state.";
                return;
            }
            job.prompt = PromptBuilder.build(patch, job.snapshot.hasWorkspaceSettings());
            job.executable = CodexExecutableLocator.find(CodexCommitSettings.getInstance().getExecutable());
            job.indicator.setIndeterminate(true);
            job.indicator.setText2("Detect the current Codex model");
            job.model = backend.resolveModel(job.executable, () -> cancelled(job));
        }, () -> whenFresh(job, () -> {
            if (job.snapshot.workspaceOnly()) { job.snapshot.apply(job.draft); finish(job); }
            else generate(job);
        }), "Could not prepare the selected changes or detect the Codex model. "
                + "Check native Codex/Git paths and select a model in Codex. "
                + "The combined before/after text must be at most 50 MiB, "
                + "and the patch at most 512 KiB. No request was sent.");
    }

    private void generate(Job job) {
        task(job, "CodexCommit: generate commit message", () -> {
            job.draft = backend.generate(job.executable, job.prompt, job.model, () -> cancelled(job));
            ContentPolicy.validateMessage(job.draft);
        }, () -> whenFresh(job, () -> { job.snapshot.apply(job.draft); finish(job); }), "Codex could not generate a draft. This release requires native Codex CLI " + CodexRunner.SUPPORTED_CLI_VERSIONS + " on Windows or Linux "
                + "and blocks managed/system Codex configuration. Check sign-in, account quota, and connection. "
                + "The CLI may have processed the request before failing. Your commit message was not changed.");
    }

    private void whenFresh(Job job, Runnable next) {
        if (!current(job)) return;
        if (!job.snapshot.isFresh()) { stale(job); return; }
        task(job, "CodexCommit: verify selected files", () -> {
            job.fresh = job.snapshot.contentIsFresh(() -> cancelled(job));
        }, () -> {
            if (!job.fresh || !job.snapshot.isFresh()) { stale(job); return; }
            next.run();
        }, "Could not verify the selected files. Run CodexCommit again. Your commit message was preserved.");
    }

    @FunctionalInterface private interface Work { void run() throws Exception; }

    private void task(Job job, String title, Work work, Runnable completed, String failureMessage) {
        if (!current(job)) return;
        CountDownLatch stopped = new CountDownLatch(1);
        job.workerStopped = stopped;
        new Task.Backgroundable(project, title, true) {
            @Override public void run(@NotNull ProgressIndicator indicator) {
                job.indicator = indicator;
                job.worker = Thread.currentThread();
                indicator.setIndeterminate(true);
                try {
                    if (cancelled(job)) throw new InterruptedException("Cancelled.");
                    work.run();
                } catch (InterruptedException ignored) {
                    job.cancelled.set(true);
                    indicator.cancel();
                } catch (ChangeSnapshot.SelectionException exception) {
                    job.failure = exception.getMessage() + " No request was sent.";
                } catch (Exception ignored) {
                    job.failure = failureMessage;
                } finally {
                    job.worker = null;
                    stopped.countDown();
                }
            }

            @Override public void onSuccess() {
                if (!current(job)) { finish(job); return; }
                if (job.failure != null) { notice(job.failure); finish(job); }
                else completed.run();
            }

            @Override public void onCancel() { job.cancelled.set(true); finish(job); }

            @Override public void onThrowable(@NotNull Throwable ignored) {
                if (!disposed && !project.isDisposed()) notice(failureMessage);
                finish(job);
            }
        }.queue();
    }

    private boolean cancelled(Job job) {
        ProgressIndicator indicator = job.indicator;
        return disposed || project.isDisposed() || active != job || job.cancelled.get()
                || Thread.currentThread().isInterrupted() || (indicator != null && indicator.isCanceled());
    }

    private boolean current(Job job) { return !disposed && !project.isDisposed() && active == job && !job.cancelled.get(); }

    private void finish(Job job) {
        if (active == job) active = null;
        if (toolbarAnimation != null) toolbarAnimation.refresh();
    }

    private void stale(Job job) {
        notice("The checked changes, selected hunks, source content, or commit message changed. Run CodexCommit again for a fresh draft. "
                + "Your current commit message was preserved.");
        finish(job);
    }

    private void notice(String message) {
        if (!disposed && !project.isDisposed()) NotificationGroupManager.getInstance().getNotificationGroup("CodexCommit")
                .createNotification("CodexCommit", message, NotificationType.WARNING).notify(project);
    }

    @Override public void dispose() {
        disposed = true;
        if (toolbarAnimation != null) toolbarAnimation.dispose();
        Job job = active;
        active = null;
        if (job == null) return;
        job.cancelled.set(true);
        if (job.indicator != null) job.indicator.cancel();
        Thread worker = job.worker;
        if (worker != null && worker != Thread.currentThread()) worker.interrupt();
        // The worker polls cancellation while owning its child process and cleans it up before returning.
        if (job.worker != Thread.currentThread()) {
            try { job.workerStopped.await(6, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
    }
}
