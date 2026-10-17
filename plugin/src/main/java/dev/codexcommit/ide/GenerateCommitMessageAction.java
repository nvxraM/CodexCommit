package dev.codexcommit.ide;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.vcs.commit.CommitWorkflowUi;
import org.jetbrains.annotations.NotNull;

/** Appears only in a commit UI that exposes its checked, tracked changes. */
public final class GenerateCommitMessageAction extends DumbAwareAction {
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }

    @Override public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        CommitWorkflowUi ui = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI);
        event.getPresentation().setEnabled(project != null && !project.isDisposed() && ui != null
                && event.getData(VcsDataKeys.COMMIT_MESSAGE_DOCUMENT) != null
                && !ui.getIncludedChanges().isEmpty()
                && !project.getService(CodexCommitProjectService.class).isBusy());
    }

    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        CommitWorkflowUi ui = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI);
        if (project != null && !project.isDisposed() && ui != null) {
            var document = event.getData(VcsDataKeys.COMMIT_MESSAGE_DOCUMENT);
            if (document != null) project.getService(CodexCommitProjectService.class).start(ui, document);
        }
    }
}
