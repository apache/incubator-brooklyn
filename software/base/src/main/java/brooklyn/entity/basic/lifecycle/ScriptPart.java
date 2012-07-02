package brooklyn.entity.basic.lifecycle;

import groovy.lang.Closure;

import java.util.*;

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
        for(String line:lines){
            append(line);
        }
        return helper;
    }

    public ScriptHelper append(String... lines){
        return append(Arrays.asList(lines));
    }

    public ScriptHelper prepend(String line) {
        lines.add(0, line);
        return helper;
    }

    //public ScriptHelper prepend(Object l1, Object l2, Object... ll) {
    //    for (int i = ll.length - 1; i >= 0; i--) prepend(ll[i])
    //    prepend(l2);
    //    prepend(l1);
    //    return helper;
    //}

    public ScriptHelper prepend(Collection<String> lines) {
        List<String> l = new ArrayList<String>(lines);
        Collections.reverse(l);
        for(String line: l){
            prepend(line);
        }
        return helper;
    }

    public ScriptHelper prepend(String... lines){
        return prepend(Arrays.asList(lines));
    }

    public ScriptHelper reset(String line) {
        return reset(Arrays.asList(line));
    }

   public ScriptHelper reset(List<String> ll) {
        lines.clear();
        return append(ll);
    }

    /**
     * Passes the list to a closure for amendment; result of closure ignored.
     */
    public ScriptHelper apply(Closure<List<String>> c) {
        c.call(lines);
        return helper;
    }

    public boolean isEmpty() {
       return lines.isEmpty();
    }
}