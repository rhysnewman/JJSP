package jjsp.http;

import java.io.*;
import java.nio.charset.*;
import java.net.*;
import java.util.*;
import java.text.*;
import java.security.*;

import jjsp.util.*;

public class HTTPResponseHeaders extends HTTPHeaders
{
    public static final int HTTP_OK = 200;
    public static final int HTTP_PARTIAL_CONTENT = 206;
    public static final int HTTP_CONTINUE = 100;
    public static final int HTTP_NO_CONTENT = 204;
    public static final int HTTP_MOVED_PERMANENTLY = 301;
    public static final int HTTP_FOUND = 302;
    public static final int HTTP_SEE_OTHER = 303;
    public static final int HTTP_NOT_MODIFIED = 304;
    public static final int HTTP_TEMPORARY_REDIRECT = 307;
    public static final int HTTP_PERMANENT_REDIRECT = 308;
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_UNAUTHORIZED = 401;
    public static final int HTTP_FORBIDDEN = 403;
    public static final int HTTP_NOT_FOUND = 404;
    public static final int HTTP_TOO_LARGE = 413;
    public static final int HTTP_NOT_ALLOWED = 405;
    public static final int HTTP_SERVER_ERROR = 500;
    public static final int HTTP_NOT_IMPLEMENTED = 501;
    public static final int HTTP_SERVICE_UNAVAILABLE = 503;
    public static final int HTTP_GATEWAY_TIMEOUT = 504;

    public boolean isHTTP11()
    {
        return mainLine.startsWith("HTTP/1.1 ");
    }

    public void setConnectionClose()
    {
        setHeader("Connection", "close");
    }
    
    public void setLastModified(long time)
    {
        if (time >= 0)
            setHeader("Last-Modified", HTTPUtils.getUtils().formatHTTPDate(time));
    }

    public void setLastModified(Date date)
    {
        setLastModified(date.getTime());
    }

    public void configureToPreventCaching()
    {
        setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
        setHeader("Expires", "Sat, 01 Jan 2000 00:00:00 GMT");
        setHeader("Pragma", "no-cache");
    }

    public void configureCacheControl(int maxAgeSeconds)
    {
        configureCacheControl((String) null, maxAgeSeconds);
    }

    public void configureCacheControl(boolean isPublic, int maxAgeSeconds)
    {
        configureCacheControl((String) null, isPublic, System.currentTimeMillis(), maxAgeSeconds);
    }

    public void configureCacheControl(byte[] rawData, int maxAgeSeconds)
    {
        configureCacheControl(HTTPUtils.getUtils().createETag(rawData), maxAgeSeconds);
    }
 
    public void configureCacheControl(String eTag, int maxAgeSeconds)
    {
        configureCacheControl(eTag, System.currentTimeMillis(), maxAgeSeconds);
    }
    
    public void configureCacheControl(String eTag, long lastModified, int maxAgeSeconds)
    {
        configureCacheControl(eTag, true, lastModified, maxAgeSeconds);
    }

    public void configureCacheControl(String eTag, boolean isPublic, long lastModified, int maxAgeSeconds)
    {
        setLastModified(lastModified);
        if (maxAgeSeconds > 0)
        {
            if (isPublic)
                setHeader("Cache-Control", "public, max-age="+maxAgeSeconds);
            else
                setHeader("Cache-Control", "private, max-age="+maxAgeSeconds);
        }
        else
            configureToPreventCaching();

        if (eTag != null)
            setHeader("ETag", eTag);
    }

    public boolean cacheControlConfigured()
    {
        return hasHeader("Cache-Control");
    }

    public void setContentLength(long length)
    {
        setHeader("Content-Length", ""+length);
        deleteHeader("Transfer-Encoding");
    }

    public void setGZipContentEncoding()
    {
        setContentEncoding("gzip");
    }

    public void setContentEncoding(String encType)
    {
        setHeader("Content-Encoding", encType);
    }

    public void clearContentType()
    {
        setHeader("Content-Type", null);
    }

    public void setContentType(String mimeType)
    {
        if (mimeType != null)
            setHeader("Content-Type", mimeType);
    }

    public void guessAndSetContentType(String fileName)
    {
        String type = guessMIMEType(fileName);
        if (type != null)
            setHeader("Content-Type", guessMIMEType(fileName));
    }

    public void guessAndSetContentType(String fileName, String defaultType)
    {
        String type = guessMIMEType(fileName, defaultType);
        if (type != null)
            setHeader("Content-Type", type);
    }

    public boolean contentTypeConfigured()
    {
        return hasHeader("Content-Type");
    }

