package dev.codexcommit.ide;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import dev.codexcommit.core.CodexExecutableLocator;
import dev.codexcommit.core.CodexRunner;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public final class CodexCommitConfigurable implements Configurable {
    private JPanel panel;
    private TextFieldWithBrowseButton executable;

    @Override public @Nls String getDisplayName() { return "CodexCommit"; }

    @Override public @Nullable JComponent createComponent() {
        panel = new JPanel(new BorderLayout(0, 12));
        JPanel row = new JPanel(new BorderLayout(8, 0));
        JLabel label = new JLabel("Native Codex executable:");
        row.add(label, BorderLayout.WEST);
        executable = new TextFieldWithBrowseButton(new JTextField(42));
        executable.setText(CodexCommitSettings.getInstance().getExecutable());
        executable.addBrowseFolderListener(null, FileChooserDescriptorFactory.singleFile()
                .withTitle("Choose the native Codex executable")
                .withDescription("Choose codex.exe on Windows or codex on Linux. Shell wrappers are not supported."));
        label.setLabelFor(executable.getTextField());
        executable.getTextField().getAccessibleContext().setAccessibleName("Native Codex executable");
        row.add(executable, BorderLayout.CENTER);
        panel.add(row, BorderLayout.NORTH);
        panel.add(new JLabel("<html>Leave blank to discover the native Codex executable on PATH.<br>"
                + "Supported native Codex CLI versions: " + CodexRunner.SUPPORTED_CLI_VERSIONS + ".<br>"
                + "Use an absolute path to codex.exe (Windows) or codex (Linux). macOS execution is disabled in this release.<br>"
                + "Shell wrappers, WSL, and custom commands are not accepted.<br><br>"
                + "Sign in using the official Codex CLI outside the IDE. CodexCommit does not store credentials.<br>"
                + "The Generate button sends the selected patch to OpenAI through your Codex account. Quota or charges may apply.<br>"
                + "Check your selected changes for private data and secrets before you use the button.<br>"
                + "Use a personal account without organization policy. Edit the draft in the commit message field.<br>"
                + "The IDE's Git setting must also identify an absolute path to native Git.</html>"), BorderLayout.CENTER);
        return panel;
    }

    @Override public boolean isModified() {
        return executable != null && !executable.getText().strip().equals(CodexCommitSettings.getInstance().getExecutable());
    }

    @Override public void apply() throws ConfigurationException {
        if (executable == null) return;
        String value = executable.getText().strip();
        if (!value.isEmpty()) {
            try { CodexExecutableLocator.find(value); }
            catch (IOException | IllegalArgumentException ignored) {
                throw new ConfigurationException("Choose an existing absolute path to the native Codex executable. Shell scripts are not supported.");
            }
        }
        CodexCommitSettings.getInstance().setExecutable(value);
    }

    @Override public void reset() {
        if (executable != null) executable.setText(CodexCommitSettings.getInstance().getExecutable());
    }

    @Override public void disposeUIResources() {
        if (executable != null) executable.dispose();
        panel = null;
        executable = null;
    }
}
