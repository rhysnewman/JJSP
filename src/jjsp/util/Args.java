package jjsp.util;

import java.util.*;

public class Args
{
    private static Map<String, String> params = new LinkedHashMap();

    public static Map parseArgs(String argList)
    {
        String[] args = argList.split(" ");
        for (int i=0; i<args.length; i++)
            args[i] = args[i].trim();

        return parseArgs(args);
    }

    public static Map parseArgs(String[] args)
    {
        return parseArgs(args, 0);
    }

    public static Map parseArgs(String[] args, int startIndex)
    {
        Map pp = new LinkedHashMap();

        for (int i=startIndex; i<args.length; i++)
        {
            String argName = args[i].toLowerCase();
            if (argName.startsWith("-"))
                argName = argName.substring(1);

            if ((i == args.length-1) || args[i+1].startsWith("-"))
                pp.put(argName, "true");
            else
                pp.put(argName, args[++i]);
        }
        
        return pp;
    }

    public static String toArgString(Map argMap)
    {
        if ((argMap == null) || (argMap.size() == 0))
            return "";

        StringBuffer buf = new StringBuffer();
        Iterator itt = argMap.keySet().iterator();
        while (itt.hasNext())
        {
            String key = (String) itt.next();
            String value = argMap.get(key).toString();
            buf.append("-"+key.toLowerCase()+" "+value+" ");
        }

        return buf.substring(0, buf.length()-1);
    }

    public static synchronized Map<String, String> parse(String[] args, int startIndex)
    {
        params = parseArgs(args, startIndex);
        Map<String, String> pp = new LinkedHashMap<String, String>();
        pp.putAll(params);
        return pp;
    }

    public static synchronized Map<String, String> parse(String[] args)
    {
        params = parseArgs(args);
        Map<String, String> pp = new LinkedHashMap<String, String>();
        pp.putAll(params);
        return pp;
    }

    public static synchronized String getArg(String param, String def)
    {
        param = param.toLowerCase();
        if (!params.containsKey(param))
            return def;
        return params.get(param);
    }

    public static synchronized String getArg(String param)
    {
        param = param.toLowerCase();
        if (!params.containsKey(param))
            throw new IllegalStateException("No parameter: "+param);
        return params.get(param);
    }

    public static synchronized boolean hasArg(String arg)
    {
        return params.containsKey(arg.toLowerCase());
    }

    public static synchronized boolean getBoolean(String param, boolean def)
    {
        param = param.toLowerCase();
        if (!params.containsKey(param))
            return def;
        return "true".equals(params.get(param));
    }

    public static synchronized int getInt(String param, int def)
    {
        param = param.toLowerCase();
        if (!params.containsKey(param))
            return def;
        return Integer.parseInt(params.get(param));
    }

    public static synchronized int getInt(String param)
    {
        param = param.toLowerCase();
        if (!params.containsKey(param))
            throw new IllegalStateException("No parameter: "+param);
        return Integer.parseInt(params.get(param));
    }

    public static synchronized long getLong(String param)
    {
        param = param.toLowerCase();
        if (!params.containsKey(param))
            throw new IllegalStateException("No parameter: "+param);
        return Long.parseLong(params.get(param));
    }

    public static synchronized double getDouble(String param)
    {
        param = param.toLowerCase();
        if (!params.containsKey(param))
            throw new IllegalStateException("No parameter: "+param);
        return Double.parseDouble(params.get(param));
    }

    public static synchronized String getFirstArg(String[] paramNames, String def)
    {
        for (int i=0; i<paramNames.length; i++)
        {
            String param = paramNames[i].toLowerCase();
            String result = getArg(param, null);
            if (result != null)
                return result;
        }
        return def;
    }
}