    public String getHeader(String key, String defaultValue)
    {
        try
        {
            return super.getHeader(key, defaultValue);
        }
        catch (ClassCastException e) // Handle the special case for set-cookie, commands which are in a list rather than a String
        {
            List ll = (List) headerMap.get(key);
            StringBuffer buf = new StringBuffer();
            for (int i=0; i<ll.size(); i++)
            {
                if (i > 0)
                    buf.append(", ");
                buf.append((String) ll.get(i));
            }
            return buf.toString();
        }
    }

    public void setHeader(String key, String value)
    {
        if ("Set-Cookie".equalsIgnoreCase(key))
            throw new IllegalStateException("Cannot use setHeader to set a cookie value - use setCookie instead");
        else
            super.setHeader(key, value);
    }

    public void clearCookies()
    {
        deleteHeader("Set-Cookie");
    }

    public void setCookie(HttpCookie cookie) 
    {
        List ll = (List) headerMap.get("Set-Cookie");
        if (ll == null)
        {
            ll = new ArrayList();
            headerMap.put("Set-Cookie", ll);
        }
        ll.add(cookie);
    }

    public HttpCookie getCookie(String name)
    {
        List ll = (List) headerMap.get("Set-Cookie");
        if (ll == null)
            return null;
        for (int i=0; i<ll.size(); i++)
        {
            HttpCookie cookie = (HttpCookie) ll.get(i);
            if (cookie.getName().equals(name))
                return cookie;
        }
        return null;
    }

    public void configureAsOK()
    {
        configure(HTTP_OK, "OK");
    }

    public void configureAsPartialContent()
    {
        configure(HTTP_PARTIAL_CONTENT, "Partial Content");
    }

    public void configureAsNotAuthorized(boolean allowBasicAuth)
    {
        configureAsNotAuthorised(allowBasicAuth);
    }

    public void configureAsNotAuthorised(boolean allowBasicAuth)
    {
        configure(HTTP_UNAUTHORIZED, "Unauthorized");
        if (allowBasicAuth)
            setHeader("WWW-Authenticate", "Basic");
    }
    
    public void configureAsNotAuthorised(String basicAuthRealm)
    {
        configure(HTTP_UNAUTHORIZED, "Unauthorized");
        if (basicAuthRealm != null)
            setHeader("WWW-Authenticate", "Basic realm=\""+basicAuthRealm+"\"");
        else
            setHeader("WWW-Authenticate", "Basic");
    }

    public void configureAsNotFound()
    {
        configure(HTTP_NOT_FOUND, "Not Found");
    }
    
    public void configureAsNotAllowed()
    {
        configureAsNotAllowed("Not Allowed");
    }

    public void configureAsNotAllowed(String message)
    {
        configure(HTTP_NOT_ALLOWED, message);
    }

    public void configureAsUnavailable()
    {
        configure(HTTP_SERVICE_UNAVAILABLE, "Service Unavailable");
    }

    public void configureAsServerError()
    {
        configureAsServerError("");
    }

    public void configureAsServerError(String headerMessage)
    {
        configure(HTTP_SERVER_ERROR, "Internal Server Error "+ headerMessage);
    }

    public void configureAsBadRequest()
    {
        configureAsBadRequest(null);
    }

    public void configureAsBadRequest(String message)
    {
        if (message == null)
            configure(HTTP_BAD_REQUEST, "The Request was Invalid");
        else
            configure(HTTP_BAD_REQUEST, message);
    }

    public void configureAsNotImplemented()
    {
        configure(HTTP_NOT_IMPLEMENTED, "Not Implemented");
    }

    public void configureAsRedirect(String location)
    {
        configureAsRedirect(location, HTTP_SEE_OTHER);
    }

    public void configureAsRedirect(String location, int httpRedirectCode)
    {
        if (httpRedirectCode == HTTP_SEE_OTHER)
            configure(HTTP_SEE_OTHER, "See Other");
        else if (httpRedirectCode == HTTP_FOUND)
            configure(HTTP_FOUND, "Found");
        else if (httpRedirectCode == HTTP_MOVED_PERMANENTLY)
            configure(HTTP_MOVED_PERMANENTLY, "Moved Permanently");
        else if (httpRedirectCode == HTTP_TEMPORARY_REDIRECT)
            configure(HTTP_TEMPORARY_REDIRECT, "Temporary Redirect");
        else if (httpRedirectCode == HTTP_PERMANENT_REDIRECT)
            configure(HTTP_PERMANENT_REDIRECT, "Permanent Redirect");
        else
            throw new IllegalStateException("HTTP Redirect code unknown: "+httpRedirectCode);
            
        setHeader("Location", location);
    }

    public void configureAsNoContent()
    {
        configure(HTTP_NO_CONTENT, "No Content");
    }

    public void configureAsTooLarge()
    {
        configure(HTTP_TOO_LARGE, "Request Entity Too Large");
    }

    public void configureAsNotModified()
    {
        configure(HTTP_NOT_MODIFIED, "Not Modified");
    }

