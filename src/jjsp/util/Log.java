package jjsp.util;

import java.io.*;
import java.nio.file.*;
import java.util.logging.*;

public class Log 
{
    public static void set() throws IOException 
    {
        set("jet_log");
    }

    public static void set(String directory) throws IOException 
    {
        int maxLength = 1024 * 1024 * 10;
        int count = 10;
        set(directory, maxLength, count, "log", new SimpleFormatter());
    }

    public static void set(String directory, int maxLength, int count) throws IOException 
    {
        set(directory, maxLength, count, "log", new SimpleFormatter());
    }

    public static void set(String directory, int maxLength, int count, String name, Formatter formatter) throws IOException 
    {
        Path dir = Paths.get(directory);
        dir.toFile().mkdirs();

        Path path = dir.resolve(name);
        FileHandler handler = new FileHandler(path.toString(), maxLength, count, true);
        handler.setFormatter(formatter);

        Logger.getGlobal().addHandler(handler);
        Logger.getGlobal().setUseParentHandlers(false);
    }
}
