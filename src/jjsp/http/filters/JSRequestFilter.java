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
