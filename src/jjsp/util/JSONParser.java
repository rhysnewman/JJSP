package jjsp.util;

import java.io.*;
import java.net.*;
import java.util.*;

import java.lang.reflect.*;

public class JSONParser
{
    private static char skipSpaces(String json, int[] pos)
    {
        while (true)
        {
            if (pos[0] >= json.length())
                return 0;
            char ch = json.charAt(pos[0]);
            if (Character.isWhitespace(ch))
                pos[0]++;
            else
                return ch;
        }
    }

    private static Boolean parseBoolean(String json, int[] pos)
    {
        if (json.regionMatches(pos[0], "true", 0, 4))
        {
            pos[0] += 4;
            return Boolean.TRUE;
        }

        if (json.regionMatches(pos[0], "false", 0, 5))
        {
            pos[0] += 5;
            return Boolean.FALSE;
        }

        return null;
    }

    private static Number parseNumber(String json, int[] pos)
    {
        int endPos = json.length();
        int startPos = pos[0];

        boolean foundExp = false;
        boolean foundDot = false;
        boolean allowPM = true;
        for (int i=startPos; i<endPos; i++)
        {
            char ch = json.charAt(i);
            if ((ch == 'e') || (ch == 'E'))
            {
                if (foundExp)
                    return null;
                allowPM = true;
                foundExp = true;
                continue;
            }

            if ((ch == '+') || (ch == '-'))
            {
                if (allowPM)
                {
                    allowPM = false;
                    ch = skipSpaces(json, pos);
                    if (ch == 0)
                        return null;
                    else
                        continue;
                }
                else
                    return null;
            }

            allowPM = false;
            if (ch == '.')
            {
                if (foundDot)
                    return null;
                foundDot = true;
                continue;
            }

            if (!Character.isDigit(json.charAt(i)))
            {
                pos[0] = endPos = i;
                break;
            }
        }

        if (startPos == endPos)
            return null;

        String numericString = json.substring(startPos, endPos);
        try
        {
            return Integer.parseInt(numericString);
        }
        catch (Exception e) {}

        try
        {
            return Long.parseLong(numericString);
        }
        catch (Exception e) {}

        try
        {
            return Double.parseDouble(numericString);
        }
        catch (Exception e) {}

        throw new IllegalStateException("Failed to parse JSON number at "+startPos+" '"+numericString+"'");
    }

    private static List parseArray(String json, int[] pos)
    {
        int start = pos[0];
        if (json.charAt(start) != '[')
            return null;
        pos[0]++;

        ArrayList result = new ArrayList();
        while (true)
        {
            char ch = skipSpaces(json, pos);
            if (ch == 0)
                break;
            else if (ch == ']')
            {
                pos[0]++;
                return result;
            }
            else if (ch == ',')
            {
                pos[0]++;
                if (skipSpaces(json, pos) == 0)
                    break;
            }

            Object val = parse(json, pos);
            result.add(val);
        }
        throw new IllegalStateException("json Array format at "+start+" ["+(pos[0]-start)+"]  '"+json.substring(start)+"'");
    }

    private static Map parseObject(String json, int[] pos)
    {
        int start = pos[0];
        if (json.charAt(start) != '{')
            return null;
        pos[0]++;

        Map result = new LinkedHashMap();
        while (true)
        {
            char ch = skipSpaces(json, pos);
            if (ch == 0)
                break;
            else if (ch == '}')
            {
                pos[0]++;
                return result;
            }
            else if (ch == ',')
            {
                pos[0]++;
                if (skipSpaces(json, pos) == 0)
                    break;
            }

            String key = parseString(json, pos);
            ch = skipSpaces(json, pos);
            if (ch == 0)
                break;

            pos[0]++;
            if (ch != ':')
                break;

            Object val = parse(json, pos);
            result.put(key, val);
        }
        throw new IllegalStateException("json Object format at "+pos[0]+"  ["+start+", "+json.length()+"]  '"+json.substring(pos[0])+"'");
    }

    private static String parseString(String json, int[] pos)
    {
        int startPos = pos[0];
        if (json.charAt(startPos) != '"')
            return null;
        pos[0]++;

        boolean isEscape = false;
        StringBuffer buf = new StringBuffer();

        for (int i=startPos+1; i<json.length(); i++)
        {
            char ch = json.charAt(i);
            if (ch == '\\')
            {
                if (!isEscape)
                {
                    isEscape = true;
                    continue;
                }
            }

            if (ch == '"')
            {
                if (!isEscape)
                {
                    pos[0] = i+1;
                    return buf.toString();
                }
            }

            if (isEscape)
            {
                if (ch == '\\')
                    buf.append('\\');
                else if (ch == '/')
                    buf.append('/');
                else if (ch == 'b')
                    buf.append('\b');
                else if (ch == 'f')
                    buf.append('\f');
                else if (ch == 'n')
                    buf.append('\n');
                else if (ch == 'r')
                    buf.append('\r');
                else if (ch == 't')
                    buf.append('\t');
                else
                    buf.append(ch);
            }
            else
                buf.append(ch);

            isEscape = false;
        }

        throw new IllegalStateException("json string at at "+startPos+"  '"+json+"'");
    }

