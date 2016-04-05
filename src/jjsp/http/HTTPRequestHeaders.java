package jjsp.http;

import java.io.*;
import java.nio.charset.*;
import java.net.*;
import java.util.*;
import java.text.*;
import java.security.*;

import jjsp.util.*;

public class HTTPRequestHeaders extends HTTPHeaders
{
    public static final int DEFAULT_HEADER_LINE_LENGTH = 4*1024;
    
    private int pos;
    private String reqURL;
    private byte[] lineBuffer;
    private String clientIPAddress;
    private Map queryMap, cookieMap;
    private boolean queryParsed, cookiesParsed;

    public HTTPRequestHeaders()
    {
        this(DEFAULT_HEADER_LINE_LENGTH);
    }

    public HTTPRequestHeaders(int lineLength)
    {
        pos = 0;
        reqURL = null;
        lineBuffer = new byte[lineLength];
        queryMap = new HashMap();
        cookieMap = new HashMap();
        clientIPAddress = null;
    }

    public void clear()
    {
        super.clear();
        reqURL = null;
        pos = 0;
        queryParsed = false;
        cookiesParsed = false;
        queryMap.clear();
        cookieMap.clear();
        clientIPAddress = null;
    }

    private boolean readLine(InputStream src) throws IOException
    {
        int pos2 = readLine(src, lineBuffer);
        if (pos2 < 0)
            return false;
        pos = pos2;
        return true;
    }

    private String getLineAsString()
    {
        return getCRLFTerminatedLineAsString(lineBuffer, pos);
    }

    public boolean readNextHeaderValuesFromStream(InputStream src) throws IOException
    {
        while (true)
        {
            if (!readLine(src))
                return false; // Entity too large
            if (pos == 2)
                return true;

            for (int i=0; i<pos; i++)
            {
                if (lineBuffer[i] != (byte)':')
                    continue;
                
                String key = new String(lineBuffer, 0, i, HTTPUtils.ASCII);
                
                int val = i+1;
                while (lineBuffer[val] == (byte)' ')
                    val++;
                
                String value = new String(lineBuffer, val, pos-val-2, HTTPUtils.ASCII);
                if (getHeaderCount() >= MAX_HEADERS)
                    return false;

                setHeader(key, value);
                break;
            }
        }
    }
    
    public boolean readHeadersFromStream(InputStream src) throws IOException
    {
        return readHeadersFromStream(src, null);
    }

    public boolean readHeadersFromStream(InputStream src, InetSocketAddress clientSocketAddress) throws IOException
    {
        clear();
        if (!readLine(src))
            return false;// Header value too long, or too many headers 

        mainLine = getLineAsString();
        if (!readNextHeaderValuesFromStream(src))
            return false;// Header value too long, or too many headers

        try
        {
            if (clientSocketAddress != null)
                clientIPAddress = clientSocketAddress.getAddress().getHostAddress();
        }
        catch (Exception e) {}
        
        try
        {
            String forwardHeader = getHeader("X-Forwarded-For");
            if (forwardHeader == null)
                forwardHeader = getHeader("Forwarded");
            
            if (forwardHeader != null)
            {
                int comma = forwardHeader.indexOf(",");
                if (comma < 0)
                    clientIPAddress = forwardHeader.trim();
                else
                    clientIPAddress = forwardHeader.substring(0, comma).trim();

                if (clientIPAddress.startsWith("for="))
                    clientIPAddress = clientIPAddress.substring(4);
                if (clientIPAddress.startsWith("\""))
                    clientIPAddress = clientIPAddress.substring(1, clientIPAddress.length()-1).trim();
            }
        }
        catch (Exception e) {}

        return true;
    }

    public String getRequestURL()
    {
        if (reqURL != null)
            return reqURL;

        int space = mainLine.indexOf(" ");
        if (space < 0)
            return null;
        int end = mainLine.lastIndexOf(" ");
        if (end < 0)
            return null;

        try
        {
            reqURL = URLDecoder.decode(mainLine.substring(space+1, end).trim(), "UTF-8");
        }
        catch (Exception e) {}
        return reqURL;
    }

    public String getAbsoluteURL()
    {
        String result = getRequestURL();
        if (result == null)
            return null;
        if (result.startsWith("http://") || result.startsWith("https://"))
            return result;

        String host = getHost();
        if (host != null)
            return "http://"+host+result;
        return result;
    }

