
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

public class Editor extends TextEditor 
{
    private volatile String sourceName;
    private volatile Color[] textColours;

    private ScriptParser jetParser;

    public Editor(int fontSize)
    {
        super(fontSize);
        getJetParser();
    } 

    public void setSourceName(String srcName)
    {
        this.sourceName = srcName;
    }

    public String getSourceName()
    {
        return sourceName;
    }

    public ScriptParser getJetParser()
    {
        if (jetParser == null)
            jetParser = new ScriptParser("");
        return jetParser;
    }

    protected void layoutRefreshed(CharSequence content) 
    {
        if (jetParser == null)
            return;
        jetParser.setSource(content);

        int[] annotations = jetParser.getAnnotations();
        Color[] cc = new Color[annotations.length];
        
        for (int i=0; i<annotations.length; i++)
        {
            Color c = null;
            
            switch (annotations[i])
            {
            case ScriptParser.ESCAPED:
                c = Color.web("#FF00BF");
                break;
            case ScriptParser.CONTINUATION:
                c = Color.web("#F060BF");
                break;
            case ScriptParser.SINGLE_QUOTED:
            case ScriptParser.DOUBLE_QUOTED:
            case ScriptParser.ARG_QUOTED:
                c = Color.web("#8A0886"); 
                break;
            case ScriptParser.COMMENT_LINE:
            case ScriptParser.COMMENT_REGION:
                c = Color.web("#FF0000");
                break;
            case ScriptParser.VERBATIM_TEXT: 
                c = Color.web("#7401DF");
                break;
            case ScriptParser.JAVASCRIPT_SOURCE:
                c = Color.web("#00BFFF");
                break;
            case ScriptParser.KEYWORD:
            case ScriptParser.KEYWORD_WITH_ARGS:
            case ScriptParser.ARGS:
                c = Color.web("#FF00BF");
                break;
            case ScriptParser.EXPRESSION:
            case ScriptParser.BRACKETED_EXPRESSION: 
            case ScriptParser.ARG_EXPRESSION:
            case ScriptParser.ARG_BRACKETED_EXPRESSION:
            case ScriptParser.DOUBLE_QUOTED_EXPRESSION:
            case ScriptParser.DOUBLE_QUOTED_BRACKETED_EXPRESSION:
                c = Color.web("#FF8000");
                break;
            case ScriptParser.ORDINARY:
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
