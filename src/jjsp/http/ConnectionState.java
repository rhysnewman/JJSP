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
