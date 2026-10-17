package dev.codexcommit.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/** Stores an executable preference only, never prompts, patches, tokens, or drafts. */
@Service(Service.Level.APP)
@State(name = "CodexCommitSettings", storages = @Storage(value = "codexcommit.xml", roamingType = RoamingType.DISABLED))
public final class CodexCommitSettings implements PersistentStateComponent<CodexCommitSettings.Options> {
    public static final class Options {
        public String codexExecutable = "";
    }

    private Options options = new Options();

    public static CodexCommitSettings getInstance() {
        return ApplicationManager.getApplication().getService(CodexCommitSettings.class);
    }

    @Override public @NotNull Options getState() { return options; }

    @Override public void loadState(@NotNull Options state) {
        options = new Options();
        options.codexExecutable = state.codexExecutable == null ? "" : state.codexExecutable;
    }

    public String getExecutable() { return options.codexExecutable; }

    public void setExecutable(String value) { options.codexExecutable = value == null ? "" : value.strip(); }
}
