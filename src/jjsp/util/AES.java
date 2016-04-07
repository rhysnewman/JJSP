/*
JJSP - Java and Javascript Server Pages 
Copyright (C) 2016 Global Travel Ventures Ltd

This program is free software: you can redistribute it and/or modify 
it under the terms of the GNU General Public License as published by 
the Free Software Foundation, either version 3 of the License, or 
(at your option) any later version.

This program is distributed in the hope that it will be useful, but 
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
for more details.

You should have received a copy of the GNU General Public License along with 
this program. If not, see http://www.gnu.org/licenses/.
*/
package jjsp.util;

import java.io.*;
import java.util.*;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.security.spec.*;

public class AES
{
//    public static final String MODE = "AES/ECB/NoPadding"; // INSECURE, don't use ECB
//    public static final String MODE = "AES/CBC/NoPadding";
//    public static final String MODE = "AES/CFB/NoPadding";

    public static final String ALGORITHM = "AES";
    public static final String TYPE = "AES/CBC/PKCS5Padding";
    public static final int BITS = 128;
    public static final int IV_SIZE = 16;

    private final SecretKey key;
    private final Mac sha256_HMAC;

    private final SecretKeySpec keySpec;
    private final Cipher encrypter, decrypter;

    private static final byte[] SALT = Utils.getAsciiBytes("9afbiqm(*^wudg46");
    private static SecureRandom random = new SecureRandom();

    public synchronized static byte[] randomBytes(int length)
    {
        byte[] res = new byte[length];
        random.nextBytes(res);
        return res;
    }

    public synchronized static char[] randomPassword(int length)
    {
        char[] result = new char[length];
        for (int i=0; i<length; i++)
            result[i] = (char) (random.nextInt(126-33)+33);

        return result;
    }

    public AES() throws Exception
    {
        this((SecretKey) null);
    }

    public AES(byte[] encoded) throws Exception
    {
        this(new SecretKeySpec(encoded, ALGORITHM));
    }

    public AES(SecretKey key) throws Exception
    {
        if (key == null)
        {
            KeyGenerator kg = KeyGenerator.getInstance(ALGORITHM);
            kg.init(BITS);
            key = kg.generateKey();
        }
        this.key = key;

        keySpec = new SecretKeySpec(key.getEncoded(), ALGORITHM);
        encrypter = Cipher.getInstance(TYPE);
        decrypter = Cipher.getInstance(TYPE);

        sha256_HMAC = Mac.getInstance("HmacSHA256");
        sha256_HMAC.init(key);
    }

    public SecretKey getKey()
    {
        return key;
    }

    public byte[] encrypt(byte[] data) throws Exception
    {
        byte[] iv = randomBytes(IV_SIZE);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        
        byte[] enc = null;
        synchronized (encrypter)
        {
            encrypter.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            enc = encrypter.doFinal(data);
        }

        return prependHMAC(concat(iv, enc));
    }

    public byte[] decrypt(byte[] input) throws Exception
    {
        input = verifyAndRemoveHMAC(input);
        byte[] iv = new byte[IV_SIZE];
        System.arraycopy(input, 0, iv, 0, iv.length);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        byte[] cipher = new byte[input.length - IV_SIZE];
        System.arraycopy(input, IV_SIZE, cipher, 0, input.length-IV_SIZE);

        synchronized (decrypter)
        {
            decrypter.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            return decrypter.doFinal(cipher);
        }
    }

    public String encrypt(String in) throws Exception 
    {
        return Utils.toHexString(encrypt(Utils.getAsciiBytes(in)));
    }

    public String decrypt(String cipher) throws Exception 
    {
        return Utils.toString(decrypt(Utils.fromHexString(cipher)));
    }

    private byte[] prependHMAC(byte[] input) throws Exception 
    {
        byte[] mac_data = null;

        synchronized (sha256_HMAC)
        {
            sha256_HMAC.reset();
            sha256_HMAC.init(key);
            mac_data = sha256_HMAC.doFinal(input);
        }

        return concat(mac_data, input);
    }

    private byte[] verifyAndRemoveHMAC(byte[] input) throws Exception 
    {
        byte[] mac_data = null;
        byte[] cipher = Arrays.copyOfRange(input, 32, input.length);
            
        synchronized (sha256_HMAC)
        {
            sha256_HMAC.reset();
            mac_data = sha256_HMAC.doFinal(cipher);
        }

        if (!Arrays.equals(mac_data, Arrays.copyOfRange(input, 0, 32)))
            throw new IllegalStateException("Incorrect HMAC");
        return cipher;
    }

    public static byte[] concat(byte[] first, byte[] second) {
        byte[] res = new byte[first.length+ second.length];
        System.arraycopy(first, 0, res, 0, first.length);
        System.arraycopy(second, 0, res, first.length, second.length);
        return res;
    }

    public static AES fromRandom() throws Exception
    {
        return new AES();
    }

    public static AES fromPassword(String pass) throws Exception {
        return fromPassword(pass.toCharArray());
    }

    public static AES fromPassword(char[] pass) throws Exception
    {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(pass, SALT, 65536, BITS);

        SecretKey tmp = factory.generateSecret(spec);
        SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");

        return new AES(secret);
    }

    public static void enumerateAllCryptoAlgorithmsAvailable() throws Exception
    {
        Provider p[] = Security.getProviders();
        for (int i = 0; i < p.length; i++) 
        {
            System.out.println(p[i]);
            for (Enumeration e = p[i].keys(); e.hasMoreElements();)
                System.out.println("\t" + e.nextElement());
        }
    }

    public static void test(String[] args) throws Exception
    {
        //enumerateAllCryptoAlgorithmsAvailable();

        AES key = fromPassword("pass".toCharArray());
        byte[] data = "Some clear text".getBytes();
        byte[] ciphertext = key.encrypt(data);

        Base64.Encoder b64 = Base64.getEncoder();
        System.out.println(b64.encodeToString(ciphertext));

        byte[] ciphertext2 = key.encrypt(data);
        System.out.println(b64.encodeToString(ciphertext2));

        AES other = fromPassword("pass".toCharArray());
        byte[] decrypted = other.decrypt(ciphertext);
        System.out.println(new String(decrypted));

        byte val = (byte)(Math.random()*256);
        int index = (int)(Math.random()* ciphertext.length);
        ciphertext[index] = (ciphertext[index] == val) ? (byte)(val+1): val;
        System.out.println(new String(other.decrypt(ciphertext)));
    }
    
    public static String getPassword(File pwFile) throws Exception
    {
        return getPassword(Utils.loadText(pwFile));
    }
    
    public static String getPassword(String base64EncodedPassword) throws Exception
    {
        Base64.Decoder b64 = Base64.getDecoder();
        byte[] raw = b64.decode(base64EncodedPassword);
        
        String s1 = "9458asdio23890asdfdsfgss";
        s1 = s1.replace("a", "3");

        AES aes = fromPassword(s1.toCharArray());
        byte[] decrypted = aes.decrypt(raw);

        return Utils.toString(decrypted);
    }

}