    public String getPath()
    {
        String reqURL = getRequestURL();
        if (reqURL.startsWith("http://"))
        {
            int s = reqURL.indexOf("/", 7);
            if (s < 0)
                return "/";
            reqURL = reqURL.substring(s);
        }
        else if (reqURL.startsWith("https://"))
        {
            int s = reqURL.indexOf("/", 8);
            if (s < 0)
                return "/";
            reqURL = reqURL.substring(s);
        }

        int q = reqURL.indexOf("?");
        if (q >= 0)
            reqURL = reqURL.substring(0, q);

        int h = reqURL.indexOf("#");
        if (h >= 0)
            reqURL = reqURL.substring(0, h);

        return reqURL;
    }

    public String getQueryString()
    {
        String reqURL = getRequestURL();
        int q = reqURL.indexOf("?");
        if (q < 0)
            return null;
        int h = reqURL.indexOf("#");
        if (h < q)
            h = reqURL.length();
        return reqURL.substring(q + 1, h);
    }

    public Map getQueryParameters()
    {
        if (!queryParsed)
        {
            queryParsed = true;
            parseHTTPQueryParameters(getQueryString(), queryMap);
        }
        return queryMap;
    }

    public boolean hasQueryParam(String key)
    {
        return getQueryParameters().get(key) != null;
    }

    public String[] getQueryKeys()
    {
        Map q = getQueryParameters();
        String[] result = new String[q.size()];
        q.keySet().toArray(result);
        return result;
    }

    public String getQuery(String key)
    {
        Map m = getQueryParameters();
        String value = (String) m.get(key);
        if (m == null)
            throw new IllegalStateException("Missing query parameter: '"+key+"'");
        return value;
    }

    public int getQueryInt(String key)
    {
        return Integer.parseInt(getQuery(key));
    }

    public long getQueryLong(String key)
    {
        return Long.parseLong(getQuery(key));
    }

    public double getQueryDouble(String key)
    {
        return Double.parseDouble(getQuery(key));
    }
    
    public boolean getQueryBoolean(String key)
    {
        return Boolean.parseBoolean(getQuery(key));
    }
    
    public Date getQueryDate(String key, DateFormat df)
    {
        try
        {
            return df.parse(getQuery(key));
        }
        catch (ParseException e) 
        {
            throw new IllegalStateException("Invalid date string for key '"+key+"'");
        }
    }

    public String getQuery(String key, String defaultValue)
    {
        String result = (String) getQueryParameters().get(key);
        if (result == null)
            return defaultValue;
        return result;
    }

    public boolean getQueryBoolean(String key, boolean defaultValue)
    {
        String value = getQuery(key, null);
        if (value == null)
            return defaultValue;
        
        return Boolean.parseBoolean(value);
    }

    public int getQueryInt(String key, int defaultValue)
    {
        String value = getQuery(key, null);
        if (value == null)
            return defaultValue;
        
        return Integer.parseInt(value);
    }

    public long getQueryLong(String key, long defaultValue)
    {
        String value = getQuery(key, null);
        if (value == null)
            return defaultValue;
        
        return Long.parseLong(value);
    }

    public double getQueryDouble(String key, double defaultValue)
    {
        String value = getQuery(key, null);
        if (value == null)
            return defaultValue;
        
        return Double.parseDouble(value);
    }

    public Date getQueryDate(String key, DateFormat df, long defaultTimeMillis)
    {
        String value = getQuery(key, null);
        if (value == null)
            return new Date(defaultTimeMillis);

        try
        {
            return df.parse(value);
        }
        catch (ParseException e)
        {
            throw new IllegalStateException("Invalid date string '"+value+"'");
        }
    }

    public static Map parseHTTPQueryParameters(String queryString)
    {
        return parseHTTPQueryParameters(queryString, null);
    }

    public static Map parseHTTPQueryParameters(String queryString, Map result)
    {
        if (result == null)
            result = new HashMap();
        if (queryString == null)
            return result;

        if (queryString.startsWith("?"))
            queryString = queryString.substring(1);

        String[] params = queryString.split("&");
        for (int i=0; i<params.length; i++)
        {
            int eq = params[i].indexOf("=");
            if (eq < 0)
                continue;
            try
            {
                String key = URLDecoder.decode(params[i].substring(0, eq), "UTF-8");
                String value = URLDecoder.decode(params[i].substring(eq+1), "UTF-8");
                result.put(key, value);
            }
            catch (UnsupportedEncodingException e) {}
        }

        return result;
    }
    
