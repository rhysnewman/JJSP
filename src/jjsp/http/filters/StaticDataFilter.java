package jjsp.http.filters;

import java.io.*;

import jjsp.http.*;

public class StaticDataFilter extends AbstractRequestFilter
{
    protected byte[] rawData;
    protected int cacheTime;
    protected long lastModifiedTime;
    protected String eTag, contentType, urlPath, contentEncoding;

    public StaticDataFilter(String name, String urlPath, String fixedResponse, HTTPRequestFilter chain)
    {
        this(name, urlPath, fixedResponse.getBytes(HTTPUtils.ASCII), 86400, chain);
    }

    public StaticDataFilter(String name, String urlPath, byte[] fixedResponseData, HTTPRequestFilter chain)
    {
        this(name, urlPath, fixedResponseData, 86400, chain);
    }

    public StaticDataFilter(String name, String urlPath, byte[] fixedResponseData, int cacheTime, HTTPRequestFilter chain)
    {
        this(name, urlPath, fixedResponseData, cacheTime, null, chain);
    }

    public StaticDataFilter(String name, String urlPath, byte[] fixedResponseData, int cacheTime, String contentType, HTTPRequestFilter chain)
    {
        super(name, chain);

        rawData = fixedResponseData;
        if (rawData == null)
            throw new NullPointerException("FixedResponseData for filter "+name+" is null");
        eTag = HTTPUtils.getUtils().createETag(rawData);
        lastModifiedTime = System.currentTimeMillis()/1000*1000;

        if (contentType == null)
            this.contentType = "text/html; charset=utf-8";
        else
            this.contentType = contentType;

        if ((urlPath != null) && !urlPath.startsWith("/"))
            urlPath = "/"+urlPath;
        
        this.cacheTime = cacheTime;
        this.urlPath = urlPath;
    }

    public String getFixedURLPath()
    {
        return urlPath;
    }

    public void setContentEncoding(String enc)
    {
        contentEncoding = enc;
    }

    public void setLastModificationTime(long ms)
    {
        lastModifiedTime = ms/1000*1000;
    }

    public void setCacheTimeSeconds(int seconds)
    {
        cacheTime = seconds;
    }

    public int getCacheTimeSeconds()
    {
        return cacheTime;
    }

    public String getContentType()
    {
        return contentType;
    }

    public void setContentType(String type)
    {
        contentType = type;
    }

    public byte[] getRawBytes()
    {
        return rawData;
    }

    protected void configureResponseHeaders(HTTPInputStream req, HTTPOutputStream resp)
    {
        resp.getHeaders().configureAsOK();
        if (req.getHeaders().requestsPartialContent())
            resp.getHeaders().configureAsPartialContent();
        
        if (cacheTime > 0)
            resp.getHeaders().configureCacheControl(eTag, lastModifiedTime, cacheTime);
        else
        {
            resp.getHeaders().configureToPreventCaching();
            resp.getHeaders().setLastModified(lastModifiedTime);
        }

        if (contentType != null)
            resp.getHeaders().setContentType(contentType);
        if (contentEncoding != null)
            resp.getHeaders().setContentEncoding(contentEncoding);
    }
    
    protected boolean checkNotModified(HTTPInputStream req, HTTPOutputStream resp)
    {
        long time = req.getHeaders().getIfModifiedSinceTime();
        if ((time < 0) || (lastModifiedTime > time))
            return false;
        if (req.getHeaders().requestsPartialContent())
            return false;
        return true;
    }

    protected boolean handleRequest(HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException
    {
        if (urlPath != null)
        {
            String path = request.getHeaders().getPath();
            if (!urlPath.equalsIgnoreCase(path))
                return false;
        }

        if (checkNotModified(request, response))
        {
            configureResponseHeaders(request, response);
            
            response.getHeaders().configureAsNotModified();
            response.getHeaders().setContentLength(rawData.length);
            response.sendHeaders();
        }
        else
        {
            configureResponseHeaders(request, response);

            long start = 0;
            long end = rawData.length;        
            long[] limits = request.getHeaders().extractByteRanges();
            if (limits != null)
            {
                start = limits[0];
                end = limits[1];
                if (end < 0)
                    end = rawData.length;
                else
                    end = Math.min(rawData.length, end);
            }

            if (request.getHeaders().isHead())
            {
                response.getHeaders().setContentLength(end-start);
                response.sendHeaders();
            }
            else
                response.sendContent(rawData, (int) start, (int) (end-start));
        }

        return true;
    }
}
