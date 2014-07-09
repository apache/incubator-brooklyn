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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** utility which takes a bunch of segments and applies shortening rules to them */
public class StringShortener {

    protected Map<String,String> wordsByIdInOrder = new LinkedHashMap<String,String>();
    protected String separator = null;
    
    protected interface ShorteningRule {
        /** returns the new list, with the relevant items in the list replaced */
        public int apply(LinkedHashMap<String, String> words, int maxlen, int length);
    }
    
    protected class TruncationRule implements ShorteningRule {
        public TruncationRule(String id, int len) {
            this.id = id;
            this.len = len;
        }
        String id;
        int len;
        
        public int apply(LinkedHashMap<String, String> words, int maxlen, int length) {
            String v = words.get(id);
            if (v!=null && v.length()>len) {
                int charsToRemove = v.length() - len;
                if (length-charsToRemove < maxlen) charsToRemove = length-maxlen;
                words.put(id, v.substring(0, v.length() - charsToRemove));
                length -= charsToRemove;
                if (charsToRemove==v.length() && separator!=null && length>0)
                    length -= separator.length();
            }
            return length;
        }
    }
    
    protected class RemovalRule implements ShorteningRule {
        public RemovalRule(String id) {
            this.id = id;
        }
        String id;
        
        public int apply(LinkedHashMap<String, String> words, int maxlen, int length) {
            String v = words.get(id);
            if (v!=null) {
                words.remove(id);
                length -= v.length();
                if (separator!=null && length>0)
                    length -= separator.length();
            }
            return length;
        }
    }
    
    private List<ShorteningRule> rules = new ArrayList<StringShortener.ShorteningRule>();
    

    public StringShortener separator(String separator) {
        this.separator = separator;
        return this;
    }

    public StringShortener append(String id, String text) {
        String old = wordsByIdInOrder.put(id, text);
        if (old!=null) {
            throw new IllegalStateException("Cannot append with id '"+id+"' when id already present");
        }
        // TODO expose a replace or update
        return this;
    }

    public StringShortener truncate(String id, int len) {
        String v = wordsByIdInOrder.get(id);
        if (v!=null && v.length()>len) {
            wordsByIdInOrder.put(id, v.substring(0, len));
        }
        return this;
    }

    public StringShortener canTruncate(String id, int len) {
        rules.add(new TruncationRule(id, len));
        return this;
    }

    public StringShortener canRemove(String id) {
        rules.add(new RemovalRule(id));
        return this;
    }

    public String getStringOfMaxLength(int maxlen) {
        LinkedHashMap<String, String> words = new LinkedHashMap<String,String>();
        words.putAll(wordsByIdInOrder);
        int length = 0;
        for (String w: words.values()) {
            if (!Strings.isBlank(w)) {
                length += w.length();
                if (separator!=null)
                    length += separator.length();
            }
        }
        if (separator!=null && length>0)
            // remove trailing separator if one had been added
            length -= separator.length();
        
        List<ShorteningRule> rulesLeft = new ArrayList<ShorteningRule>();
        rulesLeft.addAll(rules);
        
        while (length > maxlen && !rulesLeft.isEmpty()) {
            ShorteningRule r = rulesLeft.remove(0);
            length = r.apply(words, maxlen, length);
        }
        
        StringBuilder sb = new StringBuilder();
        for (String w: words.values()) {
            if (!Strings.isBlank(w)) {
                if (separator!=null && sb.length()>0)
                    sb.append(separator);
                sb.append(w);
            }
        }
        
        String result = sb.toString();
        if (result.length() > maxlen) result = result.substring(0, maxlen);
        
        return result;
    }

}
