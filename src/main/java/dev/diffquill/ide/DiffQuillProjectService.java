package dev.diffquill.ide;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.vcs.commit.CommitWorkflowUi;
import dev.diffquill.core.CodexExecutableLocator;
import dev.diffquill.core.CodexRunner;
import dev.diffquill.core.ContentPolicy;
import dev.diffquill.core.PromptBuilder;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One cancellable job per project, owned by the project/plugin lifetime. */
@Service(Service.Level.PROJECT)
public final class DiffQuillProjectService implements Disposable {
    private final Project project;
    private volatile boolean disposed;
    private volatile Job active;

    private static final class Job {
        final AtomicBoolean cancelled = new AtomicBoolean();
        volatile ProgressIndicator indicator;
        volatile CountDownLatch workerStopped = new CountDownLatch(0);
        volatile Thread worker;
        ChangeSnapshot snapshot;
        Path executable;
        String prompt;
        String draft;
        String failure;
    }

    public DiffQuillProjectService(Project project) { this.project = project; }

    public boolean isBusy() { return active != null; }

    public void start(CommitWorkflowUi ui, Document messageDocument) {
        if (disposed || project.isDisposed() || active != null) return;
        Job job = new Job();
        active = job;
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
        task(job, "DiffQuill: prepare selected changes", () -> {
            String patch = job.snapshot.build(() -> cancelled(job));
            job.prompt = PromptBuilder.build(patch);
            job.executable = CodexExecutableLocator.find(DiffQuillSettings.getInstance().getExecutable());
        }, () -> reviewRequest(job), "Could not prepare the selected changes. Check native Codex/Git paths and the whole-file selection. "
                + "The combined before/after text must be at most 256 KiB and the patch at most 120 KiB. No request was sent.");
    }

    private void reviewRequest(Job job) {
        if (!current(job)) return;
        if (!job.snapshot.isFresh()) { stale(job); return; }
        ReviewDialog dialog = new ReviewDialog(project, job.prompt, false);
        if (!show(job, dialog)) { finish(job); return; }
        if (!current(job)) return;
        if (!job.snapshot.isFresh()) { stale(job); return; }
        task(job, "DiffQuill: generate commit message", () -> {
            job.draft = new CodexRunner().generate(job.executable, job.prompt, () -> cancelled(job));
            ContentPolicy.validateMessage(job.draft);
        }, () -> reviewDraft(job), "Codex could not generate a draft. This release requires native Codex CLI 0.160.1 on Windows or Linux "
                + "and blocks managed/system Codex configuration. Check sign-in, account quota, and connection. "
                + "The CLI may have processed the request before failing. Your commit message was not changed.");
    }

    private void reviewDraft(Job job) {
        if (!current(job)) return;
        if (!job.snapshot.isFresh()) { stale(job); return; }
        ReviewDialog dialog = new ReviewDialog(project, job.draft, true);
        if (show(job, dialog) && current(job)) {
            if (!job.snapshot.isFresh()) { stale(job); return; }
            job.snapshot.apply(dialog.getText());
        }
        finish(job);
    }

    private boolean show(Job job, ReviewDialog dialog) {
        if (!current(job)) return false;
        Disposable close = () -> { if (!dialog.isDisposed()) dialog.close(ReviewDialog.CANCEL_EXIT_CODE); };
        Disposer.register(this, close);
        try { return dialog.showAndGet(); }
        finally { Disposer.dispose(close); }
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

    private void finish(Job job) { if (active == job) active = null; }

    private void stale(Job job) {
        notice("The checked changes, selected hunks, source content, or commit message changed. Run DiffQuill again to review a fresh request. "
                + "Your current commit message was preserved.");
        finish(job);
    }

    private void notice(String message) {
        if (!disposed && !project.isDisposed()) NotificationGroupManager.getInstance().getNotificationGroup("DiffQuill")
                .createNotification("DiffQuill", message, NotificationType.WARNING).notify(project);
    }

    @Override public void dispose() {
        disposed = true;
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
