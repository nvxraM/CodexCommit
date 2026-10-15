package dev.codexcommit.ide;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import dev.codexcommit.core.CodexExecutableLocator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public final class CodexCommitConfigurable implements Configurable {
    private JPanel panel;
    private JTextField executable;

    @Override public @Nls String getDisplayName() { return "CodexCommit"; }

    @Override public @Nullable JComponent createComponent() {
        panel = new JPanel(new BorderLayout(0, 12));
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(new JLabel("Native Codex executable:"), BorderLayout.WEST);
        executable = new JTextField(CodexCommitSettings.getInstance().getExecutable(), 42);
        row.add(executable, BorderLayout.CENTER);
        panel.add(row, BorderLayout.NORTH);
        panel.add(new JLabel("<html>Leave blank to discover the native Codex executable on PATH.<br>"
                + "Use an absolute path to codex.exe (Windows) or codex (Linux). macOS execution is disabled in this release.<br>"
                + "Shell wrappers, WSL, and custom commands are not accepted.<br><br>"
                + "Sign in using the official Codex CLI outside the IDE. CodexCommit does not store credentials.<br>"
                + "Every generation requires review of the exact request before it is sent through your Codex account.<br>"
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

    @Override public void disposeUIResources() { panel = null; executable = null; }
}
