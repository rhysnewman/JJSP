package jjsp.util;

import java.io.*;

public interface JSONable
{
    public Object toJSON();

    default public String toJSONString()
    {
        return JSONParser.toString(toJSON());
    }
}
