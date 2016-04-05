
package jjsp.http.filters;

import java.io.*;
import java.net.*;

import jjsp.http.*;

public class HTTPRequestHandlerAdapter extends AbstractRequestFilter
{
    protected HTTPRequestHandler handler;

    public HTTPRequestHandlerAdapter(String name, HTTPRequestHandler handler, HTTPRequestFilter filterChain)
    {
        super(name, filterChain);
        this.handler = handler;
    }

    protected boolean handleRequestAndReport(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException
    {
        if (handler.handleRequest(request, response, state, chain))
            return true;

        return response.outputSent() && response.contentStreamClosed();
    }
}
