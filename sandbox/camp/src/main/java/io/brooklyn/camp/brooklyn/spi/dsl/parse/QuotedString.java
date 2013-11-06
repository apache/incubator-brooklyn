package io.brooklyn.camp.brooklyn.spi.dsl.parse;

import static com.google.common.base.Preconditions.checkNotNull;
import brooklyn.util.text.StringEscapes.JavaStringEscapes;

import com.google.common.base.Objects;

public class QuotedString {
    private final String s;
    
    public QuotedString(String s) {
        this.s = checkNotNull(s, "string");
    }
    @Override
    public String toString() {
        return s;
    }
    public String unwrapped() {
        return JavaStringEscapes.unwrapJavaString(s);
    }
    
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof QuotedString) && ((QuotedString)obj).toString().equals(toString());
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(s);
    }
}