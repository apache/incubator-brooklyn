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
package org.apache.brooklyn.util.yaml;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.apache.brooklyn.util.collections.Jsonya;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.UserFacingException;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.yaml.Yamls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import com.google.common.annotations.Beta;
import com.google.common.collect.Iterables;

public class Yamls {

    private static final Logger log = LoggerFactory.getLogger(Yamls.class);

    /** returns the given (yaml-parsed) object as the given yaml type.
     * <p>
     * if the object is an iterable or iterator this method will fully expand it as a list. 
     * if the requested type is not an iterable or iterator, and the list contains a single item, this will take that single item.
     * <p>
     * in other cases this method simply does a type-check and cast (no other type coercion).
     * <p>
     * @throws IllegalArgumentException if the input is an iterable not containing a single element,
     *   and the cast is requested to a non-iterable type 
     * @throws ClassCastException if cannot be casted */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> T getAs(Object x, Class<T> type) {
        if (x==null) return null;
        if (x instanceof Iterable || x instanceof Iterator) {
            List result = new ArrayList();
            Iterator xi;
            if (Iterator.class.isAssignableFrom(x.getClass())) {
                xi = (Iterator)x;
            } else {
                xi = ((Iterable)x).iterator();
            }
            while (xi.hasNext()) {
                result.add( xi.next() );
            }
            if (type.isAssignableFrom(List.class)) return (T)result;
            if (type.isAssignableFrom(Iterator.class)) return (T)result.iterator();
            x = Iterables.getOnlyElement(result);
        }
        if (type.isInstance(x)) return (T)x;
        throw new ClassCastException("Cannot convert "+x+" ("+x.getClass()+") to "+type);
    }

    /**
     * Parses the given yaml, and walks the given path to return the referenced object.
     * 
     * @see #getAt(Object, List)
     */
    @Beta
    public static Object getAt(String yaml, List<String> path) {
        Iterable<Object> result = new org.yaml.snakeyaml.Yaml().loadAll(yaml);
        Object current = result.iterator().next();
        return getAtPreParsed(current, path);
    }
    
    /** 
     * For pre-parsed yaml, walks the maps/lists to return the given sub-item.
     * In the given path:
     * <ul>
     *   <li>A vanilla string is assumed to be a key into a map.
     *   <li>A string in the form like "[0]" is assumed to be an index into a list
     * </ul>
     * 
     * Also see {@link Jsonya}, such as {@code Jsonya.of(current).at(path).get()}.
     * 
     * @return The object at the given path, or {@code null} if that path does not exist.
     */
    @Beta
    @SuppressWarnings("unchecked")
    public static Object getAtPreParsed(Object current, List<String> path) {
        for (String pathPart : path) {
            if (pathPart.startsWith("[") && pathPart.endsWith("]")) {
                String index = pathPart.substring(1, pathPart.length()-1);
                try {
                    current = Iterables.get((Iterable<?>)current, Integer.parseInt(index));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid index '"+index+"', in path "+path);
                } catch (IndexOutOfBoundsException e) {
                    throw new IllegalArgumentException("Invalid index '"+index+"', in path "+path);
                }
            } else {
                current = ((Map<String, ?>)current).get(pathPart);
            }
            if (current == null) return null;
        }
        return current;
    }

    @SuppressWarnings("rawtypes")
    public static void dump(int depth, Object r) {
        if (r instanceof Iterable) {
            for (Object ri : ((Iterable)r))
                dump(depth+1, ri);
        } else if (r instanceof Map) {
            for (Object re: ((Map)r).entrySet()) {
                for (int i=0; i<depth; i++) System.out.print(" ");
                System.out.println(((Entry)re).getKey()+":");
                dump(depth+1, ((Entry)re).getValue());
            }
        } else {
            for (int i=0; i<depth; i++) System.out.print(" ");
            if (r==null) System.out.println("<null>");
            else System.out.println("<"+r.getClass().getSimpleName()+">"+" "+r);
        }
    }

    /** simplifies new Yaml().loadAll, and converts to list to prevent single-use iterable bug in yaml */
    @SuppressWarnings("unchecked")
    public static Iterable<Object> parseAll(String yaml) {
        Iterable<Object> result = new org.yaml.snakeyaml.Yaml().loadAll(yaml);
        return (List<Object>) getAs(result, List.class);
    }

    /** as {@link #parseAll(String)} */
    @SuppressWarnings("unchecked")
    public static Iterable<Object> parseAll(Reader yaml) {
        Iterable<Object> result = new org.yaml.snakeyaml.Yaml().loadAll(yaml);
        return (List<Object>) getAs(result, List.class);
    }

