package io.brooklyn.camp.brooklyn.spi.dsl.parse;

import brooklyn.util.text.StringEscapes.JavaStringEscapes;

public class QuotedString {
    private final String s;
    public QuotedString(String s) {
        this.s = s;
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
}