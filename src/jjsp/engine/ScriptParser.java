package jjsp.engine;

import java.io.*;
import java.util.*;

import jjsp.util.*;

public class ScriptParser
{
    public static final int ORDINARY = 0;
    public static final int ESCAPED = 1;
    public static final int CONTINUATION = 2;
    public static final int COMMENT_LINE = 3;
    public static final int COMMENT_REGION = 4;
    public static final int VERBATIM_TEXT = 5;
    public static final int JAVASCRIPT_SOURCE = 6;
    public static final int SINGLE_QUOTED = 7;

    public static final int DOUBLE_QUOTED = 8;
    public static final int DOUBLE_QUOTED_EXPRESSION = 9;
    public static final int DOUBLE_QUOTED_BRACKETED_EXPRESSION = 10;

    public static final int EXPRESSION = 11;
    public static final int BRACKETED_EXPRESSION = 12;

    public static final int KEYWORD = 13;
    public static final int KEYWORD_WITH_ARGS = 14;

    public static final int ARGS = 15;
    public static final int ARG_QUOTED = 16;
    public static final int ARG_EXPRESSION = 17;
    public static final int ARG_BRACKETED_EXPRESSION = 18;

    private static final int DEFAULT_FOREACH_LIMIT = 10000;

    private static final String[] SINGLE_KEYWORDS = {"#end", "#{end}", "#break", "#{break}", "#continue", "#{continue}", "#elseif", "#{elseif}", "#else", "#{else}"};

    private static final String ANNOTATION_CHARS = "_EqQSDcCvJBbXwYmkpRU";

    private CharSequence src;
    private int[] annotations;

    public ScriptParser(CharSequence src)
    {
        setSource(src);
    }

    private boolean isIdentifierPart(CharSequence src, int index)
    {
        if (index >= src.length())
            return false;
        char ch = src.charAt(index);
        return (ch == '_') || (ch == '$') || Character.isLetterOrDigit(ch);
    }
    
    private boolean regionMatches(int index, String target)
    {
        if (target == null)
            return false;

        int tlen = target.length();
        if (index + tlen > src.length())
            return false;

        for (int i=0, j=index; i<tlen; i++, j++)
            if (src.charAt(j) != target.charAt(i))
                return false;
    
        return true;
    }

    private boolean isEscaped(int index)
    {
        boolean isEscaped = false;
        for (int i=index-1; i>=0; i--)
        {
            if (src.charAt(i) != '\\')
                break;
            isEscaped = !isEscaped;
        }
        return isEscaped;
    }