    private static Object parse(String json, int[] pos)
    {
        char ch = skipSpaces(json, pos);
        if (ch == 0)
            return null;
        int startPos = pos[0];
        if (startPos == json.length())
            return null;

        Object result = parseArray(json, pos);
        if (result != null)
            return result;

        result = parseObject(json, pos);
        if (result != null)
            return result;

        result = parseBoolean(json, pos);
        if (result != null)
            return result;

        result = parseString(json, pos);
        if (result != null)
            return result;

        result = parseNumber(json, pos);
        if (result != null)
            return result;

        if (json.regionMatches(pos[0], "null", 0, 4))
        {
            pos[0] += 4;
            return null;
        }

        throw new IllegalStateException("json object at at "+startPos+"  '"+json+"'");
    }

    public static Object parse(Object json)
    {
        if (json == null)
            return null;
        return parse(json.toString());
    }

    public static Object parse(String json)
    {
        if (json == null)
            return null;
        return parse(json, new int[1]);
    }

    private static void escapeString(String s, StringBuffer buf)
    {
        buf.append('"');
        for (int i=0; i<s.length(); i++)
        {
            char ch = s.charAt(i);

            if (ch == '"')
                buf.append("\\\"");
            else if (ch == '\\')
                buf.append("\\\\");
            else if (ch == '\b')
                buf.append("\\b");
            else if (ch == '\f')
                buf.append("\\f");
            else if (ch == '\n')
                buf.append("\\n");
            else if (ch == '\r')
                buf.append("\\r");
            else if (ch == '\t')
                buf.append("\\t");
            else
                buf.append(ch);
        }
        buf.append('"');
    }

    private static void toString(Object obj, StringBuffer buf, boolean withNewlines)
    {
        if (obj == null)
            buf.append("null");
        else if ((obj instanceof Boolean) || (obj instanceof Number))
            buf.append(obj.toString());
        else if (obj instanceof Map)
        {
            Map m = (Map) obj;
            if (m.size() == 0)
            {
                buf.append("{}");
                return;
            }
            
            boolean first = true;
            Iterator itt = m.keySet().iterator();

            buf.append('{');
            while (itt.hasNext())
            {
                if (!first)
                    buf.append(',');
                if (withNewlines)
                    buf.append('\n');
                
                String s = (String) itt.next();
                Object val = m.get(s);
                escapeString(s, buf);
                buf.append(":");
                toString(val, buf, withNewlines);
                first = false;
            }
            
            if (withNewlines)
                buf.append("\n}");
            else
                buf.append('}');
        }
        else if (obj instanceof Object[])
        {
            Object[] l = (Object[]) obj;
            if (l.length == 0)
            {
                buf.append("[]");
                return;
            }

            boolean first = true;
            buf.append('[');
            for (int i=0; i<l.length; i++)
            {
                if (!first)
                    buf.append(',');
                if (withNewlines)
                    buf.append('\n');
                
                toString(l[i], buf, withNewlines);
                first = false;
            }
            
            if (withNewlines)
                buf.append("\n]");
            else
                buf.append(']');
        }
        else if (obj instanceof List)
        {
            List l = (List) obj;
            if (l.size() == 0)
            {
                buf.append("[]");
                return;
            }
            
            boolean first = true;
            Iterator itt = l.iterator();

            buf.append('[');
            while (itt.hasNext())
            {
                if (!first)
                    buf.append(',');
                if (withNewlines)
                    buf.append('\n');
                
                Object val = itt.next();
                toString(val, buf, withNewlines);
                first = false;
            }
            
            if (withNewlines)
                buf.append("\n]");
            else
                buf.append(']');
        }
        else if (obj instanceof String)
            escapeString(obj.toString(), buf);
        else if (obj instanceof JSONable)
            buf.append(((JSONable) obj).toJSONString());
        else
        {
            try
            {
                Class cls = obj.getClass();
                Method m = cls.getDeclaredMethod("toJSON", new Class[0]);
                Object jsonObj = m.invoke(obj, new Object[0]);
                buf.append(toString(jsonObj, withNewlines));
            }
            catch (Exception e)
            {
                escapeString(obj.toString(), buf);
            }
        }
    }
    
    public static String toString(Object obj)
    {
        return toString(obj, false);
    }
    
    public static String toString(Object obj, boolean withNewlines)
    {
        StringBuffer buf = new StringBuffer();
        toString(obj, buf, withNewlines);
        return buf.toString();
    }

    public static String stripWhitespace(String src)
    {
        boolean inQuote = false, isEscaped = false;
        StringBuffer buf = new StringBuffer();

        for (int i=0; i<src.length(); i++)
        {
            char ch = src.charAt(i);

            if (!inQuote)
            {
                if (ch == '"')
                {
                    inQuote = true;
                    isEscaped = false;
                }
                else if (Character.isWhitespace(ch))
                    continue;
            }
            else if (inQuote)
            {
                if (ch == '\\')
                    isEscaped = !isEscaped;
                else if ((ch == '"') && !isEscaped)
                    inQuote = false;
            }

            buf.append(ch);
        }

        return buf.toString();
    }

