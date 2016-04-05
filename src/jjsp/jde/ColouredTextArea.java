
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

import jjsp.util.*;

public class ColouredTextArea extends TextEditor 
{
    protected Color[] charColours;

    public ColouredTextArea(int textSize)
    {
        super(textSize);
        setEditable(false);
    }
    
    public void setText(String text)
    {
        if (charColours != null)
            Arrays.fill(charColours, null);
        super.setText(text);
    }

    public void insertColouredText(int pos, String toInsert, Color colour)
    {
        String text = getText();
        if ((pos < 0) || (pos > text.length()))
            throw new ArrayIndexOutOfBoundsException("Insert position outside valid text");
        
        if (charColours == null) 
            charColours = new Color[text.length()+Math.max(1024, toInsert.length())];
        else if (charColours.length < text.length()+ toInsert.length())
        {
            Color[] cc = new Color[text.length()+Math.max(1024, toInsert.length())];
            System.arraycopy(charColours, 0, cc, 0, charColours.length);
            charColours = cc;
        }
        
        System.arraycopy(charColours, pos, charColours, pos+toInsert.length(), charColours.length-pos-toInsert.length());
        for (int i=0, j=pos; i<toInsert.length(); i++, j++)
            charColours[j] = colour;
        
        super.setText(text.substring(0, pos)+toInsert+text.substring(pos));
    }

    public void appendColouredText(String toAppend, Color colour)
    {
        insertColouredText(getTextLength(), toAppend, colour);
    }
    
    protected void setStyleForCharacter(CharSequence content, int lineNumber, int charPos, boolean isSelected, int caretPos, GraphicsContext gc)
    {
        super.setStyleForCharacter(content, lineNumber, charPos, isSelected, caretPos, gc);
        try
        {
            gc.setFill(charColours[charPos]);
        }
        catch (Exception e) {}
    }
}
