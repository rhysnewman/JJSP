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