    public static Object removeMultinameAttribute(Map<String,Object> obj, String ...equivalentNames) {
        Object result = null;
        for (String name: equivalentNames) {
            Object candidate = obj.remove(name);
            if (candidate!=null) {
                if (result==null) result = candidate;
                else if (!result.equals(candidate)) {
                    log.warn("Different values for attributes "+Arrays.toString(equivalentNames)+"; " +
                            "preferring '"+result+"' to '"+candidate+"'");
                }
            }
        }
        return result;
    }

    public static Object getMultinameAttribute(Map<String,Object> obj, String ...equivalentNames) {
        Object result = null;
        for (String name: equivalentNames) {
            Object candidate = obj.get(name);
            if (candidate!=null) {
                if (result==null) result = candidate;
                else if (!result.equals(candidate)) {
                    log.warn("Different values for attributes "+Arrays.toString(equivalentNames)+"; " +
                            "preferring '"+result+"' to '"+candidate+"'");
                }
            }
        }
        return result;
    }
    
    @Beta
    public static class YamlExtract {
        String yaml;
        NodeTuple focusTuple;
        Node prev, key, focus, next;
        Exception error;
        boolean includeKey = false, includePrecedingComments = true, includeOriginalIndentation = false;
        
        private int indexStart(Node node, boolean defaultIsYamlEnd) {
            if (node==null) return defaultIsYamlEnd ? yaml.length() : 0;
            return index(node.getStartMark());
        }
        private int indexEnd(Node node, boolean defaultIsYamlEnd) {
            if (!found() || node==null) return defaultIsYamlEnd ? yaml.length() : 0;
            return index(node.getEndMark());
        }
        private int index(Mark mark) {
            try {
                return mark.getIndex();
            } catch (NoSuchMethodError e) {
                try {
                    getClass().getClassLoader().loadClass("org.testng.TestNG");
                } catch (ClassNotFoundException e1) {
                    // not using TestNG
                    Exceptions.propagateIfFatal(e1);
                    throw e;
                }
                if (!LOGGED_TESTNG_WARNING.getAndSet(true)) {
                    log.warn("Detected TestNG/SnakeYAML version incompatibilities: "
                        + "some YAML source reconstruction will be unavailable. "
                        + "This can happen with TestNG plugins which force an older version of SnakeYAML "
                        + "which does not support Mark.getIndex. "
                        + "It should not occur from maven CLI runs. "
                        + "(Subsequent occurrences will be silently dropped, and source code reconstructed from YAML.)");
                }
                // using TestNG
                throw new KnownClassVersionException(e);
            }
        }
        
        static AtomicBoolean LOGGED_TESTNG_WARNING = new AtomicBoolean();
        static class KnownClassVersionException extends IllegalStateException {
            private static final long serialVersionUID = -1620847775786753301L;
            public KnownClassVersionException(Throwable e) {
                super("Class version error. This can happen if using a TestNG plugin in your IDE "
                    + "which is an older version, dragging in an older version of SnakeYAML which does not support Mark.getIndex.", e);
            }
        }

        public int getEndOfPrevious() {
            return indexEnd(prev, false);
        }
        @Nullable public Node getKey() {
            return key;
        }
        public Node getResult() {
            return focus;
        }
        public int getStartOfThis() {
            if (includeKey && focusTuple!=null) return indexStart(focusTuple.getKeyNode(), false);
            return indexStart(focus, false);
        }
        private int getStartColumnOfThis() {
            if (includeKey && focusTuple!=null) return focusTuple.getKeyNode().getStartMark().getColumn();
            return focus.getStartMark().getColumn();
        }
        public int getEndOfThis() {
            return getEndOfThis(false);
        }
        private int getEndOfThis(boolean goToEndIfNoNext) {
            if (next==null && goToEndIfNoNext) return yaml.length();
            return indexEnd(focus, false);
        }
        public int getStartOfNext() {
            return indexStart(next, true);
        }

        private static int initialWhitespaceLength(String x) {
            int i=0;
            while (i < x.length() && x.charAt(i)==' ') i++;
            return i;
        }
        
        public String getFullYamlTextOriginal() {
            return yaml;
        }

