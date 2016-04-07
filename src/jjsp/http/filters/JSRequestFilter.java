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
import java.lang.invoke.*;
import javax.script.*;

import jjsp.http.*;
import jjsp.util.*;

public class JSRequestFilter extends AbstractRequestFilter
{
    private Invocable invocable;

    public JSRequestFilter(String jsFunctionName, ScriptEngine engine, HTTPRequestFilter filterChain) throws Exception
    {
        super(jsFunctionName, filterChain);  
        this.invocable = (Invocable) engine;
    }

    protected boolean handleRequest(HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException
    {
        try 
        {
            return ((Boolean) invocable.invokeFunction(getName(), request, response, state)).booleanValue();
        } 
        catch (Throwable e) 
        {
            throw new IOException("Error in JS Request Filter function '"+getName()+"'", e);
        }
    }

    public static JSRequestFilter createJSFilter(String jsFunctionName, String jsSource) throws Exception
    {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("nashorn");
        engine.eval(jsSource);
        return new JSRequestFilter(jsFunctionName, engine, null);
    }
}
