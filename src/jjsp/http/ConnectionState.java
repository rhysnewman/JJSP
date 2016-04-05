package jjsp.http;

import java.io.*;
import java.util.*;

public class ConnectionState implements AutoCloseable
{
    private boolean closed = false;
    private HashMap state = new HashMap();

    public void put(String key, Object value) 
    {
        if (closed)
            throw new IllegalStateException("Connection State closed");
        state.put(key, value);
    }

    public Object get(String key) 
    {
        if (closed)
            throw new IllegalStateException("Connection State closed");
        return state.get(key);
    }

    public void close() throws Exception
    {
        closed = true;
        Iterator itt = state.values().iterator();
        while (itt.hasNext())
        {
            Object value = itt.next();
            if (value instanceof AutoCloseable)
            {
                try
                {
                    ((AutoCloseable) value).close();
                }
                catch (Throwable t) {}
            }
        }
    } 
}
