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
package jjsp.jde;

import java.io.*;
import java.util.*;

import jjsp.util.*;

public class JSHighlighter
{
    public static final int ORDINARY = 0;
    public static final int ESCAPED = 1;
    public static final int COMMENT_LINE = 2;
    public static final int COMMENT_REGION = 3;
    public static final int SINGLE_QUOTED = 4;
    public static final int DOUBLE_QUOTED = 5;
    public static final int ROUND_BRACKET = 6;
    public static final int CURLY_BRACKET = 7;
    public static final int SPECIAL_CHAR = 8;
    public static final int KEYWORD = 9;

    private static final String[] KEYWORDS = {"abstract", "arguments", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "debugger", "default", "delete", "do", "double", "else", "enum", "eval", "export", "extends", "false", "final", "finally", "float", "for", "function", "goto", "if", "implements", "import", "in", "instanceof", "int", "interface", "let", "long", "native", "new", "null", "package", "private", "protected", "public", "return", "short", "static", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "true", "try", "typeof", "var", "void", "volatile", "while", "with", "yield"};

    private static final String ANNOTATION_CHARS = "_EqQSDcCvJBbXwYmkpRU";

    private CharSequence src;
    private int[] annotations;

    public JSHighlighter(CharSequence src)
    {
        setSource(src);
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
                }
                else if (ch == '"')
                    regionType = DOUBLE_QUOTED;
                else if (ch == '\'')
                    regionType = SINGLE_QUOTED;
                else if (regionMatches(i, "//"))
                    regionType = COMMENT_LINE;
                else if (regionMatches(i, "/*"))
                    regionType = COMMENT_REGION;
                else if ((ch == '(') || (ch == ')'))
                {
                    annotations[i] = ROUND_BRACKET;
                    continue;
                }
                else if ((ch == '{') || (ch == '}'))
                {
                    annotations[i] = CURLY_BRACKET;
                    continue;
                }
                else if ((ch == '=') || (ch == '.') || (ch == ','))
                {
                    annotations[i] = SPECIAL_CHAR;
                    continue;
                }
                else 
                {
                    String singleKeyword = null;
                    for (int j=0; j<KEYWORDS.length; j++)
                    {
                        String kw = KEYWORDS[j];
                        if (!regionMatches(i, kw))
                            continue;
                        if ((i > 0) && Character.isLetterOrDigit(src.charAt(i-1)))
                            continue;
                        if (i + kw.length() >= src.length())
                            continue;

                        char nextChar =  src.charAt(i+kw.length());
                        if (!Character.isLetterOrDigit(nextChar))
                        {
                            singleKeyword = kw;
                            break;
                        }
                    }

                    if (singleKeyword != null)
                    {
                        regionType = KEYWORD;
                        regionEnd = singleKeyword;
                    }
                }
            }
            else if (regionType == DOUBLE_QUOTED)
            {
                if ((ch == '"') && !isEscaped(i))
                    regionEnd = "\"";
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
                if (regionMatches(i, "*/"))
                    regionEnd = "*/";
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
}
