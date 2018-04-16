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
package jjsp.util;

import java.util.*;

public class Args
{
    private static Map<String, String> params = new LinkedHashMap<>();

    public static Map<String, String> parseArgs(String argList)
    {
        String[] args = argList.split("\\s+");
        for (int i=0; i<args.length; i++)
            args[i] = args[i].trim();

        return parseArgs(args);
    }

    public static Map<String, String> parseArgs(String[] args)
    {
        return parseArgs(args, 0);
    }

    public static Map<String, String> parseArgs(String[] args, int startIdx)
    {
        // simple arg parser - accepts boolean flags and string values
        // boolean flags will be mapped to 'null' in the arg map
        // use quotes to group argument values - DO NOT mix and match quote marks
        Map<String, String> map = new LinkedHashMap<>();

        String arg = null, value = null;
        char quote = 0;
        for ( int i = startIdx; i < args.length; i++ )
        {
            String str = args[i];
            if ( i == startIdx && !str.startsWith("-") )
                continue;

            str = str.replaceAll("\\s+", " ");
            if ( str.startsWith("-") )
            {
                if ( arg != null )
                    map.put(arg, value);
                // new arg
                arg = str.substring(1).toLowerCase();
                value = null;
            }
            else if ( str.startsWith("'") || str.startsWith("\"") )
            {
                value = str.substring(1);
                quote = str.charAt(0);
            }
            else if ( quote != 0 )
            {
                if ( str.charAt(str.length() - 1) == quote ) {
                    str = str.substring(0, str.length() -1);
                    quote = 0;
                }
                value += " " + str;
            }
            else
                value = str;
        }
        if ( arg != null )
            map.put(arg, value);

        return map;
    }

    public static String toArgString(Map<String, String> argMap)
    {
        if ((argMap == null) || (argMap.size() == 0))
            return "";

        StringBuilder builder = new StringBuilder();
        for ( String key : argMap.keySet() ) {
            builder.append("-" + key + " ");
            String value = argMap.get(key);
            if ( value != null ) {
                if ( value.contains(" ") )
                    value = "'" + value + "'";
                builder.append(value + " ");
            }
        }
        return builder.toString().trim();
    }

    public static synchronized Map<String, String> parse(String[] args, int startIndex)
    {
        params = parseArgs(args, startIndex);
        return new LinkedHashMap<>(params);
    }

    public static synchronized Map<String, String> parse(String[] args)
    {
        params = parseArgs(args);
        return new LinkedHashMap<>(params);
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
        return hasArg(param) || def;
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