        /** Returns the original YAML with the found item replaced by the given replacement YAML.
         * @param replacement YAML to put in for the found item;
         * this YAML typically should not have any special indentation -- if required when replacing it will be inserted.
         * <p>
         * if replacing an inline map entry, the supplied entry must follow the structure being replaced;
         * for example, if replacing the value in <code>key: value</code> with a map,
         * supplying a replacement <code>subkey: value</code> would result in invalid yaml;
         * the replacement must be supplied with a newline, either before the subkey or after.
         * (if unsure we believe it is always valid to include an initial newline or comment with newline.)
         */
        public String getFullYamlTextWithExtractReplaced(String replacement) {
            if (!found()) throw new IllegalStateException("Cannot perform replacement when item was not matched.");
            String result = yaml.substring(0, getStartOfThis());
            
            String[] newLines = replacement.split("\n");
            for (int i=1; i<newLines.length; i++)
                newLines[i] = Strings.makePaddedString("", getStartColumnOfThis(), "", " ") + newLines[i];
            result += Strings.lines(newLines);
            if (replacement.endsWith("\n")) result += "\n";
            
            int end = getEndOfThis();
            result += yaml.substring(end);
            
            return result;
        }

        /** Specifies whether the key should be included in the found text, 
         * when calling {@link #getMatchedYamlText()} or {@link #getFullYamlTextWithExtractReplaced(String)},
         * if the found item is a map entry.
         * Defaults to false.
         * @return this object, for use in a fluent constructions
         */
        public YamlExtract withKeyIncluded(boolean includeKey) {
            this.includeKey = includeKey;
            return this;
        }

        /** Specifies whether comments preceding the found item should be included, 
         * when calling {@link #getMatchedYamlText()} or {@link #getFullYamlTextWithExtractReplaced(String)}.
         * This will not include comments which are indented further than the item,
         * as those will typically be aligned with the previous item
         * (whereas comments whose indentation is the same or less than the found item
         * will typically be aligned with this item).
         * Defaults to true.
         * @return this object, for use in a fluent constructions
         */
        public YamlExtract withPrecedingCommentsIncluded(boolean includePrecedingComments) {
            this.includePrecedingComments = includePrecedingComments;
            return this;
        }

        /** Specifies whether the original indentation should be preserved
         * (and in the case of the first line, whether whitespace should be inserted so its start column is preserved), 
         * when calling {@link #getMatchedYamlText()}.
         * Defaults to false, the returned text will be outdented as far as possible.
         * @return this object, for use in a fluent constructions
         */
        public YamlExtract withOriginalIndentation(boolean includeOriginalIndentation) {
            this.includeOriginalIndentation = includeOriginalIndentation;
            return this;
        }

        @Beta
        public String getMatchedYamlTextOrWarn() {
            try {
                return getMatchedYamlText();
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                if (e instanceof KnownClassVersionException) {
                    log.debug("Known class version exception; no yaml text being matched for "+this+": "+e);
                } else {
                    if (e instanceof UserFacingException) {
                        log.warn("Unable to match yaml text in "+this+": "+e.getMessage());
                    } else {
                        log.warn("Unable to match yaml text in "+this+": "+e, e);
                    }
                }
                return null;
            }
        }
        
        @Beta
        public String getMatchedYamlText() {
            if (!found()) return null;
            
            String[] body = yaml.substring(getStartOfThis(), getEndOfThis(true)).split("\n", -1);
            
            int firstLineIndentationOfFirstThing;
            if (focusTuple!=null) {
                firstLineIndentationOfFirstThing = focusTuple.getKeyNode().getStartMark().getColumn();
            } else {
                firstLineIndentationOfFirstThing = focus.getStartMark().getColumn();
            }
            int firstLineIndentationToAdd;
            if (focusTuple!=null && (includeKey || body.length==1)) {
                firstLineIndentationToAdd = focusTuple.getKeyNode().getStartMark().getColumn();
            } else {
                firstLineIndentationToAdd = focus.getStartMark().getColumn();
            }
            
            
            String firstLineIndentationToAddS = Strings.makePaddedString("", firstLineIndentationToAdd, "", " ");
            String subsequentLineIndentationToRemoveS = firstLineIndentationToAddS;

/* complexities of indentation:

x: a
 bc
 
should become

a
 bc

whereas

- a: 0
  b: 1
  
selecting 0 should give

a: 0
b: 1

 */
            List<String> result = MutableList.of();
            if (includePrecedingComments) {
                if (getEndOfPrevious() > getStartOfThis()) {
                    throw new UserFacingException("YAML not in expected format; when scanning, previous end "+getEndOfPrevious()+" exceeds this start "+getStartOfThis());
                }
                String[] preceding = yaml.substring(getEndOfPrevious(), getStartOfThis()).split("\n");
                // suppress comments which are on the same line as the previous item or indented more than firstLineIndentation,
                // ensuring appropriate whitespace is added to preceding[0] if it starts mid-line
                if (preceding.length>0 && prev!=null) {
                    preceding[0] = Strings.makePaddedString("", prev.getEndMark().getColumn(), "", " ") + preceding[0];
                }
                for (String p: preceding) {
                    int w = initialWhitespaceLength(p);
                    p = p.substring(w);
                    if (p.startsWith("#")) {
                        // only add if the hash is not indented further than the first line
                        if (w <= firstLineIndentationOfFirstThing) {
                            if (includeOriginalIndentation) p = firstLineIndentationToAddS + p;
                            result.add(p);
                        }
                    }
                }
            }
            
            boolean doneFirst = false;
            for (String p: body) {
                if (!doneFirst) {
                    if (includeOriginalIndentation) {
                        // have to insert the right amount of spacing
                        p = firstLineIndentationToAddS + p;
                    }
                    result.add(p);
                    doneFirst = true;
                } else {
                    if (includeOriginalIndentation) {
                        result.add(p);
                    } else {
                        result.add(Strings.removeFromStart(p, subsequentLineIndentationToRemoveS));
                    }
                }
            }
            return Strings.join(result, "\n");
        }
        