    public void setSource(CharSequence src)
    {
        if (src == null)
            src = "";
        this.src = src;
        annotations = new int[src.length()];
        Arrays.fill(annotations, ORDINARY);

        int nestLevel = 0;
        int regionType = ORDINARY, nextRegion = ORDINARY;
        String regionEnd = null, regionNestStart = null, regionNestEnd = null;
        for (int i=0; i<src.length(); i++)
        {
            if (annotations[i] != ORDINARY)
                continue;
            
            char ch = src.charAt(i);
            nextRegion = ORDINARY;

            if (regionType == ORDINARY)
            {
                if (ch == '\\')
                {
                    regionType = ESCAPED;
                    regionEnd = "\\\\";
                    if (regionMatches(i, "\\\n"))
                        regionType = CONTINUATION;
                }
                else if (ch == '"')
                    regionType = DOUBLE_QUOTED;
                else if (ch == '\'')
                    regionType = SINGLE_QUOTED;
                else if (regionMatches(i, "\\\n"))
                {
                    regionType = CONTINUATION;
                    regionEnd = "\\\n";
                }
                else if (regionMatches(i, "##"))
                    regionType = COMMENT_LINE;
                else if (regionMatches(i, "#*"))
                    regionType = COMMENT_REGION;
                else if (regionMatches(i, "#[["))
                    regionType = VERBATIM_TEXT;
                else if (regionMatches(i, "<%"))
                    regionType = JAVASCRIPT_SOURCE;
                else if (ch == '#')
                {
                    String singleKeyword = null;
                    for (int j=0; j<SINGLE_KEYWORDS.length; j++)
                        if (regionMatches(i, SINGLE_KEYWORDS[j]) && !isIdentifierPart(src, i + SINGLE_KEYWORDS[j].length()))
                        {
                            singleKeyword = SINGLE_KEYWORDS[j];
                            break;
                        }

                    if ((singleKeyword != null) && (singleKeyword.indexOf("elseif") < 0))
                    {
                        regionType = KEYWORD;
                        regionEnd = singleKeyword;
                    }
                    else
                        regionType = KEYWORD_WITH_ARGS;
                }
                else if (regionMatches(i, "${"))
                {
                    nestLevel = 0;
                    regionNestStart = "{";
                    regionNestEnd = "}";
                    regionType = BRACKETED_EXPRESSION;
                }
                else if (ch == '$')
                    regionType = EXPRESSION;
            }
            else if (regionType == ARGS)
            {
                if (ch == '(')
                    nestLevel++;
                else if (ch == ')')
                {
                    nestLevel--;
                    if (nestLevel == 0)
                        regionEnd = regionNestEnd;
                }
                else if ((ch == '"') && !isEscaped(i))
                    regionType = ARG_QUOTED;
            }
            else if (regionType == ARG_QUOTED)
            {
                if (regionMatches(i, "${"))
                    regionType = ARG_BRACKETED_EXPRESSION;
                else if (regionMatches(i, "$"))
                    regionType = ARG_EXPRESSION;
                else if ((ch == '"') && !isEscaped(i))
                {
                    regionEnd = "\"";
                    nextRegion = ARGS;
                }
            }
            else if (regionType == ARG_BRACKETED_EXPRESSION)
            {
                if (regionMatches(i, "}"))
                {
                    regionEnd = "}";
                    nextRegion = ARG_QUOTED;
                }
            }
            else if (regionType == ARG_EXPRESSION)
            {
                if (!Character.isLetterOrDigit(ch) && (ch != '_') && (ch != '$'))
                {
                    regionType = ARG_QUOTED;
                    if ((ch == '"') && !isEscaped(i))
                    {
                        regionEnd = "\"";
                        nextRegion = ARGS;
                    }
                }
            }
            else if (regionType == DOUBLE_QUOTED)
            {
                if ((ch == '"') && !isEscaped(i))
                    regionEnd = "\"";
                else if (regionMatches(i, "${"))
                    regionType = DOUBLE_QUOTED_BRACKETED_EXPRESSION;
                else if (regionMatches(i, "$"))
                    regionType = DOUBLE_QUOTED_EXPRESSION;
            }
            else if (regionType == DOUBLE_QUOTED_EXPRESSION)
            {
                if (!Character.isLetterOrDigit(ch) && (ch != '_') && (ch != '$'))
                {
                    if ((ch == '"') && !isEscaped(i))
                        regionEnd = "\"";
                    regionType = DOUBLE_QUOTED;
                }
            }
            else if (regionType == DOUBLE_QUOTED_BRACKETED_EXPRESSION)
            {
                if (regionMatches(i, "}"))
                {
                    regionEnd = "}";
                    nextRegion = DOUBLE_QUOTED;
                }
            }
            else if (regionType == SINGLE_QUOTED) 
            {
                if ((ch == '\'') && !isEscaped(i))
                    regionEnd = "'";
            }
            else if (regionType == COMMENT_LINE)
            {
                if ((ch == '\n') && !isEscaped(i))
                    regionEnd = "\n";
            }
            else if (regionType == COMMENT_REGION)
            {
                if (regionMatches(i, "*#"))
                    regionEnd = "*#";
            }
            else if (regionType == VERBATIM_TEXT)
            {
                if (regionMatches(i, "]]#"))
                    regionEnd = "]]#";
            }
            else if (regionType == JAVASCRIPT_SOURCE)
            {
                if (regionMatches(i, "%>"))
                    regionEnd = "%>";
            }
            else if (regionType == KEYWORD_WITH_ARGS)
            {
                if (ch == '(') 
                {
                    nestLevel = 1;
                    regionNestStart = "(";
                    regionNestEnd = ")";
                    regionType = ARGS;
                }
            }
            else if (regionType == EXPRESSION)
            {
                if (!Character.isLetterOrDigit(ch) && (ch != '_') && (ch != '$'))
                    regionType = ORDINARY;
            }
            else if (regionType == BRACKETED_EXPRESSION)
            {  
                if (ch == '{')
                    nestLevel++;
                else if (ch == '}')
                {
                    nestLevel--;
                    if (nestLevel == 0)
                        regionEnd = regionNestEnd;
                }
            }
            else if (regionMatches(i, regionNestStart))
                nestLevel++;
            else if (regionMatches(i, regionNestEnd))
            {
                nestLevel--;
                if (nestLevel == 0)
                    regionEnd = regionNestEnd;
            }

            if (regionEnd != null)
            {
                int pos = Math.min(src.length(), i + regionEnd.length());
                Arrays.fill(annotations, i, pos, regionType);

                regionType = nextRegion;
                regionEnd = null;
                i = pos-1;
            }
            else if (i<src.length())
                annotations[i] = regionType;
        }
    }

