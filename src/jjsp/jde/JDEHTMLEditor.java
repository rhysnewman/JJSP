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
import java.util.*;

import javafx.application.*;
import javafx.geometry.*;

import javafx.scene.*;
import javafx.stage.*;
import javafx.scene.paint.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.shape.*;
import javafx.scene.web.*;

import jjsp.util.*;
import jjsp.engine.*;

public class JDEHTMLEditor extends JDEComponent
{
    private String lastHTMLText;
    protected HTMLEditor editor;

    public JDEHTMLEditor(URI uri)
    {
        super(uri);
        editor = new HTMLEditor();
        lastHTMLText = "";

        setCenter(editor);
        loadFromURI();
    }

    public boolean loadFromURI()
    {
        return load(getURI());
    }
    
    public boolean load(URI src)
    {
        if (src == null)
            return false;

        try
        {
            String text = JDETextEditor.loadTextFromURI(src);
            componentURI = src;

            editor.setHtmlText(text);
            lastModified = getLastModifiedFromURI();
            return true;
        }
        catch (Exception e)
        {
            setStatus("Error Loading "+src, e);
            return false;
        }
    }

    public byte[] getDataForAutoSave()
    {
        String currentText = editor.getHtmlText();
        if (lastHTMLText.equals(currentText))
            return null;
        
        lastHTMLText = currentText;
        return Utils.getAsciiBytes(lastHTMLText);
    }

    public void loadAutoSavedData(byte[] data)
    {
        super.loadAutoSavedData(data);
        if (data == null)
            return;

        editor.setHtmlText(Utils.toString(data));
    }
}
