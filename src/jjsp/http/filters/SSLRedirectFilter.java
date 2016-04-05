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
