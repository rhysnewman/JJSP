package jjsp.util;

import java.io.*;
import java.util.*;

/***
 Wrapper to java.util.Properties since I seem to keep repeating this pattern.
 */
public class Props extends Properties
{
    public final String configFilePath;
    
    private final Set<String> requiredParams;

    public Props(String configPath) throws IOException
    {
        this(new File(configPath));
    }

    public Props(File configFile) throws IOException
    {
        this(configFile, Collections.EMPTY_LIST);
    }

    public Props(String configPath, Collection<String> requiredParams) throws IOException
    {
        this(new File(configPath), requiredParams);
    }

    public Props(File configFile, Collection<String> requiredParams) throws IOException
    {
        this.configFilePath =  configFile.getPath();

        if (! configFile.isFile())
            throw new IllegalStateException("Cannot find specified properties file "+ configFile);

        this.requiredParams = new HashSet<>(requiredParams);

        Reader reader = new FileReader(configFile);
        try
        {
            load(reader);
        }
        finally
        {
            reader.close();
        }

        for (String arg : requiredParams)
            if (!containsKey(arg))
                throw new IllegalStateException("Missing required parameter : " + arg);
    }

    public File getFile(String param)
    {
        return new File(get(param));
    }

    public Boolean getBoolean(String param, boolean defaultValue)
    {
        if (contains(param))
            return Boolean.parseBoolean(get(param));
        return defaultValue;
    }

    public int getInt(String param, int defaultValue)
    {
        if (contains(param))
            return Integer.parseInt(get(param));
        return defaultValue;
    }

    public String get(String param)
    {
        return getProperty(param).trim();
    }

    public boolean contains(String param)
    {
        return getProperty(param) != null;
    }

    public String get(String param, String defaultValue)
    {
        return getProperty(param, defaultValue).trim();
    }

    public String[] required()
    {
        return requiredParams.toArray(new String[0]);
    }
}
