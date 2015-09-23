package org.apache.brooklyn.enricher.stock.reducer;

import java.util.List;
import java.util.Map;

import com.google.common.base.Function;

public class StringStringReducer extends GenericStringReducer<String> {
    
    public StringStringReducer() {}

    @Override
    protected Function<List<String>, String> createReducerFunction(
            String reducerName, Map<String, ?> parameters) {
        Function<List<String>, String> function = super.createReducerFunction(reducerName, parameters);
        if(function != null) return function;
        
        if(reducerName.equals("joiner")){
            return new JoinerFunction(parameters.get("separator"));
        }
        throw new IllegalStateException("unknown function: " + reducerName);
    }
}