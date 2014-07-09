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

import com.google.common.base.Throwables;

public class WildcardGlobs {

    /** returns true iff the target matches the given pattern,
     * under simplified bash rules -- viz permitting * and ? and comma delimited patterns inside curly braces 
     * @throws InvalidPatternException */
    public static boolean isGlobMatched(String globPattern, String targetText) throws InvalidPatternException {
        List<String> patterns = getGlobsAfterBraceExpansion(globPattern);       
        for (String p : patterns) {
            if (isNoBraceGlobMatched(p, targetText))
                return true;
        }
        return false;
    }

    /** whether a glob-ish string without braces (e.g. containing just ? and * chars) matches;
     * can be used directly, also used implicitly by isGlobMatched after glob expansion */
    public static boolean isNoBraceGlobMatched(String globPattern, String target) {
        int pi=0, ti=0;
        while (pi<globPattern.length() && ti<target.length()) {
            char pc = globPattern.charAt(pi);
            char tc = target.charAt(pi);
            if (pc=='?') {
                pi++; ti++;
                continue;
            }
            if (pc!='*') {
                if (pc!=tc) return false;
                pi++; ti++;
                continue;
            }
            //match 0 or more chars
            String prest = globPattern.substring(pi+1);
            while (ti<=target.length()) {
                if (isNoBraceGlobMatched(prest, target.substring(ti)))
                    return true;
                ti++;
            }
            return false;
        }
        while (pi<globPattern.length() && globPattern.charAt(pi)=='*')
            pi++;
        return (pi==globPattern.length() && ti==target.length());
    }   

    /** returns a list with no curly braces in any entries,
     * and guaranteeing order such that any {..,X,..,Y,..} will result in X being before Y in the resulting list;
     * e.g. given a{,b,c} gives a ab and ac; no special treatment of numeric ranges, quotes, or parentheses 
     * (see SpecialistGlobExpander for that) */
    public static List<String> getGlobsAfterBraceExpansion(String pattern) throws InvalidPatternException {
        return getGlobsAfterBraceExpansion(pattern, false, PhraseTreatment.NOT_A_SPECIAL_CHAR, PhraseTreatment.NOT_A_SPECIAL_CHAR);
    }

    /** if a string contains a demarcated phrase, e.g. between open and close parentheses, or inside unescaped quotes
     * this argument determines how that phrase is treated with regards to brace expansion */
    public enum PhraseTreatment {
        /** the region is treated like any other region */
        NOT_A_SPECIAL_CHAR, 
        /** the interior will be expanded if there is a {x,y} expression _entirely_ inside the phrase, but otherwise commas inside it will be ignored;
         * it will be an error if there is a { char inside the phrase, or if the phrase is not internally well-formed with regards to the phrase characters,
         * (e.g. if quotes are interior expandable and parens are anything but not_a_special_char (e.g. interior expandable or interior not expandable) 
         * then any expression inside a quoted phrase must have matching parentheses) */
        INTERIOR_EXPANDABLE, 
        /** the interior will not be expanded at all, not if there's a comma inside, and not even if there is a {x,y} expression entirely inside; the braces will be left;
         * interior of parenthetical phrases must have matching parentheses (to determine the right end parenthesis),
         * apart from parentheses inside any quoted phrases when quotes are interior_not_expandable which will be ignored;
         * quotes inside not_expandable paren phrases will be ignored */
        INTERIOR_NOT_EXPANDABLE 
    };

