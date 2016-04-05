package jjsp.util;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;
import java.util.concurrent.*;

public interface JSONVisitor
{
    public default void reset()
    {
    }

    public default void finished(boolean wasComplete)
    {
    }
    
    public boolean visitElement(Object root, String propertyName, int arrayIndex, int arrayLength, Object value);

    public default boolean visitMapStart(Map m)
    {
        return true;
    }

    public default boolean visitMapEnd(Map m)
    {
        return true;
    }
    
    public default boolean visitListStart(List l)
    {
        return true;
    }

    public default boolean visitListEnd(List l)
    {
        return true;
    }
}
