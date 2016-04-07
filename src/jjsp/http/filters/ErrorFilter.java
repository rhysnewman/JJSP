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
import java.net.*;

import jjsp.util.*;
import jjsp.http.*;

public class ErrorFilter extends AbstractRequestFilter 
{
    public static final String SENDING_SERVER_ERROR = "SENDING_SERVER_ERROR";
    public static final String SENDING_HTML_SERVER_ERROR = "SENDING_HTML_SERVER_ERROR";
    
    protected HTTPRequestFilter errorFilter;

    public ErrorFilter(String filterName, HTTPRequestFilter filterChain)
    {
        this(filterName, null, filterChain);
    }

    public ErrorFilter(String filterName, HTTPRequestFilter errorFilter, HTTPRequestFilter filterChain)
    {
        super(filterName, filterChain);
        this.errorFilter = errorFilter;
    }

    public void setErrorHandler(HTTPRequestFilter errorFilter)
    {
        this.errorFilter = errorFilter;
    }

    protected void constructHTMLStackTracePage(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state, Throwable error) throws Exception
    {
        if (response.outputSent())
            return;

        response.getHeaders().configureAsServerError("Error processing: "+request.getHeaders().getRequestURL());

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bout);
        for (Throwable tt = error; tt != null; tt = tt.getCause())
            tt.printStackTrace(ps);
        ps.close();
        
        String html = "<HTML><BODY>\n<h1>Server Error for HTTP URL: '"+HTTPHeaders.escapeHTML(request.getHeaders().getRequestURL())+"'</h1>";
        html += "<hr><pre>\n";
        html += HTTPHeaders.escapeHTML(Utils.toString(bout.toByteArray()))+"\n";
        html += "</pre></body></html>";

        response.sendContent(html);
    }

    public HTTPFilterChain filterRequest(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state)
    {
        HTTPFilterChain myChain = new HTTPFilterChain(filterName, chain);
        
        try
        {
            myChain = filterChain.filterRequest(myChain, request, response, state);
            if ((myChain.getPrimaryError() == null) || response.outputSent())
                return myChain;
        }
        catch (Throwable error)
        {
            myChain.error = error;
        }
        
        myChain.report = SENDING_SERVER_ERROR;
        if (errorFilter != null)
            return errorFilter.filterRequest(myChain, request, response, state);

        try
        {
            myChain.report = SENDING_HTML_SERVER_ERROR;
            constructHTMLStackTracePage(chain, request, response, state, myChain.getPrimaryError());
        }
        catch (Throwable e) {}
        
        return myChain;
    }
}
