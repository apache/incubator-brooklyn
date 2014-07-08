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
package brooklyn.util.math;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.primitives.Bytes;

/** represents an immutable ordered collection of bits with a known length
 * <p>
 * when converting to and from bytes and larger numbers, this representation 
 * uses the least-significant first convention both for bits and for bytes (little endian)
 * <p> 
 * (i.e. least significant byte is first, as is the least significant bit;
 * ninth element in this list is the least significant bit in the second byte,
 * so a list {0,0,0,0,0,0,0,0,1,0} represents 256)
 **/
public class BitList {

    private final BitSet bits;
    protected final int length;
    
    protected BitList(BitSet bits, int length) {
        assert length >= bits.length();
        this.bits = bits;
        this.length = length;
    }
    
    public static BitList newInstance(BitSet bits, int length) {
        return new BitList(bits, length);
    }
    
    public int length() {
        return length;
    }

    public boolean get(int index) {
        if (index<0 || index>=length)
            throw new IndexOutOfBoundsException("Index "+index+" in "+this);
        return bits.get(index);
    }
    
    public static BitList newInstance(byte ...bytes) {
        BitSet bits = new BitSet();
        for (int i=0; i < bytes.length*8; i++)
            if ((bytes[i/8] & (1 << (i%8))) > 0)
                bits.set(i);
        return newInstance(bits, bytes.length*8);
    }

    /** as {@link #newInstance(byte...)}, but accepting ints for convenience; 
     * only the least significant 8 bits of the parameters are considered */
    public static BitList newInstanceFromBytes(int ...bytes) {
        BitSet bits = new BitSet();
        for (int i=0; i < bytes.length*8; i++)
            if ((bytes[i/8] & (1 << (i%8))) > 0)
                bits.set(i);
        return newInstance(bits, bytes.length*8);
    }
    
    public static BitList newInstance(List<Boolean> l) {
        BitSet bs = new BitSet();
        for (int i=0; i<l.size(); i++)
            bs.set(i, l.get(i));
        return new BitList(bs, l.size());
    }

    public static BitList newInstance(boolean ...l) {
        BitSet bs = new BitSet();
        for (int i=0; i<l.length; i++)
            bs.set(i, l[i]);
        return new BitList(bs, l.length);
    }
    
    public static BitList newInstance(BigInteger x) {
        BitSet bs = new BitSet();
        for (int i=0; i<x.bitLength(); i++)
            if (x.testBit(i)) bs.set(i);
        return new BitList(bs, x.bitLength());
    }

    /**
     * returns the bits converted to bytes, with least significant bit first
     * *and* first 8 bits in the first byte  
     * <p> 
     * NB this may be different to BitSet.valueOf available since java 7 (as late as that!)
     * which reverses the order of the bytes */
    public byte[] asBytes() {
        byte[] bytes = new byte[(length+7)/8];
        for (int i=0; i<bits.length(); i++)
            if (bits.get(i))
                bytes[i/8] |= 1 << (i%8);
        return bytes;
    }

    public int[] asUnsignedBytes() {
        int[] bytes = new int[(length+7)/8];
        for (int i=0; i<bits.length(); i++)
            if (bits.get(i))
                bytes[i/8] |= 1 << (i%8);
        return bytes;
    }

    /** nb: BitSet forgets the length */
    public BitSet asBitSet() {
        return (BitSet) bits.clone();
    }

    public List<Boolean> asList() {
        List<Boolean> list = new ArrayList<Boolean>();
        for (int i=0; i<length(); i++) {
            list.add(get(i));
        }
        return list;
    }
    
    /** represents the result of this bit list logically ORred with the other */ 
    public BitList orred(BitList other) {
        BitSet result = asBitSet();
        result.or(other.asBitSet());
        return new BitList(result, Math.max(length, other.length));
    }

    /** represents the result of this bit list logically ANDed with the other */ 
    public BitList anded(BitList other) {
        BitSet result = asBitSet();
        result.and(other.asBitSet());
        return new BitList(result, Math.max(length, other.length));
    }

    /** represents the result of this bit list logically XORred with the other */ 
    public BitList xorred(BitList other) {
        BitSet result = asBitSet();
        result.xor(other.asBitSet());
        return new BitList(result, Math.max(length, other.length));
    }

    /** represents the result of this bit list logically notted */ 
    public BitList notted() {
        BitSet result = asBitSet();
        result.flip(0, length);
        return new BitList(result, length);
    }

    /** creates a new instance with the given length, either reducing the list or padding it with 0's 
     * (at the end, in both cases) 
     */
    public BitList resized(int length) {
        BitSet b2 = asBitSet();
        if (b2.length()>length) 
            b2.clear(length, b2.length());
        return newInstance(b2, length);
    }

    public BitList reversed() {
        BitSet b = new BitSet();
        for (int from=bits.length()-1, to=length-bits.length(); from>=0; from--, to++) {
            if (get(from)) b.set(to);
        }
        return new BitList(b, length);
    }

    public int commonPrefixLength(BitList other) {
        int i=0;
        while (i<length && i<other.length) {
            if (get(i)!=other.get(i)) return i;
            i++;
        }
        return i;
    }

    /** true iff the length is 0; see also isZero */
    public boolean isEmpty() {
        return length==0;
    }

    /** true iff all bits are 0 */
    public boolean isZero() {
        return bits.cardinality()==0;
    }

    public BigInteger asBigInteger() {
        if (length==0) return BigInteger.ZERO;
        return new BigInteger(Bytes.toArray(Lists.reverse(asByteList())));
    }

    public boolean[] asArray() {
        boolean[] result = new boolean[length];
        for (int i=0; i<length; i++)
            result[i] = get(i);
        return result;
    }
    
    public List<Byte> asByteList() {
        return Bytes.asList(asBytes());
    }

    /** returns value of this as a byte(ignoring any too-high bits) */
    public byte byteValue() {
        return asBigInteger().byteValue();
    }

    /** returns value of this as an integer (ignoring any too-high bits) */
    public int intValue() {
        return asBigInteger().intValue();
    }
    
    /** returns value of this as a long (ignoring any too-high bits) */
    public long longValue() {
        return asBigInteger().longValue();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((bits == null) ? 0 : bits.hashCode());
        result = prime * result + length;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BitList other = (BitList) obj;
        if (bits == null) {
            if (other.bits != null)
                return false;
        } else if (!bits.equals(other.bits))
            return false;
        if (length != other.length)
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<length; i++) {
            if (i%8==0 && i>0) sb.append(":"); //for readability
            sb.append(get(i) ? '1' : '0');
        }
        return sb.toString();
    }

}
