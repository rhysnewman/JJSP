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
import java.sql.*;
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
import javafx.scene.control.cell.*;
import javafx.scene.layout.*;
import javafx.scene.input.*;
import javafx.scene.image.*;
import javafx.scene.web.*;

import javafx.geometry.*;
import javafx.util.*;
import javafx.util.converter.*;
import javafx.stage.*;
import javafx.collections.*;

import jjsp.http.*;
import jjsp.engine.*;
import jjsp.util.*;

public class JSONTablePane extends JDEComponent 
{
    protected StackPane mainStack;
    protected BorderPane mainPane;

    public JSONTablePane(URI uri)
    {
        super(uri);
        
        mainPane = new BorderPane();
        
        mainStack = new StackPane();
        mainStack.getChildren().add(mainPane);
        BorderPane.setMargin(mainStack, new Insets(10,10,10,10));

        setCenter(mainStack);
    }

    public void overlayAlert(Node node)
    {
        BorderPane bg = new BorderPane();
        bg.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
        bg.setCenter(node);
        BorderPane.setAlignment(node, Pos.CENTER);

        mainStack.getChildren().add(bg);
        bg.toFront();
        setCursor(Cursor.DEFAULT);
    }
    
    public void showError(String message, Throwable t)
    {
        Button ok = new Button("Dismiss");
        ok.setOnAction((evt) ->  clearAlert());

        VBox overlay = new VBox(20);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color: white; -fx-padding:20px; -fx-border-radius: 10 10 10 10; -fx-background-radius: 10 10 10 10;");
        
        TextArea area = new TextArea(JDETextEditor.toString(t));
        area.setEditable(false);
        VBox.setVgrow(area, Priority.ALWAYS);

        overlay.getChildren().addAll(new Label(message), area, ok);
        overlay.setMaxHeight(500);
        overlay.setMaxWidth(600);
        overlayAlert(overlay);
    }

    public void showJSONOverlay(String json)
    {
        showJSONOverlay("JSON Cell Data Display", json);
    }

    public void showJSONOverlay(String title, String json)
    {
        Object obj = JSONParser.parse(json);
        json = JSONParser.prettyPrint(obj);
        showPlainOverlay(title, json);
    }

    public void showPlainOverlay(String title, String text)
    {
        TextArea jsonText = new TextArea(text);
        //jsonText.setEditable(false);
        jsonText.setFont(Font.font("monospaced", FontWeight.NORMAL, 18));
        jsonText.setStyle("-fx-text-fill: blue");
        BorderPane.setMargin(jsonText, new Insets(20, 0, 20, 0));

        Font big = new Font(22);
        
        BorderPane overlay = new BorderPane();
        Label tl = new Label(title);
        tl.setFont(big);
        overlay.setTop(tl);
        overlay.setCenter(jsonText);
        
        TextField searchString = new TextField();
        searchString.setFont(big);
        
        Button search = new Button("Search");
        search.setFont(big);
        searchString.setOnAction(evt->search.fire());
        search.setOnAction(evt ->
                           {
                               String ss = searchString.getText();
                               if (ss.length() == 0)
                                   return;
                               
                               String src = jsonText.getText();
                               int currentPos = jsonText.getCaretPosition()+ss.length();
                               if ((currentPos < 0) || (currentPos >= src.length()))
                                   currentPos = 0;
                               
                               int next = src.indexOf(searchString.getText(), currentPos);
                               if (next < 0)
                               {
                                   if (currentPos > 0)
                                       next = src.indexOf(searchString.getText());
                                   if (next < 0)
                                       return;
                               }
                               jsonText.selectRange(next, next+ss.length());
                           });

        Button close = new Button("Close");
        close.setFont(big);
        close.setOnAction(evt -> clearAlert());
        
        HBox bottom = new HBox(10);
        HBox.setHgrow(searchString, Priority.ALWAYS);
        bottom.setAlignment(Pos.BASELINE_CENTER);
        
        bottom.getChildren().addAll(search, searchString, close);
        overlay.setBottom(bottom);
        
        overlay.setMaxHeight(1200);
        overlay.setMaxWidth(1800);
        //if ((mainStack.getHeight() > 1300) && (mainStack.getWidth() > 1900))
        //    overlay.setStyle("-fx-background-color: white; -fx-padding:20px; -fx-border-radius: 10 10 10 10; -fx-background-radius: 10 10 10 10;");
        //else
        overlay.setStyle("-fx-background-color: white; -fx-padding:20px; -fx-border-color: blue; -fx-border-width: 6;");
            
        overlay.addEventFilter(KeyEvent.KEY_PRESSED, (event) -> { if (event.getCode() == KeyCode.ESCAPE) { event.consume(); close.fire(); }} );
        
        BorderPane bg = new BorderPane();
        bg.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
        bg.setCenter(overlay);
        BorderPane.setAlignment(overlay, Pos.CENTER);
        //bg.setOnMouseClicked(evt -> clearAlert());

        mainStack.getChildren().add(bg);
        bg.toFront();
        setCursor(Cursor.DEFAULT);
        jsonText.requestFocus();
    }

    public void clearAlert()
    {
        mainStack.getChildren().retainAll(mainPane);
        setCursor(Cursor.DEFAULT);
    }

    protected String jsonStringDisplayFor(Object obj)
    {
        if (obj == null)
            return null;

        String result = obj.toString();
        try
        {
            Object json = JSONParser.parse(obj.toString());
            return result;
        }
        catch (Exception e) {}
        return null;
    }

    public class JSONTableCell extends TableCell
    {
        private String columnTitle;
        
        public JSONTableCell(String title)
        {
            columnTitle = title;
            setStyle("-fx-alignment: baseline-left;");
            setEditable(true);
        }
        
        public void startEdit()
        {
            super.startEdit();
            super.cancelEdit();
            
            if (!isEditable())
                return;

            Object item = getItem();
            if (item == null)
                return;
            
            String jsonString = jsonStringDisplayFor(item);
            if ((jsonString != null) && (jsonString.length() > 0))
                showJSONOverlay(columnTitle, jsonString);
            else
            { 
                String text = item.toString();
                if (text.length() < 12)
                    return;
                
                showPlainOverlay(columnTitle, text);
            }
        }

        protected void updateItem(Object item, boolean isEmpty)
        {
            super.updateItem(item, isEmpty);
            
            setGraphic(null);
            if (item == null)
                setText("");
            else
                setText(item.toString());
        }
    }
}

    
