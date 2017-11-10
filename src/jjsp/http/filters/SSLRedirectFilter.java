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

public class SSLRedirectFilter implements HTTPRequestFilter
{
    private String name, sslHostName;

    protected HTTPRequestFilter onwardChain;

    public SSLRedirectFilter(String name, HTTPRequestFilter onwardChain)
    {
        this(name, null, onwardChain);
    }

    public SSLRedirectFilter(String name, String sslHostName, HTTPRequestFilter onwardChain)
    {
        this.name = name;
        this.onwardChain = onwardChain;
        setSSLHostName(sslHostName);
    }

    public String getSSLHostName()
    {
        return sslHostName;
    }

    public void setSSLHostName(String host)
    {
        sslHostName = host;
        if (sslHostName != null)
            sslHostName = sslHostName.replace("/", "");
    }
    
    public void close() 
    {
        if (onwardChain != null)
            onwardChain.close();
    }

    public HTTPFilterChain filterRequest(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state)
    {
        HTTPFilterChain myChain = new HTTPFilterChain(name, chain);

        response.getHeaders().setHeader("Strict-Transport-Security", "max-age=3600; includeSubDomains");
        if (!request.isSecure())
        {
            try
            { 
                String host = sslHostName;
                if (host == null)
                    host = request.getHeaders().getHost();

                String secureURL = "https://"+host+request.getHeaders().getRequestURL();
                myChain.report = "Redirect to SSL: "+secureURL;
                
                response.getHeaders().configureAsRedirect(secureURL, HTTPResponseHeaders.HTTP_MOVED_PERMANENTLY);
                response.getHeaders().configureCacheControl(86400); // Even permenent redirects should have a time limit!
                response.sendHeaders();
            }
            catch (Exception e)
            {
                myChain.error = e;
            }

            return myChain;
        }
        else
        {
            myChain.report = "Is SSL";
            return onwardChain.filterRequest(myChain, request, response, state);
        }
    }
}
