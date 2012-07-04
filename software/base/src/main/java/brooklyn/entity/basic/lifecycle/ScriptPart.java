package brooklyn.entity.basic.lifecycle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ScriptPart {
    protected ScriptHelper helper;
    protected List<String> lines = new LinkedList<String>();

    public ScriptPart(ScriptHelper helper) {
        this.helper = helper;
    }

    public ScriptHelper append(CharSequence line) {
        lines.add(line.toString());
        return helper;
    }

    public ScriptHelper append(Collection<? extends CharSequence> lines) {
        for (CharSequence line : lines) {
            append(line);
        }
        return helper;
    }

    public ScriptHelper append(CharSequence... lines) {
        return append(Arrays.asList(lines));
    }

    public ScriptHelper prepend(CharSequence line) {
        lines.add(0, line.toString());
        return helper;
    }

    public ScriptHelper prepend(Collection<? extends CharSequence> lines) {
        List<CharSequence> reversedLines = new ArrayList<CharSequence>(lines);
        Collections.reverse(reversedLines);
        for (CharSequence line : reversedLines) {
            prepend(line);
        }
        return helper;
    }

    public ScriptHelper prepend(CharSequence... lines) {
        return prepend(Arrays.asList(lines));
    }

    public ScriptHelper reset(CharSequence line) {
        return reset(Arrays.asList(line));
    }

    public ScriptHelper reset(List<? extends CharSequence> ll) {
        lines.clear();
        return append(ll);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}