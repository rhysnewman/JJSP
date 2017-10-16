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
package jjsp.http;

import java.io.*;
import java.nio.charset.*;
import java.net.*;
import java.util.*;
import java.text.*;
import java.security.*;

import jjsp.util.*;

public class HTTPUtils
{
    public static final Charset ASCII = Charset.forName("US-ASCII");
    public static final Charset UTF8 = Charset.forName("UTF-8");

    private AES aes;
    private String aesPassword;
    private MessageDigest md5;
    private MessageDigest sha256;
    private DateFormat httpDateFormat;
    private long lastFormattedTime;
    private String lastFormat;

    private static String cachedHostName = "localhost";
    static
    {
        try
        {
            cachedHostName = InetAddress.getLocalHost().getCanonicalHostName();
        }
        catch (Throwable e)
        {
            try
            {
                cachedHostName = InetAddress.getLocalHost().getHostAddress();
            }
            catch (Throwable ee) {}
        }
    }

    static class LocalUtils extends ThreadLocal
    {
        protected Object initialValue()
        {
            return new HTTPUtils();
        }
    }

    private static final ThreadLocal localUtils = new LocalUtils();

    public static HTTPUtils getUtils()
    {
        return (HTTPUtils) localUtils.get();
    }

    public HTTPUtils()
    {
        httpDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", DateFormatSymbols.getInstance(Locale.US));

        try
        {
            httpDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        }
        catch (Exception e) {}

        lastFormattedTime = System.currentTimeMillis();
        lastFormat = httpDateFormat.format(lastFormattedTime);

        try
        {
            md5 = MessageDigest.getInstance("md5");
        }
        catch (Exception e) {}

        try
        {
            sha256 = MessageDigest.getInstance("SHA-256");
        }
        catch (Exception e) {}

        aesPassword = null;
        aes = null;
    }

    public AES getAES(String password) throws Exception
    {
        if ((aes == null) || !password.equals(aesPassword))
        {
            aesPassword = password;
            aes = AES.fromPassword(password);
        }
        return aes;
    }

    public MessageDigest getSHA256()
    {
        return sha256;
    }

    public byte[] SHA256(byte[] input) throws Exception
    {
        return sha256.digest(input);
    }

    public String SHA256(String in)  throws Exception
    {
        return Utils.toHexString(SHA256(Utils.getAsciiBytes(in)));
    }

    public String createETag(byte[] rawData)
    {
        if (rawData == null)
            return null;
        try
        {
            md5.reset();
            md5.update(rawData);
            return Base64.getUrlEncoder().encodeToString(md5.digest());
        }
        catch (Exception e) {}

        return "NOMD5_"+System.currentTimeMillis();
    }

    public static String getThisHostName()
    {
        return cachedHostName;
    }

    public String formatHTTPDate(long time)
    {
        if (time < lastFormattedTime)
            return httpDateFormat.format(time);
        else if (time < lastFormattedTime + 500)
            return lastFormat;

        lastFormat = httpDateFormat.format(time);
        lastFormattedTime = time;
        return lastFormat;
    }

    public String formatHTTPDate()
    {
        return formatHTTPDate(System.currentTimeMillis());
    }

    public long parseHTTPDate(String dateString, long defaultValue)
    {
        try
        {
            return httpDateFormat.parse(dateString.trim()).getTime();
        }
        catch (Exception e)
        {
            return defaultValue;
        }
    }
}
