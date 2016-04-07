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
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import javax.script.*;

import javafx.application.*;
import javafx.event.*;
import javafx.scene.*;
import javafx.beans.*;
import javafx.beans.property.*;
import javafx.beans.value.*;
import javafx.scene.paint.*;
import javafx.scene.text.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.*;
import javafx.scene.image.*;
import javafx.scene.web.*;

import javafx.geometry.*;
import javafx.util.*;
import javafx.stage.*;
import javafx.collections.*;

import jjsp.http.*;
import jjsp.engine.*;
import jjsp.util.*;

public class JavascriptPane extends JDETextEditor
{
    public JavascriptPane(URI jfURI, SharedTextEditorState sharedState)
    {
        super(jfURI, sharedState);
    }

    protected void init(SharedTextEditorState sharedState)
    {
        editor = new JSEditor();
        editor.setSharedTextEditorState(sharedState);
        setCenter(editor);
        editor.setMinHeight(100);

        setCenter(editor);
        searchBox = new BorderPane();
        setTop(searchBox);

        loadFromURI();
    }

    public void setStatus(String message, Throwable t) 
    {
        super.setStatus(message, t);
        if (t != null)
            editor.setText("Error loading Javascript Source: "+message+"\n\n"+toString(t));
    }

    protected FileChooser initFileChooser()
    {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Javascript Source Code", "*.js"));
        return fileChooser;
    }
}
