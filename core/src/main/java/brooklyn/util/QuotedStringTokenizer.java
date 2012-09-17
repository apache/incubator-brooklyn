package brooklyn.util;


/** @deprecated since 0.4.0 use {@link brooklyn.util.text.QuotedStringTokenizer} 
 */  
public class QuotedStringTokenizer extends brooklyn.util.text.QuotedStringTokenizer {

    public QuotedStringTokenizer(String stringToTokenize, boolean includeQuotes) {
        super(stringToTokenize, includeQuotes);
    }

    public QuotedStringTokenizer(String stringToTokenize, String quoteChars, boolean includeQuotes, String delimiters,
            boolean includeDelimiters) {
        super(stringToTokenize, quoteChars, includeQuotes, delimiters, includeDelimiters);
    }

    public QuotedStringTokenizer(String stringToTokenize, String quoteChars, boolean includeQuotes) {
        super(stringToTokenize, quoteChars, includeQuotes);
    }

    public QuotedStringTokenizer(String stringToTokenize) {
        super(stringToTokenize);
    }
    
}
