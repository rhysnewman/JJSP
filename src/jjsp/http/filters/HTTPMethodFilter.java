
package jjsp.http.filters;

import java.io.*;

import jjsp.http.*;

public class HTTPMethodFilter implements HTTPRequestFilter
{
    private String name;

    protected HTTPRequestFilter postFilter, getFilter, headFilter;

    public HTTPMethodFilter(String name, HTTPRequestFilter headFilter, HTTPRequestFilter getFilter, HTTPRequestFilter postFilter)
    {
        this.name = name;

        this.postFilter = postFilter;
        this.getFilter = getFilter;
        this.headFilter = headFilter;
    }
    
    public void close() 
    {
        if (postFilter != null)
            postFilter.close();
        if (getFilter != null)
            getFilter.close();
        if (headFilter != null)
            headFilter.close();
    }

    public HTTPFilterChain filterRequest(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state)
    {
        HTTPFilterChain myChain = new HTTPFilterChain(name, chain);
        HTTPRequestHeaders reqHeaders = request.getHeaders();

        if (reqHeaders.isGet())
            return getFilter.filterRequest(myChain, request, response, state);
        else if (reqHeaders.isPost())
            return postFilter.filterRequest(myChain, request, response, state);
        else if (reqHeaders.isHead())
            return headFilter.filterRequest(myChain, request, response, state);
        else
        {
            myChain.error = new IOException("Invalid HTTP method "+reqHeaders.getMainLine());
            return myChain;
        }
    }
}
