package org.apache.brooklyn.enricher.stock.reducer;

import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;

public abstract class GenericStringReducer<T> extends Reducer<T, String>{

    @Override
    protected Function<List<T>, String> createReducerFunction(
            String reducerName, Map<String, ?> parameters) {
        if (reducerName.equals("formatString")){
            String format = Preconditions.checkNotNull((String)parameters.get("format"), "format");
            return new FormatStringReducerFunction<T>(format);
        }
        return null;
    }

}