    public int[] getAnnotations()
    {
        return annotations;
    }

    public CharSequence getSource()
    {
        return src;
    }

    public void printWithAnnotations()
    {
        printWithAnnotations(System.out);
    }

    public void printWithAnnotations(PrintStream ps)
    {
        int lineStart = 0;
        for (int i=0; i<src.length(); i++)
        {
            char ch = src.charAt(i);
            ps.print(ch);

            boolean isEOL = (ch == '\n') || (i == src.length()-1);
            if (isEOL)
            {
                if (ch != '\n')
                    ps.println();
                    
                for (int j=lineStart; j<=i; j++)
                    ps.print(ANNOTATION_CHARS.charAt(Math.max(0, annotations[j])));
                ps.println();
                lineStart = i+1;
            }
        }
    }

    public class ScriptPart
    {
        public final int startIndex, endIndex, type;
        
        public ScriptPart(int start, int end, int type)
        {
            this.type = type;
            startIndex = start;
            endIndex = end;
        }

        public boolean eol()
        {
            return src.charAt(endIndex) == '\n';
        }

        public String toString()
        {
            return src.subSequence(startIndex, Math.min(src.length(), endIndex+1)).toString();
        }

        public String toStringNoEOL()
        {
            if (eol())
                return src.subSequence(startIndex, endIndex).toString();
            return toString();
        }
    }

    public ScriptPart[] getParts()
    {
        if (annotations.length == 0)
            return new ScriptPart[0];

        ArrayList buffer = new ArrayList();
        int start = 0, type = annotations[0];

        for (int i=0; i<annotations.length; i++)
        { 
            if (src.charAt(i) == '\n')
            {
                if (i >= start)
                    buffer.add(new ScriptPart(start, i, type));
                start = i+1;
                if (i < annotations.length-1)
                    type = annotations[i+1];
            }
            else if (annotations[i] != type)
            {
                if (i-1 >= start)
                    buffer.add(new ScriptPart(start, i-1, type));
                type = annotations[i];
                start = i;
            }
        }

        if (start < annotations.length)
            buffer.add(new ScriptPart(start, annotations.length-1, type));
        
        ScriptPart[] result = new ScriptPart[buffer.size()];
        buffer.toArray(result);
        return result;
    }

    private static String hex(char ch) 
    {
        return Integer.toHexString(ch).toUpperCase(Locale.ENGLISH);
    }

