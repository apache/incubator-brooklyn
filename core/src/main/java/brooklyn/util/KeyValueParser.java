package brooklyn.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Parses a String that consists of multiple arguments, which are either single or key-value pairs.
 * The value may be in quotes.
 * 
 * For example:
 *  a=x, b="x x", c, "d d"
 *  
 * Would return the ordered map:
 *  "a" = "x"
 *  "b" = "x x"
 *  "c" = null
 *  "d d" = null 
 * 
 * @author aled
 **/
public class KeyValueParser {

    public static String toLine(Map<String, String> parts) {
        QuotedStringTokenizer tokenizer = new QuotedStringTokenizer("", true);
        
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : parts.entrySet()) {
            result.append(tokenizer.quoteToken(entry.getKey()));
            if (entry.getValue() != null) result.append("="+tokenizer.quoteToken(entry.getValue()));
            result.append(", ");
        }
        if (result.length() > 0) result.deleteCharAt(result.length()-1);
        return result.toString();
    }

    public static String toLine(Collection<String> parts) {
        QuotedStringTokenizer tokenizer = new QuotedStringTokenizer("", false);
        
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            result.append(tokenizer.quoteToken(part)+", ");
        }
        if (result.length() > 0) result.deleteCharAt(result.length()-1);
        return result.toString();
    }

    public static List<String> parseList(String line) {
        List<String> result = new ArrayList<String>();
        QuotedStringTokenizer tokenizer = new QuotedStringTokenizer(line, null, true, ",", false);
        
        while (tokenizer.hasMoreTokens()) {
            result.add(tokenizer.unquoteToken(tokenizer.nextToken().trim()));
        }
        return result;
    }
    
    @Deprecated // use parseMap
    public static Map<String,String> parse(String line) {
        return parseMap(line);
    }
    
    public static Map<String,String> parseMap(String line) {
        Map<String,String> result = new LinkedHashMap<String,String>();
        
        QuotedStringTokenizer tokenizer = new QuotedStringTokenizer(line, null, true, ",", false);
        
        while (tokenizer.hasMoreTokens()) {
            //String token = tokenizer.unquoteToken(tokenizer.nextToken().trim());
            String token = tokenizer.nextToken().trim();
            
            int index = token.indexOf("=");
            
            if (index < 0) {
                String unquotedKey = tokenizer.unquoteToken(token);
                result.put(unquotedKey, null);
                
            } else if (index < (token.length()-1)) {
                String unquotedKey = tokenizer.unquoteToken(token.substring(0, index).trim());
                String unquotedVal = tokenizer.unquoteToken(token.substring(index+1).trim());
                result.put(unquotedKey, unquotedVal);
                
            } else { // ends with =
                assert index == token.length() -1;
                String unquotedKey = tokenizer.unquoteToken(token.substring(0, index).trim());
                result.put(unquotedKey, "");
            }
        }
        return result;
    }
}
