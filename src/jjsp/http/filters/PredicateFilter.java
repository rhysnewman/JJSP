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
