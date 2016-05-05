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

    private ScriptParser jjspParser;

    public Editor(int fontSize)
    {
        super(fontSize);
        getJJSPParser();
    } 

    public void setSourceName(String srcName)
    {
        this.sourceName = srcName;
    }

    public String getSourceName()
    {
        return sourceName;
    }

    public ScriptParser getJJSPParser()
    {
        if (jjspParser == null)
            jjspParser = new ScriptParser("");
        return jjspParser;
    }

    protected void layoutRefreshed(CharSequence content) 
    {
        if (jjspParser == null)
            return;
        jjspParser.setSource(content);

        int[] annotations = jjspParser.getAnnotations();
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