    protected static class ExpressionToExpand {
        String resultSoFar;
        String todo;
        String operatorStack;
        public ExpressionToExpand(String resultSoFar, String todo, String operatorStack) {
            super();
            this.resultSoFar = resultSoFar;
            this.todo = todo;
            this.operatorStack = operatorStack;
        }
        @Override
        public String toString() {
            return "ExpressionToExpand["+todo+":"+resultSoFar+"/"+operatorStack+"]";
        }
    }
    /** returns a list with no curly braces in any entries; e.g. given a{,b} gives a and ab; 
     * quotes and parentheses are kept, but their contents may be excluded from expansion or otherwise treated specially as per the flag.
     * with allowNumericRanges, "{1-3}" is permitted for {1,2,3}. */
    public static List<String> getGlobsAfterBraceExpansion(String pattern, boolean allowNumericRanges, PhraseTreatment quoteTreatment, PhraseTreatment parenthesesTreatment) throws InvalidPatternException {
        List<ExpressionToExpand> patterns = new ArrayList<ExpressionToExpand>();
        List<String> result = new ArrayList<String>();
        patterns.add(new ExpressionToExpand("", pattern, ""));
        while (!patterns.isEmpty()) {
            ExpressionToExpand cs = patterns.remove(0);
            StringBuffer resultSoFar = new StringBuffer(cs.resultSoFar);
            String operatorStack = cs.operatorStack;
            boolean inQuote = operatorStack.contains("\"");
            boolean expanded = false;
            for (int i=0; i<cs.todo.length(); i++) {
                assert !expanded;
                char c = cs.todo.charAt(i);
                boolean inParen = operatorStack.contains("(") &&
                    (!inQuote || operatorStack.lastIndexOf('\"')<operatorStack.lastIndexOf('('));
                if (inQuote && !(inParen && parenthesesTreatment.equals(PhraseTreatment.INTERIOR_NOT_EXPANDABLE))) {
                    if (c=='"') {
                        if (i>0 && cs.todo.charAt(i-1)=='\\') {
                            //this escaped quote, keep
                            resultSoFar.append(c);
                            continue;
                        }
                        //unquote
                        resultSoFar.append(c);
                        inQuote = false;
                        if (operatorStack.charAt(operatorStack.length()-1)!='\"')
                            throw new InvalidPatternException("Quoted string contents not valid, after parsing "+resultSoFar);
                        operatorStack = operatorStack.substring(0, operatorStack.length()-1);
                        continue;
                    }
                    if (quoteTreatment.equals(PhraseTreatment.INTERIOR_NOT_EXPANDABLE)) {
                        resultSoFar.append(c);
                        continue;
                    }
                    //interior is expandable, continue parsing as usual below
                }
                if (inParen) {
                    if (c==')') {
                        //unparen
                        resultSoFar.append(c);
                        if (operatorStack.charAt(operatorStack.length()-1)!='(')
                            throw new InvalidPatternException("Parenthetical contents not valid, after parsing "+resultSoFar);
                        operatorStack = operatorStack.substring(0, operatorStack.length()-1);
                        continue;
                    }
                    if (parenthesesTreatment.equals(PhraseTreatment.INTERIOR_NOT_EXPANDABLE)) {
                        resultSoFar.append(c);
                        if (c=='(')
                            operatorStack+="(";
                        continue;
                    }
                    //interior is expandable, continue parsing as usual below
                }

                if (c=='"' && !quoteTreatment.equals(PhraseTreatment.NOT_A_SPECIAL_CHAR)) {
                    resultSoFar.append(c);
                    inQuote = true;
                    operatorStack += "\"";
                    continue;
                }
                if (c=='(' && !parenthesesTreatment.equals(PhraseTreatment.NOT_A_SPECIAL_CHAR)) {
                    resultSoFar.append(c);
                    operatorStack += "(";
                    continue;
                }

                if (c!='{') {
                    resultSoFar.append(c);
                    continue;
                }

                //brace.. we will need to expand
                expanded = true;
                String operatorStackBeforeExpansion = operatorStack;
                int braceStartIndex = i;
                int tokenStartIndex = i+1;

                //find matching close brace
                List<String> tokens = new ArrayList<String>();
                operatorStack += "{";
                while (true) {
                    if (++i>=cs.todo.length()) {
                        throw new InvalidPatternException("Curly brace not closed, parsing '"+cs.todo.substring(braceStartIndex)+"' after "+resultSoFar);
                    }
                    c = cs.todo.charAt(i);
                    inParen = operatorStack.contains("(") &&
                        (!inQuote || operatorStack.lastIndexOf('\"')<operatorStack.lastIndexOf('('));
                    if (inQuote && !(inParen && parenthesesTreatment.equals(PhraseTreatment.INTERIOR_NOT_EXPANDABLE))) {
                        if (c=='"') {
                            if (i>0 && cs.todo.charAt(i-1)=='\\') {
                                //this is escaped quote, doesn't affect status
                                continue;
                            }
                            //unquote
                            inQuote = false;
                            if (operatorStack.charAt(operatorStack.length()-1)!='\"')
                                throw new InvalidPatternException("Quoted string contents not valid, after parsing "+resultSoFar+cs.todo.substring(braceStartIndex, i));
                            operatorStack = operatorStack.substring(0, operatorStack.length()-1);
                            continue;
                        }
                        if (quoteTreatment.equals(PhraseTreatment.INTERIOR_NOT_EXPANDABLE)) {
                            continue;
                        }
                        //interior is expandable, continue parsing as usual below
                    }
                    if (inParen) {
                        if (c==')') {
                            //unparen
                            if (operatorStack.charAt(operatorStack.length()-1)!='(')
                                throw new InvalidPatternException("Parenthetical contents not valid, after parsing "+resultSoFar+cs.todo.substring(braceStartIndex, i));
                            operatorStack = operatorStack.substring(0, operatorStack.length()-1);
                            continue;
                        }
                        if (parenthesesTreatment.equals(PhraseTreatment.INTERIOR_NOT_EXPANDABLE)) {
                            if (c=='(')
                                operatorStack+="(";
                            continue;
                        }
                        //interior is expandable, continue parsing as usual below
                    }

                    if (c=='"' && !quoteTreatment.equals(PhraseTreatment.NOT_A_SPECIAL_CHAR)) {
                        inQuote = true;
                        operatorStack += "\"";
                        continue;
                    }
                    if (c=='(' && !parenthesesTreatment.equals(PhraseTreatment.NOT_A_SPECIAL_CHAR)) {
                        operatorStack += "(";
                        continue;
                    }

                    if (c=='}') {
                        if (operatorStack.charAt(operatorStack.length()-1)!='{')
                            throw new InvalidPatternException("Brace contents not valid, mismatched operators "+operatorStack+" after parsing "+resultSoFar+cs.todo.substring(braceStartIndex, i));
                        operatorStack = operatorStack.substring(0, operatorStack.length()-1);
                        if (operatorStack.equals(operatorStackBeforeExpansion)) {
                            tokens.add(cs.todo.substring(tokenStartIndex, i));
                            break;
                        }
                        continue;
                    }

                    if (c==',') {
                        if (operatorStack.length()==operatorStackBeforeExpansion.length()+1) {
                            tokens.add(cs.todo.substring(tokenStartIndex, i));
                            tokenStartIndex = i+1;
                            continue;
                        }
                        continue;
                    }

                    if (c=='{') {
                        operatorStack += c;
                        continue;
                    }

                    //any other char is irrelevant
                    continue;
                }

                assert operatorStack.equals(operatorStackBeforeExpansion);
                assert cs.todo.charAt(i)=='}';
                assert !tokens.isEmpty();

                String suffix = cs.todo.substring(i+1);

                List<ExpressionToExpand> newPatterns = new ArrayList<ExpressionToExpand>();
                for (String token : tokens) {
                    //System.out.println("adding: "+pre+token+post);
                    if (allowNumericRanges && token.matches("\\s*[0-9]+\\s*-\\s*[0-9]+\\s*")) {
                        int dashIndex = token.indexOf('-');
                        String startS = token.substring(0, dashIndex).trim();
                        String endS = token.substring(dashIndex+1).trim();

                        int start = Integer.parseInt(startS);
                        int end = Integer.parseInt(endS);

                        if (startS.startsWith("-")) startS=startS.substring(1).trim();
                        if (endS.startsWith("-")) endS=endS.substring(1).trim();
                        int minLen = Math.min(startS.length(), endS.length());

                        for (int ti=start; ti<=end; ti++) {
                            //partial support for negative numbers, but of course they cannot (yet) be specified in the regex above so it is moot
                            String tokenI = ""+Math.abs(ti);
                            while (tokenI.length()<minLen) tokenI = "0"+tokenI;
                            if (ti<0) tokenI = "-"+tokenI;
                            newPatterns.add(new ExpressionToExpand(resultSoFar.toString(), tokenI+suffix, operatorStackBeforeExpansion));                              
                        }
                    } else {
                        newPatterns.add(new ExpressionToExpand(resultSoFar.toString(), token+suffix, operatorStackBeforeExpansion));
                    }
                }
                // insert new patterns at the start, so we continue to expand them next
                patterns.addAll(0, newPatterns);
                
                break;
            }
            if (!expanded) {
                if (operatorStack.length()>0) {
                    throw new InvalidPatternException("Unclosed operators "+operatorStack+" parsing "+resultSoFar);
                }
                result.add(resultSoFar.toString());
            }
        }
        assert !result.isEmpty();
        return result;
    }