    public void configureAsForbidden()
    {
        configure(HTTP_FORBIDDEN, "Forbidden");
    }

    public void configure(int code, String statusMessage)
    {
        mainLine = "HTTP/1.1 "+code+" "+statusMessage;

        setHeader("Date", HTTPUtils.getUtils().formatHTTPDate());
        setHeader("Connection", "keep-alive");
    }

    public boolean responseCodeConfigured()
    {
        return mainLine != null;
    }

    public void convertToHTTP10()
    {
        if (!responseCodeConfigured())
            throw new IllegalStateException("Response code not configured");

        if (isHTTP11())
        {
            mainLine = "HTTP/1.0"+mainLine.substring(8);
            deleteHeader("Transfer-Encoding");
        }
    }

    private static byte[] crlf = Utils.getAsciiBytes("\r\n");
    private static byte[] colon = Utils.getAsciiBytes(": ");

    public void printToStream(OutputStream out) throws IOException
    {
        out.write(Utils.getAsciiBytes(mainLine));
        out.write(crlf);

        Iterator itt = headerMap.entrySet().iterator();
        while (itt.hasNext())
        {
            Map.Entry entry = (Map.Entry) itt.next();
            String key = (String) entry.getKey();
            Object val = entry.getValue();
            try
            {
                String sval = (String) val;
                out.write(Utils.getAsciiBytes(key));
                out.write(colon);
                out.write(Utils.getAsciiBytes(sval));
                out.write(crlf);
            }
            catch (ClassCastException e) //handle a list of Set-Cookie entries
            {
                List ll = (List) val;
                for (int i=0; i<ll.size(); i++)
                {
                    out.write(Utils.getAsciiBytes(key));
                    out.write(colon);
                    out.write(Utils.getAsciiBytes(formatSetCookie((HttpCookie)ll.get(i))));
                    out.write(crlf);
                }
            }
        }
        out.write(crlf);
    }

    private static byte[] continueResponseBytes = Utils.getAsciiBytes("HTTP/1.1 100 Continue\r\n\r\n");

    public static void sendContinueResponse(OutputStream out) throws IOException
    {
        out.write(continueResponseBytes);
        out.flush();
    }

    private static SimpleDateFormat cookieExpiresFormat;
    static
    {
        cookieExpiresFormat = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss zzz", Locale.ROOT); //Wdy, DD-Mon-YYYY HH:MM:SS GMT (e.g. Sat, 02 May 2009 23:38:25 GMT)
        cookieExpiresFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public static String formatSetCookie(HttpCookie cookie)
    {
        // Set-Cookie: value[; Expires=date][; Domain=domain][; Path=path][; Secure][; HttpOnly]
        StringBuffer buf = new StringBuffer();
        buf.append(cookie.getName()+"="+cookie.getValue());
        if (cookie.getMaxAge() > 0)
        {
            synchronized (cookieExpiresFormat)
            {
                buf.append("; Expires="+cookieExpiresFormat.format(new Date(System.currentTimeMillis() + cookie.getMaxAge()*1000l)));
            }
        }
        if (cookie.getDomain() != null)
            buf.append("; Domain="+cookie.getDomain());
        if (cookie.getPath() != null)
            buf.append("; Path="+cookie.getPath());
        if (cookie.getSecure())
            buf.append("; Secure");
        if (cookie.isHttpOnly())
            buf.append("; HttpOnly");
            
        return buf.toString();
    }

    public static HttpCookie parseSetCookie(String raw)
    {
        int eq = raw.indexOf("=");
        if (eq < 0)
            return null;
        String name = raw.substring(0, eq).trim();
        raw = raw.substring(eq+1);
        
        int semi = raw.indexOf(";");
        if (semi < 0)
            return new HttpCookie(name, raw.trim());
        
        HttpCookie result = new HttpCookie(name, raw.substring(semi).trim());

        raw = raw.substring(semi+1).trim();
        String[] parts = raw.split(";");
        for (int i=0; i<parts.length; i++)
        {
            String part = parts[i].trim();
            eq = part.indexOf("=");
            if (eq < 0)
                continue;

            String partName = part.substring(0, eq).trim();
            String partValue = part.substring(eq+1).trim();
            if (partName.equalsIgnoreCase("domain"))
                result.setDomain(partValue);
            else if (partName.equalsIgnoreCase("expires"))
            {
                try
                {
                    Date exp = cookieExpiresFormat.parse(partValue);
                    result.setMaxAge( (exp.getTime() - System.currentTimeMillis())/1000);
                }
                catch (Exception e) {}
            }
            else if (partName.equalsIgnoreCase("path"))
                result.setPath(partName);
            else if (partName.equalsIgnoreCase("secure"))
                result.setSecure(true);
            else if (partName.equalsIgnoreCase("httponly"))
                result.setHttpOnly(true);
        }

        return result;
    }
}
