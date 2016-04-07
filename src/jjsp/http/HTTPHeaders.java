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

public class HTTPHeaders 
{
    public static final int MAX_HEADERS = 128;
    public static final String MAIN_LINE = "Main-Line";

    protected String mainLine;
    protected LinkedHashMap headerMap;

    public HTTPHeaders()
    {
        headerMap = new LinkedHashMap();
    }

    public void clear()
    {
        mainLine = null;
        headerMap.clear();
    }

    public String getMainLine()
    {
        return mainLine;
    }

    public int getHeaderCount()
    {
        return headerMap.size();
    }

    public String[] getHeaderKeys()
    {
        String[] keys = new String[headerMap.size()];
        headerMap.keySet().toArray(keys);
        return keys;
    }

    public boolean hasHeader(String key)
    {
        return headerMap.containsKey(key);
    }

    public String getHeader(String key)
    {
        return getHeader(key, "");
    }

    public String getHeader(String key, String defaultValue)
    {
        if (key == null)
            return null;
        Object val = headerMap.get(key);
        if (val == null)
            return defaultValue;
        return (String) val;
    }

    public void setHeader(String key, String value)
    {
        if (key == null)
            return;
            
        if (value == null)
            headerMap.remove(key);
        else
        {
            if (headerMap.size() >= MAX_HEADERS)
                throw new IllegalStateException("Too many headers");
            headerMap.put(key, value);
        }
    }

    public boolean deleteHeader(String key)
    {
        if (key == null)
            return false;
        return headerMap.remove(key) != null;
    }

    protected void setHeaders(String[] keys, String[] values)
    {
        if (keys == null)
            return;
        if (keys.length != values.length)
            throw new IllegalStateException("Key array length != value array length");
        for (int i=0; i<keys.length; i++)
            setHeader(keys[i], values[i]);
    }

    public long getContentLength()
    {
        try
        {
            return Long.parseLong(getHeader("Content-Length", "-1"));
        }
        catch (Exception e) 
        {
            return -1;
        }
    }
    
    public boolean isHTTP11()
    {
        return mainLine.endsWith("HTTP/1.1") || mainLine.startsWith("HTTP/1.1");
    }

    public boolean isChunked()
    {
        return isHTTP11() && "chunked".equalsIgnoreCase(getHeader("Transfer-Encoding", "none"));
    }

    public boolean useCache()
    {
        return "no-cache".equalsIgnoreCase(getHeader("Cache-Control", "no-cache"));
    }

    public boolean closeConnection()
    {
        return !isHTTP11() || "close".equalsIgnoreCase(getHeader("Connection", "Keep-alive"));
    }

    public long[] extractByteRanges()
    {
        String spec = getHeader("Range", null);
        if (spec == null)
            return null;
        
        try
        {
            long start = 0, end=-1;
            int i1 = spec.indexOf("bytes=");
            if (i1 < 0)
                return null;
            i1 += 6;

            int i2 = spec.indexOf("-", i1);
            String startSpec = spec.substring(i1,i2).trim();
            if (startSpec.length() > 0)
                start = Long.parseLong(startSpec);
            
            if (i2+1 < spec.length())
            {
                String endSpec = spec.substring(i2+1).trim();
                end = Long.parseLong(endSpec);
            }

            if ((start > end) || (start < 0))
            {
                start = 0;
                end = -1;
            }
            return new long[]{start, end};
        }
        catch (Exception e) {}

        return null;
    }

    public String toString()
    {
        if (getMainLine() == null)
            return "[Empty HTTP Headers]";
        return getMainLine();
    }

    public void print()
    {
        print(System.out);
    }

    public void print(PrintStream ps)
    {
        ps.print(getMainLine());
        ps.print("\r\n");
        
        String[] keys = getHeaderKeys();
        for (int i=0; i<keys.length; i++)
        {
            String key = keys[i];
            Object value = headerMap.get(key);
            try
            {
                String sval = (String) value;
                ps.print(key);
                ps.print(": ");
                ps.print(sval);
                ps.print("\r\n");
            }
            catch (ClassCastException e)
            {
                List values = (List) value;
                for (int j=0; j<values.size(); j++)
                {
                    ps.print(key);
                    ps.print(": ");
                    ps.print(Utils.getAsciiBytes(HTTPResponseHeaders.formatSetCookie((HttpCookie)values.get(j))));
                    ps.print("\r\n");
                }
            }
        }
        ps.print("\r\n");
        ps.flush();
    }

    public static String guessMIMEType(String fileName)
    {
        return guessMIMEType(fileName, "application/octet-stream");
    }

    public static String guessMIMEType(String fileName, String defaultType)
    {
        fileName = fileName.toLowerCase();

        if (fileName.endsWith(".html") || fileName.endsWith(".htm") || fileName.endsWith("/") || (fileName.length() == 0))
            return "text/html; charset=utf-8";
        if (fileName.endsWith(".jpeg") || fileName.endsWith(".jpg"))
            return "image/jpeg";
        if (fileName.endsWith(".png"))
            return "image/png";
        if (fileName.endsWith(".svg"))
            return "image/svg+xml";
        if (fileName.endsWith(".gif"))
            return "image/gif";
        if (fileName.endsWith(".js"))
            return "application/javascript; charset=utf-8";
        if (fileName.endsWith(".json"))
            return "application/json; charset=utf-8";
        if (fileName.endsWith(".css"))
            return "text/css; charset=utf-8";
        if (fileName.endsWith(".class"))
            return "application/java-class";
        if (fileName.endsWith(".jar"))
            return "application/java-archive";
        if (fileName.endsWith(".zip"))
            return "application/zip";
        if (fileName.endsWith(".iso") || fileName.endsWith(".img"))
            return "application/octet-stream";

        if (fileName.endsWith(".tiff"))
            return "image/tiff";
        if (fileName.endsWith(".ico"))
            return "image/vnd.microsoft.icon";
        if (fileName.endsWith(".jnlp"))
            return "application/x-java-jnlp-file";
        if (fileName.endsWith(".txt") || fileName.endsWith(".java") || fileName.endsWith(".c") || fileName.endsWith(".cpp") || fileName.endsWith(".jet") || fileName.endsWith(".log") || fileName.equals("makefile") || fileName.endsWith("md") || fileName.endsWith(".jf"))
            return "text/plain; charset=utf-8";
        if (fileName.endsWith(".xml"))
            return "text/xml; charset=utf-8";
        if (fileName.endsWith(".wmv"))
            return "video/x-ms-wmv";
        if (fileName.endsWith(".xpi"))
            return "application/x-xpinstall";

        try
        {
            String result = URLConnection.getFileNameMap().getContentTypeFor(fileName);
            if ((result != null) && (result.length() > 0))
                return result;
        }
        catch (Exception e) {}

        return defaultType;
    }

    public Map toMap()
    {
        LinkedHashMap result = new LinkedHashMap();
        result.put(MAIN_LINE, mainLine);
        result.putAll(headerMap);

        Object sc = result.get("Set-Cookie");
        if ((sc != null) && (sc instanceof List))
        {
            List ll = (List) sc;
            result.remove("Set-Cookie");
            for (int i=0; i<ll.size(); i++)
            {
                HttpCookie cookie = (HttpCookie) ll.get(i);
                result.put("Set-Cookie:"+cookie.getName(), cookie.getValue());
            }
        }
        
        return result;
    }

    public static String escapeHTML(String src)
    {
        String result = src.replace("&", "&amp;");
        result = result.replace("<", "&lt;");
        result = result.replace(">", "&gt;");
        result = result.replace("\"", "&quot;");
        return result;
    }
}