    public static class InvalidPatternException extends RuntimeException {
        private static final long serialVersionUID = -1969068264338310749L;
        public InvalidPatternException(String msg) {
            super(msg);
        }
    }

    
    /** expands globs as per #getGlobsAfterBraceExpansion, 
     * but also handles numeric ranges, 
     * and optionally allows customized treatment of quoted regions and/or parentheses.
     * <p>
     * simple example:  machine-{0-3}-{a,b} returns 8 values,
     * machine-0-a machine-0-b machine-1-a ... machine-3-b; 
     * NB leading zeroes are meaningful, so {00-03} expands as 00, 01, 02, 03
     * <p>
     * quote INTERIOR_NOT_EXPANDABLE example: a{b,"c,d"} return ab ac,d
     * <p>
     * for more detail on special treatment of quote and parentheses see PhraseTreatment and WildcardGlobsTest 
     */
    public static class SpecialistGlobExpander {

        private boolean expandNumericRanges;
        private PhraseTreatment quoteTreatment;
        private PhraseTreatment parenthesesTreatment;

        public SpecialistGlobExpander(boolean expandNumericRanges, PhraseTreatment quoteTreatment, PhraseTreatment parenthesesTreatment) {
            this.expandNumericRanges = expandNumericRanges;
            this.quoteTreatment = quoteTreatment;
            this.parenthesesTreatment = parenthesesTreatment;
        }
        /** expands glob, including custom syntax for numeric part */ 
        public List<String> expand(String glob) throws InvalidPatternException {
            return getGlobsAfterBraceExpansion(glob, expandNumericRanges, quoteTreatment, parenthesesTreatment);
        }
        
        /** returns true iff the target matches the given pattern,
         * under simplified bash rules -- viz permitting * and ? and comma delimited patterns inside curly braces,
         * as well as things like {1,2,5-10} (and also {01,02,05-10} to keep leading 0)
         * @throws InvalidPatternException */
        public boolean isGlobMatchedNumeric(String globPattern, String targetText) throws InvalidPatternException {
            List<String> patterns = expand(globPattern);        
            for (String p : patterns) {
                if (isNoBraceGlobMatched(p, targetText))
                    return true;
            }
            return false;
        }

        /** expands glob, including custom syntax for numeric part, but to an array, and re-throwing the checked exception as a runtime exception */
        public String[] expandToArrayUnchecked(String glob) {
            try {
                return expand(glob).toArray(new String[0]);
            } catch (InvalidPatternException e) {
                throw Throwables.propagate(e);
            }
        }

        
    }

}