    private static String escapeJSString(String src)
    {
        StringBuffer buf = new StringBuffer();

        for (int i=0; i<src.length(); i++) 
        {
            char ch = src.charAt(i);
            
            if (ch > 0xfff)
                buf.append("\\u" + hex(ch));
            else if (ch > 0xff) 
                buf.append("\\u0" + hex(ch));
            else if (ch > 0x7f) 
                buf.append("\\u00" + hex(ch));
            else 
            {
                switch (ch) 
                {
                case '\b':
                    buf.append("\\b");
                    break;
                case '\n':
                    buf.append("\\n");
                    break;
                case '\t':
                    buf.append("\\t");
                    break;
                case '\f':
                    buf.append("\\f");
                    break;
                case '\r':
                    buf.append("\\r");
                    break;
                case '"':
                    buf.append("\\\"");
                    break;
                case '\\':
                    buf.append("\\\\");
                    break;
                default:
                    if (ch < 32)
                    {
                        if (ch > 0xf)
                            buf.append("\\u00" + hex(ch));
                        else
                            buf.append("\\u000" + hex(ch));
                    }
                    else
                        buf.append(ch);
                }
            }
        }
        return buf.toString();
    }

    private String stripMarkers(String s, String prefix, String postfix)
    {
        if (s.startsWith(prefix))
            s = s.substring(prefix.length());
        if (s.endsWith(postfix))
            s = s.substring(0, s.length() - postfix.length());
        return s;
    }

    private boolean jsNeedsPlus(String jsSrc)
    {
        if ((jsSrc == null) || (jsSrc.length() == 0))
            return false;
        char ch = jsSrc.charAt(jsSrc.length()-1);
        return (ch != '=') && (ch != '+') && (ch != '(') && (ch != '+') && (ch != ',') && (ch != '[') && (ch != '{') && (ch != ':');
    }

    private String stripLeadingDollars(String s)
    {
        boolean dollar = false;
        StringBuffer buf = new StringBuffer();
        for (int i=0; i<s.length(); i++)
        {
            char ch = s.charAt(i);
            if (ch == '$')
            {
                if (dollar)
                    buf.append(ch);
                dollar = !dollar;
            }
            else
            {
                buf.append(ch);
                dollar = false;
            }
        }
        return buf.toString();
    }

    private boolean isStartOfStatement(String fullExpression, String keyword)
    {
        return fullExpression.startsWith(keyword) && !isIdentifierPart(fullExpression, keyword.length());
    }

    public String translateToJavascript()
    {
        return translateToJavascript(DEFAULT_FOREACH_LIMIT);
    }

