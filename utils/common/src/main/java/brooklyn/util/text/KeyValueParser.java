/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.util.text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Splitter;


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
 * Consider instead using {@link Splitter#withKeyValueSeparator(char)}, but that doesn't give the
 * same behaviour for values, see {@link QuotedStringTokenizer}. For example:
 * <pre>
 * {@code
 * String val = "a=x,b=y";
 * Map<String,String> map = Splitter.on(",").withKeyValueSeparator("=").split(val);
 * }
 * </pre>
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
    
    /** takes a string of the form "key=value,key2=value2" and returns a map;
     * values can be quoted (but not keys) */
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
