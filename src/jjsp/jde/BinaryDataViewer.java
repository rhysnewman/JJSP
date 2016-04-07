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

import jjsp.util.*;

public class BinaryDataViewer extends JDEComponent 
{
    private static final int MAX_SIZE = 1024*1024;
    private static final int COLUMN_WIDTH = 32;
    
    private TextEditor text;

    public BinaryDataViewer(URI uri)
    {
        this(uri, MAX_SIZE);
    }
    
    public BinaryDataViewer(URI uri, int limit)
    {
        super(uri);

        String textContent = "";
        InputStream in = null;
        try
        {
            in = uri.toURL().openStream();
            textContent = formatBinaryData(in, limit);
        }
        catch (Exception e) {}
        finally
        {
            try
            {
                in.close();
            }
            catch (Exception e) {}
        }

        text = new TextEditor(JDETextEditor.EDITOR_TEXT_SIZE);
        text.setEditable(false);
        text.setText(textContent);
        
        setCenter(text);
    }

    public void requestFocus()
    {
        super.requestFocus();
        text.requestFocus();
    }

    public void setDisplayed(boolean isShowing) 
    {
        super.setDisplayed(isShowing);
        text.setIsShowing(isShowing);
    }

    public static String formatBinaryData(InputStream in, int limit) throws IOException
    {
        StringBuffer buf = new StringBuffer();
        StringBuffer buf1 = new StringBuffer();
        StringBuffer buf2 = new StringBuffer();
        
        int pos = 0, col = 0, widthTarget = -1;
        try
        {
            while (pos < limit)
            {
                int lineStart = pos;
                buf1.setLength(0);
                buf2.setLength(0);

                for (col=0; col<COLUMN_WIDTH; col++)
                {
                    int b = in.read();
                    if (b < 0)
                        throw new EOFException();

                    buf1.append(String.format("%02X", 0xFF & b));
                    if ((pos % 4) == 3)
                        buf1.append(' ');
                    
                    char ch = (char) b; 
                    if ((ch >= 32) && (ch <= 126))
                        buf2.append(ch);
                    else
                        buf2.append('.');
                    
                    pos++;
                }
                
                widthTarget = Math.max(widthTarget, buf1.length());
                buf.append(buf1+"  "+buf2+"\n");
            }

            buf.append("\n.....[limit of display]\n\n");
        }
        catch (Exception e) 
        {
            while (buf1.length() < widthTarget)
                buf1.append(" ");
            buf.append(buf1+"  "+buf2+"\n");
        }

        return buf.toString();
    }
}
