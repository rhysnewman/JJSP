
package jjsp.http.filters;

import java.io.*;

import jjsp.http.*;
import jjsp.util.*;

public class SimpleFileFilter extends AbstractRequestFilter
{
    protected File src;
    protected String contentType;

    public SimpleFileFilter(String name, File src)
    {
        this(name, src, HTTPHeaders.guessMIMEType(src.getName()));
    }

    public SimpleFileFilter(String name, File src, String contentType)
    {
        super(name, null);

        this.src = src;
        this.contentType = contentType;
    }

    protected boolean handleRequestAndReport(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException
    {
        response.getHeaders().configureAsOK();
        response.getHeaders().setLastModified(src.lastModified());
        response.getHeaders().configureToPreventCaching();

        if (request.getHeaders().isHead())
        {
            response.getHeaders().setContentLength(src.length());
            response.sendHeaders();
        }
        else
            response.sendContent(Utils.load(src));

        chain.report = src.getName();
        return true;
    }
}
