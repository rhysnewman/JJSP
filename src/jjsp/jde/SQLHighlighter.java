package jjsp.jde;

import java.io.*;
import java.util.*;

import jjsp.util.*;

public class SQLHighlighter
{
    public static final int ORDINARY = 0;
    public static final int COMMENT_LINE = 1;
    public static final int COMMENT_REGION = 2;
    public static final int SINGLE_QUOTED = 3;
    public static final int DOUBLE_QUOTED = 4;
    public static final int ROUND_BRACKET = 5;
    public static final int CURLY_BRACKET = 6;
    public static final int SPECIAL_CHAR = 7;
    public static final int KEYWORD = 8;

    private static final String[] KEYWORDS = {"and", "all", "alter", "and", "as", "asc", "before", "between", "bigint", "binary", "blob", "both", "by", "call", "cascade", "case", "change", "char", "character", "check", "collate", "column", "condition", "constraint", "continue", "convert", "create", "cross", "cursor", "database", "databases", "dec", "decimal", "declare", "default", "delete", "desc", "describe", "distinct", "div", "double", "drop", "each", "else", "elseif", "enclosed", "escaped", "exists", "exit", "explain", "false", "fetch", "float", "for", "force", "foreign", "from", "fulltext", "grant", "group", "having", "if", "ignore", "in", "index", "INFILE", "INNER", "INOUT", "INSENSITIVE", "INSERT", "INT", "INT1", "INT2", "INT3", "INT4", "INT8", "INTEGER", "INTERVAL", "INTO", "IS", "ITERATE", "JOIN", "KEY", "KEYS", "KILL", "LEADING", "LEAVE", "LEFT", "LIKE", "LIMIT", "LINES", "LOAD", "LOCALTIME", "LOCALTIMESTAMP", "LOCK", "LONG", "LONGBLOB", "LONGTEXT", "LOOP", "LOW_PRIORITY", "MATCH", "MEDIUMBLOB", "MEDIUMINT", "MEDIUMTEXT", "MIDDLEINT", "MINUTE_MICROSECOND", "MINUTE_SECOND", "MOD", "MODIFIES", "NATURAL", "NOT", "NO_WRITE_TO_BINLOG", "NULL", "NUMERIC", "ON", "OPTIMIZE", "OPTION", "OPTIONALLY", "OR", "ORDER", "OUT", "OUTER", "OUTFILE", "PRECISION", "PRIMARY", "PROCEDURE", "PURGE", "READ", "READS", "REAL", "REFERENCES", "REGEXP", "RELEASE", "RENAME", "REPEAT", "REPLACE", "REQUIRE", "RESTRICT", "RETURN", "REVOKE", "RIGHT", "RLIKE", "SCHEMA", "SCHEMAS", "SECOND_MICROSECOND", "SELECT", "SENSITIVE", "SEPARATOR", "SET", "SHOW", "SMALLINT", "SONAME", "SPATIAL", "SPECIFIC", "SQL", "SQLEXCEPTION", "SQLSTATE", "SQLWARNING", "SQL_BIG_RESULT", "SQL_CALC_FOUND_ROWS", "SQL_SMALL_RESULT", "SSL", "STARTING", "STRAIGHT_JOIN", "TABLE", "TERMINATED", "THEN", "TINYBLOB", "TINYINT", "TINYTEXT", "TO", "TRAILING", "TRIGGER", "TRUE", "UNDO", "UNION", "UNIQUE", "UNLOCK", "UNSIGNED", "UPDATE", "USAGE", "USE", "USING", "UTC_DATE", "UTC_TIME", "UTC_TIMESTAMP", "	VALUES", "VARBINARY", "VARCHAR", "VARCHARACTER", "VARYING", "WHEN", "WHERE", "WHILE", "WITH", "WRITE", "XOR", "YEAR_MONTH", "ZEROFILL"};

    private static HashSet keywords = new HashSet();
    static
    {
        for (int i=0; i<KEYWORDS.length; i++)
            keywords.add(KEYWORDS[i].toLowerCase());
    }

    private static final String ANNOTATION_CHARS = "_EqQSDcCvJBbXwYmkpRU";

