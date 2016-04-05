package jjsp.http.filters;

import java.io.*;
import java.net.*;

import jjsp.http.*;

public class PredicateFilter extends AbstractRequestFilter 
{
    public static final String YES = "PREDICATE_YES";
    public static final String NO = "PREDICATE_NO";
    
    protected HTTPRequestPredicate predicate;
    protected HTTPRequestFilter yesFilter, noFilter;

    public PredicateFilter(String filterName, HTTPRequestPredicate p, HTTPRequestFilter yes, HTTPRequestFilter no)
    {
        super(filterName, null);
        
        yesFilter = yes;
        noFilter = no;
        predicate = p;
    }

    public HTTPFilterChain filterRequest(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state)
    {
        HTTPFilterChain myChain = new HTTPFilterChain(filterName, chain);
        try
        {
            if (predicate.test(request, response, state, myChain))
            {
                myChain.report = YES;
                return yesFilter.filterRequest(myChain, request, response, state);
            }
            else
            {
                myChain.report = NO;
                return noFilter.filterRequest(myChain, request, response, state);
            }
        }
        catch (Throwable t)
        {
            myChain.report = ERROR;
            myChain.error = t;
        }
        
        return myChain;
    }
}
