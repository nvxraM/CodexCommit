package dev.codexcommit.ide;

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;

import java.util.ArrayList;
import java.util.AbstractList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.io.IOException;
import dev.codexcommit.core.ContentPolicy;

/** Uses the public diff comparison API and emits only relative names plus ordinary unified hunks. */
final class UnifiedPatch {
    record Part(int start1, int end1, int start2, int end2) {}

    static String build(String beforeName, String afterName, String before, String after, BooleanSupplier cancelled) throws Exception {
        if (cancelled.getAsBoolean()) throw new InterruptedException("Cancelled.");
        if (beforeName != null && beforeName.equals(afterName) && before.equals(after)) {
            throw new IOException("A selected change has no text difference. Metadata-only changes are not supported.");
        }
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        List<LineFragment> fragments = ComparisonManager.getInstance().compareLines(before, after, ComparisonPolicy.DEFAULT, indicator);
        List<String> oldLines = lines(before, cancelled);
        List<String> newLines = lines(after, cancelled);
        List<Part> parts = new ArrayList<>();
        for (LineFragment fragment : fragments) {
            parts.add(new Part(Math.min(oldLines.size(), fragment.getStartLine1()), Math.min(oldLines.size(), fragment.getEndLine1()),
                    Math.min(newLines.size(), fragment.getStartLine2()), Math.min(newLines.size(), fragment.getEndLine2())));
        }
        // A trailing newline is not represented by our line list's removed empty sentinel.
        // Ensure a newline-only change still replaces the final real line and emits its marker.
        if (before.endsWith("\n") != after.endsWith("\n") && !oldLines.isEmpty() && !newLines.isEmpty()) {
            if (parts.isEmpty() || parts.getLast().end1() < oldLines.size() || parts.getLast().end2() < newLines.size()
                    || (parts.getLast().start1() == parts.getLast().end1() && parts.getLast().start2() == parts.getLast().end2())) {
                if (!parts.isEmpty() && parts.getLast().start1() == parts.getLast().end1()
                        && parts.getLast().start2() == parts.getLast().end2()) parts.removeLast();
                parts.add(new Part(oldLines.size() - 1, oldLines.size(), newLines.size() - 1, newLines.size()));
            }
        }
        return format(beforeName, afterName, before, after, oldLines, newLines, parts, cancelled);
    }

    static String format(String beforeName, String afterName, String before, String after, List<Part> parts,
                         BooleanSupplier cancelled) throws InterruptedException, IOException {
        return format(beforeName, afterName, before, after, lines(before, cancelled), lines(after, cancelled), parts, cancelled);
    }

    private static String format(String beforeName, String afterName, String before, String after,
                                 List<String> oldLines, List<String> newLines, List<Part> parts,
                                 BooleanSupplier cancelled) throws InterruptedException, IOException {
        StringBuilder output = new StringBuilder();
        output.append("diff --git ").append(quoted("a/" + (beforeName == null ? afterName : beforeName))).append(' ')
                .append(quoted("b/" + (afterName == null ? beforeName : afterName))).append('\n');
        if (beforeName == null) output.append("new file mode 100644\n");
        else if (afterName == null) output.append("deleted file mode 100644\n");
        else if (!beforeName.equals(afterName)) {
            if (before.equals(after)) output.append("similarity index 100%\n");
            output.append("rename from ").append(quoted(beforeName)).append('\n');
            output.append("rename to ").append(quoted(afterName)).append('\n');
        }
        output.append("--- ").append(beforeName == null ? "/dev/null" : quoted("a/" + beforeName)).append('\n');
        output.append("+++ ").append(afterName == null ? "/dev/null" : quoted("b/" + afterName)).append('\n');
        for (int index = 0; index < parts.size();) {
            if (cancelled.getAsBoolean()) throw new InterruptedException("Cancelled.");
            Part first = parts.get(index);
            int start1 = Math.max(0, first.start1() - 3);
            int start2 = Math.max(0, first.start2() - 3);
            int end1 = Math.min(oldLines.size(), first.end1() + 3);
            int end2 = Math.min(newLines.size(), first.end2() + 3);
            int last = index;
            while (last + 1 < parts.size() && parts.get(last + 1).start1() - 3 <= end1) {
                Part next = parts.get(++last);
                end1 = Math.min(oldLines.size(), next.end1() + 3);
                end2 = Math.min(newLines.size(), next.end2() + 3);
            }
            output.append("@@ -").append(range(start1, end1)).append(" +").append(range(start2, end2)).append(" @@\n");
            int cursor1 = start1;
            int cursor2 = start2;
            for (int partIndex = index; partIndex <= last; partIndex++) {
                Part part = parts.get(partIndex);
                while (cursor1 < part.start1()) {
                    line(output, ' ', oldLines, cursor1++, before, cancelled);
                    cursor2++;
                }
                while (cursor1 < part.end1()) line(output, '-', oldLines, cursor1++, before, cancelled);
                while (cursor2 < part.end2()) line(output, '+', newLines, cursor2++, after, cancelled);
            }
            while (cursor1 < end1) { line(output, ' ', oldLines, cursor1++, before, cancelled); cursor2++; }
            index = last + 1;
        }
        return output.toString();
    }

    private static List<String> lines(String text, BooleanSupplier cancelled) throws InterruptedException {
        if (text.isEmpty()) return List.of();
        int count = text.endsWith("\n") ? 0 : 1;
        for (int offset = 0; (offset = text.indexOf('\n', offset)) >= 0; offset++) {
            if ((++count & 1023) == 0 && cancelled.getAsBoolean()) throw new InterruptedException("Cancelled.");
        }
        int[] starts = new int[count];
        int index = 1;
        for (int offset = 0; index < count && (offset = text.indexOf('\n', offset)) >= 0; offset++) {
            if ((index & 1023) == 0 && cancelled.getAsBoolean()) throw new InterruptedException("Cancelled.");
            starts[index++] = offset + 1;
        }
        return new AbstractList<>() {
            @Override public int size() { return starts.length; }
            @Override public String get(int index) {
                java.util.Objects.checkIndex(index, starts.length);
                int end = index + 1 < starts.length ? starts[index + 1] : text.length();
                if (end > starts[index] && text.charAt(end - 1) == '\n') end--;
                return text.substring(starts[index], end);
            }
        };
    }

    private static void line(StringBuilder output, char prefix, List<String> lines, int index, String original,
                             BooleanSupplier cancelled) throws InterruptedException, IOException {
        if (cancelled.getAsBoolean()) throw new InterruptedException("Cancelled.");
        String line = lines.get(index);
        if ((long) output.length() + line.length() + 2 > ContentPolicy.MAX_PATCH_BYTES) {
            throw new ChangeSnapshot.SelectionException("Patch exceeds 512 KiB. Select fewer changes.");
        }
        output.append(prefix).append(line).append('\n');
        if (index == lines.size() - 1 && !original.endsWith("\n")) output.append("\\ No newline at end of file\n");
    }

    private static String range(int start, int end) { return (end == start ? start : start + 1) + "," + (end - start); }

    private static String quoted(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
