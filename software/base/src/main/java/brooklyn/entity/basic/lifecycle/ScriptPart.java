package brooklyn.entity.basic.lifecycle;

import groovy.lang.Closure;

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

    public ScriptHelper append(String line) {
        lines.add(line);
        return helper;
    }

    public ScriptHelper append(Collection<String> lines) {
        for (String line : lines) {
            append(line);
        }
        return helper;
    }

    public ScriptHelper append(String... lines) {
        return append(Arrays.asList(lines));
    }

    public ScriptHelper prepend(String line) {
        lines.add(0, line);
        return helper;
    }

    public ScriptHelper prepend(Collection<String> lines) {
        List<String> reversedLines = new ArrayList<String>(lines);
        Collections.reverse(reversedLines);
        for (String line : reversedLines) {
            prepend(line);
        }
        return helper;
    }

    public ScriptHelper prepend(String... lines) {
        return prepend(Arrays.asList(lines));
    }

    public ScriptHelper reset(String line) {
        return reset(Arrays.asList(line));
    }

    public ScriptHelper reset(List<String> ll) {
        lines.clear();
        return append(ll);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}