    private CharSequence src;
    private int[] annotations;

    public SQLHighlighter(CharSequence src)
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

    private boolean isEscaped(int index, char quoteChar)
    {
        boolean isEscaped = false;
        for (int i=index-1; i>=0; i--)
        {
            if (src.charAt(i) != quoteChar)
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
                if (ch == '"')
                    regionType = DOUBLE_QUOTED;
                else if (ch == '\'')
                    regionType = SINGLE_QUOTED;
                else if (regionMatches(i, "--"))
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
                else if ((ch == '=') || (ch == '.') || (ch == ',') || (ch == ';') || (ch == ':') || (ch == '*'))
                {
                    annotations[i] = SPECIAL_CHAR;
                    continue;
                }
                else if ((i == 0) || (" ,(){}-\t\n\r".indexOf(src.charAt(i-1)) >= 0))
                {
                    String singleKeyword = "";
                    for (int j=i; j<src.length(); j++)
                    {
                        char end = src.charAt(j);

                        if (!Character.isLetterOrDigit(end) && ("_-".indexOf(end) < 0))
                        {
                            singleKeyword = src.subSequence(i, j).toString().toLowerCase();
                            break;
                        }
                    }

                    if ((singleKeyword.length()>0) && keywords.contains(singleKeyword))
                    {
                        regionType = KEYWORD;
                        regionEnd = singleKeyword;
                    }
                }
            }
            else if (regionType == DOUBLE_QUOTED)
            {
                if ((ch == '"') && !isEscaped(i, '"'))
                    regionEnd = "\"";
            }
            else if (regionType == SINGLE_QUOTED) 
            {
                if ((ch == '\'') && !isEscaped(i, '\''))
                    regionEnd = "'";
            }
            else if (regionType == COMMENT_LINE)
            {
                if (ch == '\n')
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

    public class StatementText
    {
        public final String sourceText;
        public final int startLine, endLine;

        StatementText(String text, int start, int end)
        {
            sourceText = text;
            startLine = start;
            endLine = end;
        }

        public String toString()
        {
            return sourceText.replace("\n", "").replace("\r", "");
        }
    }

    public StatementText[] getComments()
    {
        int startLine = 0, line = 0;
        ArrayList buf = new ArrayList();
        StringBuffer lineBuffer = new StringBuffer();

        for (int i=0; i<annotations.length; i++)
        { 
            char ch = src.charAt(i);
            boolean commentEnd = false;

            String current = lineBuffer.toString().trim();
            if (ch == '\n')
            {
                line++;
                if (current.length() == 0)
                    startLine = line;
            }

            if ((annotations[i] ==  COMMENT_LINE) || (annotations[i] == COMMENT_REGION))
                lineBuffer.append(ch);
            
            if ((current.length() > 0) && ((ch == '\n') || (i == annotations.length-1)))
            {
                current = current.replace("\n", "");
                current = current.replace("\r", "");

                buf.add(new StatementText(current, startLine, line));

                startLine = line;
                lineBuffer.setLength(0);
            }
        }

        StatementText[] result = new StatementText[buf.size()];
        buf.toArray(result);
        return result;
    }

    public StatementText[] getStatements()
    {
        int startLine = 0, line = 0;
        ArrayList buf = new ArrayList();
        StringBuffer lineBuffer = new StringBuffer();

        for (int i=0; i<annotations.length; i++)
        {
            char ch = src.charAt(i);
            if ((annotations[i] !=  COMMENT_LINE) && (annotations[i] != COMMENT_REGION))
                lineBuffer.append(ch);

            if (ch == '\n')
            {
                line++;
                if (lineBuffer.toString().trim().length() == 0)
                    startLine = line;
            }
            
            if ((annotations[i] == SPECIAL_CHAR) && (src.charAt(i) == ';'))
            {
                String l = lineBuffer.toString();
                l = l.replace("\n", "");
                l = l.replace("\r", "");
                l = l.trim();

                buf.add(new StatementText(l, startLine, line));

                startLine = line;
                lineBuffer.setLength(0);
            }
        }

        StatementText[] result = new StatementText[buf.size()];
        buf.toArray(result);
        return result;
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