    public String translateToJavascript(int foreachLimit)
    {
        ScriptPart[] parts = getParts();
        StringWriter writer = new StringWriter();
        
        Stack openingBraceType = new Stack();
        boolean isInVerbatim = false;
        String currentKeywordWithArgs = null;
        
        for (int i=0; i<parts.length; i++)
        {
            ScriptPart p = parts[i];
            String ss = p.toStringNoEOL();
            boolean println = true;
            
            switch (p.type)
            {
            case CONTINUATION:
                println = false;
                break;
            case COMMENT_LINE:
                writer.write("/*"+ss.substring(2).replace("*/", "*\\/")+"*/");
                println = false;
                break;
            case COMMENT_REGION:
                writer.write("/*"+stripMarkers(ss.replace("*/", "*\\/"), "#*", "*#")+"*/");
                println = false;
                break;
            case JAVASCRIPT_SOURCE:
                boolean defaultAddSemicolon = ss.endsWith("%>");
                String js = stripMarkers(ss, "<%", "%>").trim();
                if (defaultAddSemicolon && !js.endsWith(";") && (js.length() > 0))
                    js = js+";";
                writer.write(js);
                println = false;
                break;
            case BRACKETED_EXPRESSION:
            case DOUBLE_QUOTED_BRACKETED_EXPRESSION:
                ss = stripMarkers(ss, "${", "}");
                writer.write("$p("+stripLeadingDollars(ss)+");");
                println = false;
                break;
            case EXPRESSION:
            case DOUBLE_QUOTED_EXPRESSION:
                ss = stripMarkers(ss, "$", "");
                writer.write("$p("+stripLeadingDollars(ss)+");");
                println = false;
                break;
            case KEYWORD:
                ss = stripMarkers(ss, "#{", "}");
                ss = stripMarkers(ss, "#", "");
                ss = ss.trim();
                
                if (ss.equals("else"))
                    writer.write("}else{");
                else if (ss.equals("break"))
                    writer.write("break;");
                else if (ss.equals("continue"))
                    writer.write("continue;");
                else if (ss.equals("end"))
                {
                    if (openingBraceType.size() > 0) 
                    {
                        String blockType = (String) openingBraceType.pop();
                        if (blockType.equals("FOREACH"))
                            writer.write("}}());");
                        else if (blockType.equals("MACRO"))
                            writer.write("return macroResult;};");
                        else if (blockType.equals("MACRO_CALL_WITH_BODY"))
                            writer.write("return macroResult;}));");
                        else
                            writer.write("}");
                    }
                    else
                        writer.write("}");
                }
                println = false;
                break;
            case KEYWORD_WITH_ARGS:
                ss = stripMarkers(ss, "#{", "}");
                ss = stripMarkers(ss, "#", "");
                currentKeywordWithArgs = ss.trim();
                println = false;
                break;
            case ARG_QUOTED:
                ss = stripLeadingDollars(ss);
                if (jsNeedsPlus(currentKeywordWithArgs))
                    currentKeywordWithArgs += "+";
                currentKeywordWithArgs += "\""+escapeJSString(stripMarkers(ss, "\"", "\""))+"\"";
                println = false;
                break;
            case ARG_BRACKETED_EXPRESSION:
                ss = stripMarkers(ss, "${", "}");
                ss = stripLeadingDollars(ss).trim();
                if (jsNeedsPlus(currentKeywordWithArgs))
                    currentKeywordWithArgs += "+";
                currentKeywordWithArgs += "("+ss+")";
                println = false;
                break;
            case ARG_EXPRESSION:
                ss = stripMarkers(ss, "$", "");
                ss = stripLeadingDollars(ss).trim();
                if (jsNeedsPlus(currentKeywordWithArgs))
                    currentKeywordWithArgs += "+";
                currentKeywordWithArgs += "("+ss+")";
                println = false;
                break;
            case ARGS:
                ss = stripLeadingDollars(ss);
                currentKeywordWithArgs += ss.trim();
                println = false;
                
                if (currentKeywordWithArgs.endsWith(")"))
                {
                    if (isStartOfStatement(currentKeywordWithArgs, "set"))
                    {
                        int eq = currentKeywordWithArgs.indexOf("=", 0);
                        if (eq >= 0)
                        {
                            String varName = currentKeywordWithArgs.substring(4, eq);
                            String varValue = "";
                            try
                            {
                                varValue = currentKeywordWithArgs.substring(eq+1, currentKeywordWithArgs.length()-1);
                            }
                            catch (Exception e) {}
                            if (varValue.length() == 0)
                                varValue = "{}";
                            writer.write("var "+varName+"="+varValue+";");
                        }
                    }
                    else if (isStartOfStatement(currentKeywordWithArgs, "if"))
                    {
                        writer.write(currentKeywordWithArgs+"{");
                        openingBraceType.push("BRACE");
                    }
                    else if (isStartOfStatement(currentKeywordWithArgs, "elseif"))
                        writer.write("} else if "+currentKeywordWithArgs.substring(6)+"{");
                    else if (isStartOfStatement(currentKeywordWithArgs, "macro"))
                    {
                        writer.write("function "+currentKeywordWithArgs.substring(5).trim()+"{var macroResult = \"\"; var $p = function(arg) { if (!(typeof arg  == 'undefined')) macroResult += arg;}; var bodyContent=\"\"; if (arguments.length > 0) { if (typeof arguments[arguments.length-1] == 'function') bodyContent = arguments[arguments.length-1](); else bodyContent = arguments[arguments.length-1];} ");
                        openingBraceType.push("MACRO");
                    }
                    else if (isStartOfStatement(currentKeywordWithArgs, "foreach"))
                    {
                        int b1 = currentKeywordWithArgs.indexOf("(");
                        int b2 = currentKeywordWithArgs.lastIndexOf(")");
                        int in = currentKeywordWithArgs.indexOf(" in ");
                        if ((b1 >= 0) && (b2 > 0) && (in > 0))
                        {
                            String varName = currentKeywordWithArgs.substring(b1+1, in).trim();
                            String obj = currentKeywordWithArgs.substring(in+4, b2).trim();
                            
                            int dotdots = obj.indexOf("..");
                            if ((dotdots >= 0) && obj.startsWith("[") && obj.endsWith("]"))
                            {
                                String startExpr = obj.substring(1, dotdots).trim();
                                String endExpr = obj.substring(dotdots+2, obj.length()-1).trim();
                                
                                writer.write("(function(){var foreach = {}; foreach.start = ("+startExpr+")|0; foreach.end = ("+endExpr+")|0; for (foreach.count=0, foreach.index=foreach.start; foreach.index <= foreach.end; foreach.index++, foreach.count++) { if (foreach.count > "+foreachLimit+") throw \"Too many iterations\"; var "+varName+" = foreach.index;");
                            }
                            else
                            {
                                writer.write("(function(){var foreach = {}; foreach.values = ("+obj+"); foreach.array = foreach.values; if (!Array.isArray(foreach.array)) foreach.array = Object.keys(foreach.values); for (foreach.count=0; foreach.count<foreach.array.length; foreach.count++) { foreach.key = foreach.array[foreach.count]; foreach.value = foreach.values[foreach.key]; var "+varName+" = foreach.key;");
                            }
                            openingBraceType.push("FOREACH");
                        }
                    }
                    else 
                    {
                        if (currentKeywordWithArgs.startsWith("@"))
                        {
                            String prefix = "$p("+currentKeywordWithArgs.substring(1, currentKeywordWithArgs.length()-1).trim();
                            if (!prefix.endsWith("("))
                                prefix += ",";
                            writer.write(prefix+"function (){var macroResult = \"\"; var $p = function(arg) { if (!(typeof arg == 'undefined')) macroResult += arg;}; ");
                            openingBraceType.push("MACRO_CALL_WITH_BODY");
                        }
                        else
                            writer.write("$p("+currentKeywordWithArgs+");");
                    }
                    currentKeywordWithArgs = null;
                }
                break;
            case ESCAPED:
                writer.write("$p(\"");
                writer.write(escapeJSString(ss.substring(1)));
                writer.write("\");");
                break;
            case VERBATIM_TEXT:
                if (!isInVerbatim)
                {
                    ss = stripMarkers(ss, "#[[", "]]#");
                    isInVerbatim = true;
                }
                else
                {
                    if (ss.endsWith("]]#"))
                        isInVerbatim = false;
                    ss = stripMarkers(ss, "", "]]#");
                }
                // Fall through deliberate
            case ORDINARY:
            default:
                writer.write("$p(\"");
                if (p.eol())
                {
                    ss += '\n';
                    println = false;
                }
                writer.write(escapeJSString(ss));
                writer.write("\");");
                break;
            }
            
            if (p.eol())
            {
                if (println)
                    writer.write("$p(\"\\n\");\n");
                else
                    writer.write("\n");
            }
        }
        
        return writer.toString();
    }

    public void printParts()
    {
        ScriptPart[] pp = getParts();
        for (int i=0; i<pp.length; i++)
            System.out.println(i+" ("+pp[i].startIndex+", "+pp[i].endIndex+")  ["+pp[i].type+"] '"+pp[i].toString().replace("\n", "N")+"'");
    }

    public static void main(String[] args)
    {
        String src = "Normal<% test(); \n test2(); \n test3(); %>Normal";
        ScriptParser jp2 = new ScriptParser(src);
        System.out.println(src+"\n\n");
        jp2.printWithAnnotations();
        jp2.printParts();
        String js = jp2.translateToJavascript();
        System.out.println(js);
    }
}
