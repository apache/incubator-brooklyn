package org.apache.brooklyn.enricher.stock.reducer;

import java.util.List;
import java.util.Map;

import com.google.common.base.Function;

public class ObjectStringReducer extends GenericStringReducer<Object> {

    @Override
    protected Function<List<Object>, String> createReducerFunction(
            String reducerName, Map<String, ?> parameters) {
        
        Function<List<Object>, String> function = super.createReducerFunction(reducerName, parameters);
        if(function != null) return function;

        throw new IllegalStateException("unknown function: " + reducerName);
    }
}
