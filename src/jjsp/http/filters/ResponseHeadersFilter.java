package jjsp.http.filters;

import java.util.*;
import java.io.*;

import jjsp.http.*;

public class ResponseHeadersFilter implements HTTPRequestFilter
{
    private final String name;
    private final HTTPRequestFilter wrapped;
    private final String[] names, values;

    public ResponseHeadersFilter(String name, Map headerKeyValues, HTTPRequestFilter wrapped) 
    {
        this.name = name;
        this.wrapped = wrapped;

        names = new String[headerKeyValues.size()];
        values = new String[headerKeyValues.size()];
        headerKeyValues.keySet().toArray(names);
        for (int i=0; i<names.length; i++)
            values[i] = (String) headerKeyValues.get(names[i]);
    }

    protected void addHeadersToResponse(HTTPRequestHeaders requestHeaders, HTTPResponseHeaders responseHeaders)
    {
        for (int i=0; i<names.length; i++)
            responseHeaders.setHeader(names[i], values[i]);
    }

    public HTTPFilterChain filterRequest(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state)
    {
        HTTPFilterChain myChain = new HTTPFilterChain(name, chain);
        HTTPRequestHeaders reqHeaders = request.getHeaders();
        HTTPResponseHeaders respHeaders = response.getHeaders();

        addHeadersToResponse(reqHeaders, respHeaders);
        return wrapped.filterRequest(myChain, request, response, state);
    }
}
