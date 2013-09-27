package io.brooklyn.camp.brooklyn.spi.dsl.parse;

import java.util.List;

import com.google.common.collect.ImmutableList;

public class FunctionWithArgs {
    private final String function;
    private final List<Object> args;
    
    public FunctionWithArgs(String function, List<Object> args) {
        this.function = function;
        this.args = args==null ? null : ImmutableList.copyOf(args);
    }
    
    public String getFunction() {
        return function;
    }
    
    /** arguments (typically {@link QuotedString} or more {@link FunctionWithArgs}) */
    public List<Object> getArgs() {
        return args;
    }
    
    @Override
    public String toString() {
        return function+(args==null ? "" : args);
    }

    public Object arg(int i) {
        return args.get(i);
    }
    
}