        boolean found() {
            return focus != null;
        }
        
        public Exception getError() {
            return error;
        }
        
        @Override
        public String toString() {
            return "Extract["+focus+";prev="+prev+";key="+key+";next="+next+"]";
        }
    }
    
    private static void findTextOfYamlAtPath(YamlExtract result, int pathIndex, Object ...path) {
        if (pathIndex>=path.length) {
            // we're done
            return;
        }
        
        Object pathItem = path[pathIndex];
        Node node = result.focus;
        
        if (node.getNodeId()==NodeId.mapping && pathItem instanceof String) {
            // find key
            Iterator<NodeTuple> ti = ((MappingNode)node).getValue().iterator();
            while (ti.hasNext()) {
                NodeTuple t = ti.next();
                Node key = t.getKeyNode();
                if (key.getNodeId()==NodeId.scalar) {
                    if (pathItem.equals( ((ScalarNode)key).getValue() )) {
                        result.key = key;
                        result.focus = t.getValueNode();
                        if (pathIndex+1<path.length) {
                            // there are more path items, so the key here is a previous node
                            result.prev = key;
                        } else {
                            result.focusTuple = t;
                        }
                        findTextOfYamlAtPath(result, pathIndex+1, path);
                        if (result.next==null) {
                            if (ti.hasNext()) result.next = ti.next().getKeyNode();
                        }
                        return;
                    } else {
                        result.prev = t.getValueNode();
                    }
                } else {
                    throw new IllegalStateException("Key "+key+" is not a scalar, searching for "+pathItem+" at depth "+pathIndex+" of "+Arrays.asList(path));
                }
            }
            throw new IllegalStateException("Did not find "+pathItem+" in "+node+" at depth "+pathIndex+" of "+Arrays.asList(path));
            
        } else if (node.getNodeId()==NodeId.sequence && pathItem instanceof Number) {
            // find list item
            List<Node> nl = ((SequenceNode)node).getValue();
            int i = ((Number)pathItem).intValue();
            if (i>=nl.size()) 
                throw new IllegalStateException("Index "+i+" is out of bounds in "+node+", searching for "+pathItem+" at depth "+pathIndex+" of "+Arrays.asList(path));
            if (i>0) result.prev = nl.get(i-1);
            result.key = null;
            result.focus = nl.get(i);
            findTextOfYamlAtPath(result, pathIndex+1, path);
            if (result.next==null) {
                if (nl.size()>i+1) result.next = nl.get(i+1);
            }
            return;
            
        } else {
            throw new IllegalStateException("Node "+node+" does not match selector "+pathItem+" at depth "+pathIndex+" of "+Arrays.asList(path));
        }
        
        // unreachable
    }
    
    
    /** Given a path, where each segment consists of a string (key) or number (element in list),
     * this will find the YAML text for that element
     * <p>
     * If not found this will return a {@link YamlExtract} 
     * where {@link YamlExtract#isMatch()} is false and {@link YamlExtract#getError()} is set. */
    public static YamlExtract getTextOfYamlAtPath(String yaml, Object ...path) {
        YamlExtract result = new YamlExtract();
        if (yaml==null) return result;
        try {
            int pathIndex = 0;
            result.yaml = yaml;
            result.focus = new Yaml().compose(new StringReader(yaml));
    
            findTextOfYamlAtPath(result, pathIndex, path);
            return result;
        } catch (NoSuchMethodError e) {
            throw new IllegalStateException("Class version error. This can happen if using a TestNG plugin in your IDE "
                + "which is an older version, dragging in an older version of SnakeYAML which does not support Mark.getIndex.", e);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.debug("Unable to find element in yaml (setting in result): "+e);
            result.error = e;
            return result;
        }
    }
}
