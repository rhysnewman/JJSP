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

package jjsp.http.filters;

import java.io.*;

import jjsp.http.*;
import jjsp.util.*;

public class SingleFileFilter extends AbstractRequestFilter
{
    protected File src;
    protected int cacheTime;
    protected RandomAccessFile raf;
    protected long lastModifiedTime;
    protected String eTag, contentType;

    public SingleFileFilter(String name, File src) throws IOException
    {
        this(name, src, HTTPHeaders.guessMIMEType(src.getName()));
    }

    public SingleFileFilter(String name, File src, String contentType) throws IOException
    {
        super(name, null);

        this.src = src;
        this.contentType = contentType;
        this.cacheTime = 86400;
        
        lastModifiedTime = src.lastModified();
        eTag = "file-"+lastModifiedTime;
        raf = new RandomAccessFile(src, "r");
    }

    protected void closeFilter() throws Exception
    {
        raf.close();
        raf = null;
    }

    public void setCacheTimeSeconds(int seconds)
    {
        cacheTime = seconds;
    }

    protected long[] configureResponseHeaders(HTTPInputStream request, HTTPOutputStream response)
    {
        long[] limits = request.getHeaders().extractByteRanges();
        
        if (limits != null)
            response.getHeaders().configureAsPartialContent();
        else
            response.getHeaders().configureAsOK();
        
        if (cacheTime > 0)
            response.getHeaders().configureCacheControl(eTag, lastModifiedTime, cacheTime);
        else
        {
            response.getHeaders().setLastModified(lastModifiedTime);
            response.getHeaders().configureToPreventCaching();
        }

        if (contentType != null)
            response.getHeaders().setContentType(contentType);
        
        return limits;
    }

    protected boolean handleRequestAndReport(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException
    {
        long[] limits = configureResponseHeaders(request, response);

        long start = 0;
        long end = raf.length();
        
        if (limits != null)
        {
            start = limits[0];
            end = limits[1];
            if (end < 0)
                end = raf.length();
            else
                end = Math.min(raf.length(), end);
        }

        if (request.getHeaders().isHead())
        {
            response.getHeaders().setContentLength(end-start);
            response.sendHeaders();
        }
        else
        {
            long toRead = end-start;
            response.prepareToSendContent(end-start, false);
            byte[] buffer = new byte[(int) Math.max(10*1024, Math.min(toRead, 2*1024*1024))];

            while (true)
            {
                int rr = (int) Math.min(toRead, buffer.length);
                if (rr <= 0)
                    break;

                synchronized (raf)
                {
                    raf.seek(start);
                    raf.readFully(buffer, 0, rr);
                }

                response.write(buffer, 0, rr);
                toRead -= rr;
                start += rr;
            }
            response.close();
        }

        chain.report = src.getName();
        return true;
    }
}