    public static Object getValue(Object json, String path)
    {
        String[] parts = path.split("\\.");

        for (int i=0; i<parts.length; i++)
        {
            int index = -1;
            String key = parts[i];

            if (key.endsWith("]"))
            {
                int b = key.indexOf("[");
                try
                {
                    index = Integer.parseInt(key.substring(b+1, key.length()-1));
                    key = key.substring(0, b);
                }
                catch (Exception e)
                {
                    throw new IllegalStateException("Path syntax error - invalid index ");
                }
            }

            if ((json != null) && (json instanceof Map))
                json = ((Map) json).get(key);
            else
                return null;

            if (index >= 0)
            {
                if ((json != null) && (json instanceof List))
                    json = ((List) json).get(index);
                else
                    return null;
            }
        }

        return json;
    }

    private static boolean visitElements(Object root, Object obj, JSONVisitor visitor)
    {
        if (obj instanceof Map)
        {
            Map m = (Map) obj;
            if (!visitor.visitMapStart(m))
                return false;

            Iterator itt = m.keySet().iterator();
            while (itt.hasNext())
            {
                String k = (String) itt.next();
                Object val = m.get(k);
                if (!visitor.visitElement(root, k, -1, -1, val))
                    return false;

                if ((val instanceof List) || (val instanceof Map))
                {
                    if (!visitElements(root, val, visitor))
                        return false;
                }
            }

            if (!visitor.visitMapEnd(m))
                return false;
        }
        else if (obj instanceof List)
        {
            List l = (List) obj;
            if (!visitor.visitListStart(l))
                return false;

            Iterator itt = l.iterator();
            for (int i=0; itt.hasNext(); i++)
            {
                Object val = itt.next();
                if (!visitor.visitElement(root, null, i, l.size(), val))
                    return false;

                if ((val instanceof List) || (val instanceof Map))
                {
                    if (!visitElements(root, val, visitor))
                        return false;
                }
            }

            if (!visitor.visitListEnd(l))
                return false;
        }
        else if (obj != null)
            return visitor.visitElement(root, null, -1, -1, obj);

        return true;
    }

    public static boolean visitElements(Object obj, JSONVisitor visitor)
    {
        visitor.reset();
        boolean wasComplete = visitElements(obj, obj, visitor);
        visitor.finished(wasComplete);
        return wasComplete;
    }

    private static String findIndent(StringBuffer src)
    {
        StringBuffer buf = new StringBuffer(" ");
        for (int i=src.length()-2; i>=0; i--)
        {
            if (src.charAt(i) == '\n')
                break;
            else
                buf.append(" ");
        }
        return buf.toString();
    }

    private static void prettyPrint(StringBuffer buf, Object obj)
    {
        if (obj instanceof Map)
        {
            Map m = (Map) obj;
            if (m.size() == 0)
            {
                buf.append("{}\n");
                return;
            }

            String indent = findIndent(buf);
            buf.append("{\n");

            Iterator itt = m.keySet().iterator();
            while (itt.hasNext())
            {
                String key = itt.next().toString();
                buf.append(" "+indent+key+": ");
                prettyPrint(buf, m.get(key));
            }

            buf.append(indent+"}\n");
        }
        else if (obj instanceof List)
        {
            List l = (List) obj;
            if (l.size() == 0)
            {
                buf.append("[]\n");
                return;
            }

            String indent = findIndent(buf);
            buf.append("[\n");

            for (int i=0; i<l.size(); i++)
            {
                buf.append(" "+indent);
                prettyPrint(buf, l.get(i));
            }
            buf.append(indent+"]\n");
        }
        else if (obj instanceof Object[])
        {
            Object[] array = (Object[]) obj;
            if (array.length == 0)
            {
                buf.append("[]\n");
                return;
            }

            String indent = findIndent(buf);
            buf.append("[\n");

            for (int i=0; i<array.length; i++)
            {
                buf.append(" "+indent);
                prettyPrint(buf, array[i]);
            }
            buf.append(indent+"]\n");
        }
        else if (obj instanceof String)
        {
            String indent = findIndent(buf);
            String s = ((String) obj).replace("\n", "\n"+indent);
            buf.append("\""+s+"\"\n");
        }
        else if (obj instanceof JSONable)
            prettyPrint(buf, ((JSONable) obj).toJSON());
        else
        {
            try
            {
                Method m = obj.getClass().getDeclaredMethod("toJSON", new Class[0]);
                m.setAccessible(true);
                
                Object result = m.invoke(obj, new Object[0]);
                if (result instanceof String)
                    result = parse((String) result);
                prettyPrint(buf, result);
            }
            catch (Throwable e)
            {
                buf.append(obj+"\n");
            }
        }
    }

    public static String prettyPrint(Object obj)
    {
        StringBuffer buf = new StringBuffer();
        prettyPrint(buf, obj);
        return buf.toString();
    }
}
