package dev.diffquill.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import dev.diffquill.core.ContentPolicy;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/** Plain text only: model output and repository content are never rendered as HTML. */
final class ReviewDialog extends DialogWrapper {
    private final JTextArea text;
    private final String explanation;
    private final boolean draft;
    private final JCheckBox accountAcknowledgement;

    ReviewDialog(Project project, String value, boolean draft) {
        super(project, true);
        this.draft = draft;
        accountAcknowledgement = draft ? null : new JCheckBox(
                "I use a personal Codex account. My account and computer have no organization policy.");
        text = new JTextArea(value, 26, 100);
        text.setEditable(draft);
        text.setLineWrap(draft);
        text.setWrapStyleWord(draft);
        text.setCaretPosition(0);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        explanation = draft
                ? "Examine this draft. You can change its text. Apply Draft changes only the commit message field. "
                  + "It does not make a commit, stage files, or push changes."
                : "Examine each file name and changed line. Send to Codex sends this request to OpenAI through your Codex account. "
                  + "The request can use your account quota. Use only a personal account without organization controls. "
                  + "The plugin cannot detect every account or device policy. Do not send with an organization account. "
                  + "Examine the request for private data and secrets. Cancel sends nothing.";
        setTitle(draft ? "DiffQuill — Review Draft" : "DiffQuill — Review Request Before Sending");
        setOKButtonText(draft ? "Apply Draft" : "Send to Codex");
        setCancelButtonText("Cancel");
        init();
        if (!draft) {
            setOKActionEnabled(false);
            accountAcknowledgement.addActionListener(event -> setOKActionEnabled(accountAcknowledgement.isSelected()));
        }
    }

    @Override protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        JTextArea info = new JTextArea(explanation, draft ? 4 : 7, 90);
        info.setEditable(false);
        info.setLineWrap(true);
        info.setWrapStyleWord(true);
        info.setOpaque(false);
        info.setFont(UIManager.getFont("Label.font"));
        panel.add(info, BorderLayout.NORTH);
        panel.add(new JScrollPane(text), BorderLayout.CENTER);
        if (!draft) panel.add(accountAcknowledgement, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(860, 550));
        return panel;
    }

    @Override public @Nullable JComponent getPreferredFocusedComponent() { return text; }

    @Override protected void doOKAction() {
        if (!draft && !accountAcknowledgement.isSelected()) {
            setErrorText("A personal account and a computer without organization policy are necessary for this release.");
            return;
        }
        if (draft) {
            try { ContentPolicy.validateMessage(text.getText()); }
            catch (IllegalArgumentException ignored) {
                setErrorText("The draft must be plain text, nonempty, and at most 16 KiB.");
                return;
            }
        }
        super.doOKAction();
    }

    String getText() { return text.getText().strip(); }
}
