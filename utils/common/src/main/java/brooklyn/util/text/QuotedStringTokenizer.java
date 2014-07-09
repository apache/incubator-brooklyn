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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

/** As 'StringTokenizer' but items in quotes (single or double) are treated as single tokens
 * (cf mortbay's QuotedStringTokenizer) 
 */  
public class QuotedStringTokenizer {

	final StringTokenizer delegate;
	final String quoteChars;
	final boolean includeQuotes;
	final String delimiters;
	final boolean includeDelimiters;

	public static String DEFAULT_QUOTE_CHARS = "\"\'";
	
	
	protected String DEFAULT_QUOTE_CHARS() {
		return DEFAULT_QUOTE_CHARS;
	}
	
	public final static String DEFAULT_DELIMITERS = " \t\n\r\f";	
	
	/** default quoted tokenizer, using single and double quotes as quote chars and returning quoted results
	 * (use unquoteToken to unquote), and using whitespace chars as delimeters (not included as tokens);
	 * string may be null if the nothing will be tokenized and the class is used only for
	 * quoteToken(String) and unquote(String).
	 */
	public QuotedStringTokenizer(String stringToTokenize) {
		this(stringToTokenize, true);
	}
	public QuotedStringTokenizer(String stringToTokenize, boolean includeQuotes) {
		this(stringToTokenize, null, includeQuotes);
	}
	public QuotedStringTokenizer(String stringToTokenize, String quoteChars, boolean includeQuotes) {
		this(stringToTokenize, quoteChars, includeQuotes, null, false);
	}

	public QuotedStringTokenizer(String stringToTokenize, String quoteChars, boolean includeQuotes, String delimiters, boolean includeDelimiters) {
		delegate = new StringTokenizer(stringToTokenize==null ? "" : stringToTokenize, (delimiters==null ? DEFAULT_DELIMITERS : delimiters), true);
		this.quoteChars = quoteChars==null ? DEFAULT_QUOTE_CHARS() : quoteChars;
		this.includeQuotes = includeQuotes;
		this.delimiters = delimiters==null ? DEFAULT_DELIMITERS : delimiters;
		this.includeDelimiters = includeDelimiters;
		updateNextToken();
	}
	
	public static class Builder {
	    private String quoteChars = DEFAULT_QUOTE_CHARS;
	    private boolean includeQuotes=true;
	    private String delimiterChars=DEFAULT_DELIMITERS;
	    private boolean includeDelimiters=false;

	    public QuotedStringTokenizer build(String stringToTokenize) {
	        return new QuotedStringTokenizer(stringToTokenize, quoteChars, includeQuotes, delimiterChars, includeDelimiters);
	    }
        public List<String> buildList(String stringToTokenize) {
            return new QuotedStringTokenizer(stringToTokenize, quoteChars, includeQuotes, delimiterChars, includeDelimiters).remainderAsList();
        }
        
        public Builder quoteChars(String quoteChars) { this.quoteChars = quoteChars; return this; }
        public Builder addQuoteChars(String quoteChars) { this.quoteChars = this.quoteChars + quoteChars; return this; }
        public Builder includeQuotes(boolean includeQuotes) { this.includeQuotes = includeQuotes; return this; } 
        public Builder delimiterChars(String delimiterChars) { this.delimiterChars = delimiterChars; return this; }
        public Builder addDelimiterChars(String delimiterChars) { this.delimiterChars = this.delimiterChars + delimiterChars; return this; }
        public Builder includeDelimiters(boolean includeDelimiters) { this.includeDelimiters = includeDelimiters; return this; } 
	}
    public static Builder builder() {
        return new Builder();
    }

	String peekedNextToken = null;
	
	public synchronized boolean hasMoreTokens() {
		return peekedNextToken!=null;
	}
	
	public synchronized String nextToken() {	
		if (peekedNextToken==null) throw new NoSuchElementException();
		String lastToken = peekedNextToken;
		updateNextToken();
		return includeQuotes ? lastToken : unquoteToken(lastToken);
	}

	/** this method removes all unescaped quote chars, i.e. quote chars preceded by no backslashes (or a larger even number of them);
	 * it also unescapes '\\' as '\'.  it does no other unescaping.  */
	public String unquoteToken(String word) {
		// ( (\\A|[^\\\\]) (\\\\\\\\)* ) [ Pattern.quote(quoteChars) ]  $1
		word = word.replaceAll(
				"((\\A|[^\\\\])(\\\\\\\\)*)["+
					//Pattern.quote(
						quoteChars
					//)
						+"]+",
				"$1");
		//above pattern removes any quote preceded by even number of backslashes
		//now it is safe to replace any \c by c
		word = word.replaceAll("\\\\"+"([\\\\"+
				//Pattern.quote(
				quoteChars
				//)
				+"])", "$1");
				
		return word;
	}
	
	/** returns the input text escaped for use with unquoteTokens, and wrapped in the quoteChar[0] (usu a double quote) */
	public String quoteToken(String unescapedText) {
		String result = unescapedText;
		//replace every backslash by two backslashes
		result = result.replaceAll("\\\\", "\\\\\\\\");
		//now replace every quote char by backslash quote char
		result = result.replaceAll("(["+quoteChars+"])", "\\\\$1");
		//then wrap in quote
		result = quoteChars.charAt(0) + result + quoteChars.charAt(0);
		return result;
	}

	protected synchronized void updateNextToken() {
		peekedNextToken = null;
		String token;
		do {
			if (!delegate.hasMoreTokens()) return;
			token = delegate.nextToken();
			//skip delimeters
		} while (!includeDelimiters && token.matches("["+delimiters+"]+"));
		
		StringBuffer nextToken = new StringBuffer(token);
		pullUntilValid(nextToken);
		peekedNextToken = nextToken.toString();
	}

	private void pullUntilValid(StringBuffer nextToken) {
        while (hasOpenQuote(nextToken.toString(), quoteChars) && delegate.hasMoreTokens()) {
            //keep appending until the quote is ended or there are no more quotes
            nextToken.append(delegate.nextToken());
        }
    }

    public static boolean hasOpenQuote(String stringToCheck) {
		return hasOpenQuote(stringToCheck, DEFAULT_QUOTE_CHARS);
	}

	public static boolean hasOpenQuote(String stringToCheck, String quoteChars) {		
		String x = stringToCheck;
		if (x==null) return false;

		StringBuffer xi = new StringBuffer();
		for (int i=0; i<x.length(); i++) {
			char c = x.charAt(i);
			if (c=='\\') i++;
			else if (quoteChars.indexOf(c)>=0) {
				xi.append(c);
			}
		}
		x = xi.toString();
		
		while (x.length()>0) {
			char c = x.charAt(0);
			int match = x.indexOf(c, 1);
			if (match==-1) return true;
			x = x.substring(match+1);
		}
		return false;
	}

	public List<String> remainderAsList() {
		List<String> l = new ArrayList<String>();
		while (hasMoreTokens())
			l.add(nextToken());
		return l;
	}

}
