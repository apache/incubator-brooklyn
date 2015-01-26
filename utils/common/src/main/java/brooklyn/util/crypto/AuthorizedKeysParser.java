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
package brooklyn.util.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;

import brooklyn.util.exceptions.Exceptions;

import com.google.common.io.BaseEncoding;

public class AuthorizedKeysParser {

    public static PublicKey decodePublicKey(String keyLine) {
        try {
            ByteArrayInputStream stream = null;

            // look for the Base64 encoded part of the line to decode
            // both ssh-rsa and ssh-dss begin with "AAAA" due to the length bytes
            for (String part : keyLine.split(" ")) {
                if (part.startsWith("AAAA")) {
                    stream = new ByteArrayInputStream(BaseEncoding.base64().decode(part));
                    break;
                }
            }
            if (stream == null)
                throw new IllegalArgumentException("Encoded public key should include phrase beginning AAAA.");

            String type = readType(stream);
            if (type.equals("ssh-rsa")) {
                BigInteger e = readBigInt(stream, 1);
                BigInteger m = readBigInt(stream, 1);
                RSAPublicKeySpec spec = new RSAPublicKeySpec(m, e);
                return KeyFactory.getInstance("RSA").generatePublic(spec);
            } else if (type.equals("ssh-dss")) {
                BigInteger p = readBigInt(stream, 1);
                BigInteger q = readBigInt(stream, 1);
                BigInteger g = readBigInt(stream, 1);
                BigInteger y = readBigInt(stream, 1);
                DSAPublicKeySpec spec = new DSAPublicKeySpec(y, p, q, g);
                return KeyFactory.getInstance("DSA").generatePublic(spec);
            } else {
                throw new IllegalArgumentException("Unknown public key type " + type);
            }
            
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            throw new IllegalArgumentException("Error parsing authorized_keys/SSH2 format public key: "+e);
        }
    }

    private static int readInt(InputStream stream) throws IOException {
        return ((stream.read() & 0xFF) << 24) | ((stream.read() & 0xFF) << 16)
            | ((stream.read() & 0xFF) << 8) | (stream.read() & 0xFF);
    }
    
    private static byte[] readBytesWithLength(InputStream stream, int minLen) throws IOException {
        int len = readInt(stream);
        if (len<minLen || len>100000)
            throw new IllegalStateException("Invalid stream header: length "+len);
        byte[] result = new byte[len];
        stream.read(result);
        return result;
    }
    
    private static void writeInt(OutputStream stream, int v) throws IOException {
        for (int shift = 24; shift >= 0; shift -= 8)
            stream.write((v >>> shift) & 0xFF);
    }
    private static void writeBytesWithLength(OutputStream stream, byte[] buf) throws IOException {
        writeInt(stream, buf.length);
        stream.write(buf);
    }
    
    private static String readType(InputStream stream) throws IOException { return new String(readBytesWithLength(stream, 0)); }
    private static BigInteger readBigInt(InputStream stream, int minLen) throws IOException { return new BigInteger(readBytesWithLength(stream, minLen)); }

    public static String encodePublicKey(PublicKey key) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            String type = null;
            if (key==null) { 
                return null;
            } else if (key instanceof RSAPublicKey) {
                type = "ssh-rsa";
                writeBytesWithLength(out, type.getBytes()); 
                writeBytesWithLength(out, ((RSAPublicKey)key).getPublicExponent().toByteArray());
                writeBytesWithLength(out, ((RSAPublicKey)key).getModulus().toByteArray()); 
            } else if (key instanceof DSAPublicKey) {
                type = "ssh-dss";
                writeBytesWithLength(out, type.getBytes());
                writeBytesWithLength(out, ((DSAPublicKey)key).getParams().getP().toByteArray()); 
                writeBytesWithLength(out, ((DSAPublicKey)key).getParams().getQ().toByteArray()); 
                writeBytesWithLength(out, ((DSAPublicKey)key).getParams().getG().toByteArray()); 
                writeBytesWithLength(out, ((DSAPublicKey)key).getY().toByteArray()); 
            } else {
                throw new IllegalStateException("Unsupported public key type for encoding: "+key);
            }
            out.close();
            
            return type+" "+BaseEncoding.base64().encode(out.toByteArray());
        } catch (Exception e) {
            // shouldn't happen, as it's a byte stream...
            throw Exceptions.propagate(e);
        }
    }

}