
package jjsp.jde;

import java.net.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import javafx.application.*;
import javafx.event.*;
import javafx.scene.*;
import javafx.beans.*;
import javafx.beans.value.*;
import javafx.scene.canvas.*;
import javafx.scene.effect.*;
import javafx.scene.shape.*;
import javafx.scene.text.*;
import javafx.scene.input.*;
import javafx.scene.paint.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.*;
import javafx.scene.web.*;
import javafx.geometry.*;
import javafx.animation.*;
import javafx.util.*;
import javafx.stage.*;
import javafx.collections.*;
import javafx.concurrent.Worker.*;

import netscape.javascript.*;

import jjsp.engine.*;

public class SQLEditor extends TextEditor 
{
    private volatile String sourceName;
    private volatile Color[] textColours;

    private SQLHighlighter highlighter;

    public SQLEditor()
    {
        this(16);
    }

    public SQLEditor(int fontSize)
    {
        super(fontSize);
        getSQLHighlighter();
    } 

    public void setSourceName(String srcName)
    {
        this.sourceName = srcName;
    }

    public String getSourceName()
    {
        return sourceName;
    }

    public SQLHighlighter getSQLHighlighter()
    {
        if (highlighter == null)
            highlighter = new SQLHighlighter("");
        return highlighter;
    }

    protected SQLHighlighter.StatementText[] extractSelectedStatements(SQLHighlighter.StatementText[] statements)
    {
        int selectionStart = getSelectionLineStart();
        int selectionEnd = getSelectionLineEnd();

        if ((selectionStart < 0) || (selectionEnd < 0))
            return statements;
        
        ArrayList buf = new ArrayList();
        for (int i=0; i<statements.length; i++)
        {
            String ss = statements[i].toString();
            if ((statements[i].startLine >= selectionStart) && (statements[i].endLine <= selectionEnd))
                buf.add(statements[i]);
        }

        statements = new SQLHighlighter.StatementText[buf.size()];
        buf.toArray(statements);
        return statements;
    }

    public SQLHighlighter.StatementText[] getComments()
    {
        return getComments(true);
    }

    public SQLHighlighter.StatementText[] getComments(boolean selectedTextOnly)
    {
        SQLHighlighter.StatementText[] comments = getSQLHighlighter().getComments();
        if (selectedTextOnly)
            return extractSelectedStatements(comments);
        else
            return comments;
    }

    public SQLHighlighter.StatementText[] getSQLCommands()
    {
        return getSQLCommands(true);
    }

    public SQLHighlighter.StatementText[] getSQLCommands(boolean selectedTextOnly)
    {
        SQLHighlighter.StatementText[] statements = getSQLHighlighter().getStatements();
        if (selectedTextOnly)
            return extractSelectedStatements(statements);
        else
            return statements;
    }

    protected void layoutRefreshed(CharSequence content) 
    {
        if (highlighter == null)
            return;
        highlighter.setSource(content);

        int[] annotations = highlighter.getAnnotations();
        Color[] cc = new Color[annotations.length];
        
        for (int i=0; i<annotations.length; i++)
        {
            Color c = null;
            
            switch (annotations[i])
            {
            case SQLHighlighter.SPECIAL_CHAR:
                c = Color.web("#AF70BF");
                break;
            case SQLHighlighter.SINGLE_QUOTED:
            case SQLHighlighter.DOUBLE_QUOTED:
                c = Color.web("#8A0886"); 
                break;
            case SQLHighlighter.COMMENT_LINE:
            case SQLHighlighter.COMMENT_REGION:
                c = Color.web("#FF0000");
                break;
            case SQLHighlighter.ROUND_BRACKET: 
                c = Color.web("#7401DF");
                break;
            case SQLHighlighter.CURLY_BRACKET:
                c = Color.web("#00BFFF");
                break;
            case SQLHighlighter.KEYWORD:
                c = Color.web("#FF00BF");
                break;
            case SQLHighlighter.ORDINARY:
            default:
                c = Color.web("#013ADF");
            }
                
            cc[i] = c;
        }
        
        textColours = cc;
    }

    protected void setStyleForCharacter(CharSequence content, int lineNumber, int charPos, boolean isSelected, int caretPos, GraphicsContext gc)
    {
        try
        {
            gc.setFill(textColours[charPos]);
        }
        catch (Exception e)
        {
            gc.setFill(Color.BLUE);
        }
    }
}