    public boolean isHTTP11()
    {
        return mainLine.endsWith(" HTTP/1.1");
    }

    public String getHost()
    {
        String hostHeader = getHeader("Host", null);
        if (hostHeader != null)
            return hostHeader;
        String absoluteURL = getRequestURL();
        
        if (absoluteURL.startsWith("http://"))
            absoluteURL = absoluteURL.substring(7);
        if (absoluteURL.startsWith("https://"))
            absoluteURL = absoluteURL.substring(8);
        
        int s = absoluteURL.indexOf("/");
        if (s < 0)
            return null;
        
        String result = absoluteURL.substring(0, s);
        int c = result.indexOf(":");
        if (c > 0)
            result = result.substring(0, c);
        return result;
    }

    public String getClientIPAddress()
    {
        return clientIPAddress;
    }

    public boolean isHead()
    {
        return mainLine.startsWith("HEAD");
    }

    public boolean isGet()
    {
        return mainLine.startsWith("GET");
    }

    public boolean isPost()
    {
        return mainLine.startsWith("POST");
    }

    public boolean isPut()
    {
        return mainLine.startsWith("PUT");
    }

    public String getHTTPMethod()
    {
        if (mainLine == null)
            return null;

        int sp = mainLine.indexOf(" ");
        if (sp < 0)
            return null;
        
        return mainLine.substring(0, sp);
    }

    public boolean expectsContinueResponse()
    {
        return !isHead() && getHeader("Expect", "").startsWith("100-continue") || isPost();
    }

    public long getIfModifiedSinceTime()
    {
        return HTTPUtils.getUtils().parseHTTPDate(getHeader("If-Modified-Since", null), -1);
    }

    public boolean requestsPartialContent()
    {
        return getHeader("Range", null) != null;
    }
    
    public String getUserAgent()
    {
        return getHeader("User-Agent", "");
    }

    public static String getCRLFTerminatedLineAsString(byte[] lineBuffer, int len)
    {
        try
        {
            if (len <= 2)
                return "";
            return new String(lineBuffer, 0, len-2, HTTPUtils.ASCII);
        }
        catch (Exception e) 
        {
            return new String(lineBuffer, 0, len-2);//Should be impossible
        }
    }
    
    public static int readLine(InputStream src, byte[] lineBuffer) throws IOException
    {
        int pos = 0;
        int eol = 0;
        while (true)
        {
            int b = src.read();
            if (b < 0)
                throw new EOFException("Unexpected EOF while seeking EOL");
            if (pos >= lineBuffer.length)
                return -1; // "413 Entity Too Large" (when in HTTP header line);
            
            lineBuffer[pos++] = (byte) b;
            eol = (eol << 8) | b;
            if ((0xFFFF & eol) == 0x0D0A)
                return pos;
        }
    }

    public void clearCookies()
    {
        deleteHeader("Cookie");
        cookieMap.clear();
    }

    public String[] getCookieNames()
    {
        if (!cookiesParsed)
        {
            cookiesParsed = true;
            parseCookies(getHeader("Cookie"), cookieMap);
        }

        String[] result = new String[cookieMap.size()];
        cookieMap.keySet().toArray(result);
        return result;
    }

    public String getCookie(String key)
    {
        if (!cookiesParsed)
        {
            cookiesParsed = true;
            parseCookies(getHeader("Cookie"), cookieMap);
        }
        
        return (String) cookieMap.get(key);
    }

    public static Map parseCookies(String cookieString) 
    {
        return parseCookies(cookieString, null);
    }

    public static Map parseCookies(String cookieString, Map result) 
    {
        if (result == null)
            result = new LinkedHashMap();
        if (cookieString == null)
            return result;
        
        String[] parts = cookieString.split(";");
        for (int i=0; i<parts.length; i++)
        {
            int eq = parts[i].indexOf("=");
            if (eq < 0)
                continue;
            String name = parts[i].substring(0, eq).trim();
            String value = parts[i].substring(eq+1).trim();
            result.put(name, value);
        }
        return result;
    }
}
