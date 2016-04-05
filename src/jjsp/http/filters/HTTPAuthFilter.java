
package jjsp.http.filters;

import java.io.*;
import java.net.*;
import java.util.*;

import jjsp.http.*;
import jjsp.util.*;

public class HTTPAuthFilter implements HTTPRequestFilter
{
    public static final String AUTHORISED = "AUTHORISED";
    public static final String NOT_AUTHORISED = "NOT_AUTHORISED";
    
    private String name, realm;
    private HashMap registeredUsers;

    protected final HTTPRequestFilter failFilterChain, successFilterChain;

    public HTTPAuthFilter(String name, HTTPRequestFilter failFilterChain, HTTPRequestFilter successFilterChain)
    {
        this(name, null, null, failFilterChain, successFilterChain);
    }
    
    public HTTPAuthFilter(String name, String realm, HTTPRequestFilter failFilterChain, HTTPRequestFilter successFilterChain)
    {
        this(name, null, null, realm, failFilterChain, successFilterChain); 
    }
    
    public HTTPAuthFilter(String name, String userName, String password, HTTPRequestFilter failFilterChain, HTTPRequestFilter successFilterChain)
    {
        this(name, userName, password, "JET", failFilterChain, successFilterChain);
    }

    public HTTPAuthFilter(String name, String userName, String password, String realm, HTTPRequestFilter failFilterChain, HTTPRequestFilter successFilterChain)
    {
        this.name = name;
        this.realm = realm;
        this.failFilterChain = failFilterChain;
        this.successFilterChain = successFilterChain;

        registeredUsers = new HashMap();
        if ((userName != null) && (password != null))
            registerUser(userName, password);
    }

    public void setBasicAuthRealm(String realm)
    {
        this.realm = realm;
    }

    public void deregisterUser(String userName)
    {
        synchronized (registeredUsers)
        {
            registeredUsers.remove(userName);
        }
    }

    public boolean registerUser(String userName, String password)
    {
        if (userName == null)
            return false;
        if (password == null)
            password = "";

        synchronized (registeredUsers)
        {
            registeredUsers.put(userName, password);
            return true;
        }
    }

    protected String getPasswordFor(String user)
    {
        synchronized (registeredUsers)
        {
            return (String) registeredUsers.get(user);
        }
    }

    protected boolean checkAuthorised(HTTPInputStream request) throws IOException
    {
        HTTPRequestHeaders headers = request.getHeaders(); 
        String auth = headers.getHeader("Authorization");
        if ((auth == null) || (auth.length() == 0) || !auth.startsWith("Basic "))
            return false;
        auth = auth.substring(6).trim();
        
        Base64.Decoder decoder = Base64.getDecoder();
        String unpw = Utils.toString(decoder.decode(auth));
        
        int colon = unpw.indexOf(":");
        String un = unpw.substring(0, colon);
        String pw = unpw.substring(colon+1);
        
        String requiredPW = getPasswordFor(un);
        if ((requiredPW == null) || !requiredPW.equals(pw))
            return false;

        return true;
    }

    public HTTPFilterChain filterRequest(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state)
    {
        HTTPFilterChain myChain = new HTTPFilterChain(name, chain);

        try
        {
            if (checkAuthorised(request))
            {
                myChain.report = AUTHORISED;
                return successFilterChain.filterRequest(myChain, request, response, state);
            }
            else
            {
                myChain.report = NOT_AUTHORISED;
                if (failFilterChain != null)
                    return failFilterChain.filterRequest(myChain, request, response, state);
                
                response.getHeaders().configureAsNotAuthorised(realm);
                response.getHeaders().configureToPreventCaching();
                response.sendHeaders();
            }
        }
        catch (Throwable t)
        {
            myChain.report = AbstractRequestFilter.ERROR;
            myChain.error = t;
        }
        
        return myChain;
    }
}
