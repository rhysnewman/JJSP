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
import java.nio.*;
import java.util.*;

import java.awt.image.BufferedImage;
import javax.imageio.*;

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
import javafx.scene.image.*;
import javafx.scene.transform.*;
import javafx.scene.web.*;
import javafx.geometry.*;
import javafx.animation.*;
import javafx.util.*;
import javafx.stage.*;
import javafx.concurrent.Worker.*;
import javafx.collections.*;

import netscape.javascript.*;

import jjsp.util.*;
import jjsp.http.*;

public class WebBrowser extends JDEComponent
{
    private StackPane main;
    private WebView webView;
    private TextEditor source;
    private ControlBox controls;
    private URI initialURI, homeURI;
    private boolean isFixedURI, isInitialLoad;

    public WebBrowser()
    {
        this(false, null);
    }

    public WebBrowser(URI initialURI)
    {
        this(false, initialURI);
    }

    public WebBrowser(boolean fixedURI, URI initialURI)
    {
        super(initialURI);
        this.initialURI = initialURI;
        this.homeURI = initialURI;

        isFixedURI = fixedURI;
        isInitialLoad = true;
        setPrefWidth(1200);

        webView = new WebView();
        source = new TextEditor(JDETextEditor.EDITOR_TEXT_SIZE);
        controls = new ControlBox();
        main = new StackPane(source, webView);
        
        setTop(controls);
        setCenter(main);
        source.setColours(Color.GREEN);

        WebEngine engine = webView.getEngine();
        engine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> 
                                                           { 
                                                               if (!isInitialLoad && isFixedURI) 
                                                                   Platform.runLater(() -> engine.getLoadWorker().cancel()); 
                                                               if (newState == State.SUCCEEDED) 
                                                                   contentLoaded();
                                                           });

        //Callbacks for when javascript in the pages does various things which need the browser to create/move windows
        engine.setConfirmHandler(arg -> {return null;});
        engine.setCreatePopupHandler(arg -> {return null;});
        engine.setOnAlert(arg -> {});
        engine.setPromptHandler(arg -> {return null;});

        if (initialURI != null)
            loadURI(initialURI);
        else if (!fixedURI)
            loadContent(null, "<HTML><BODY><H1>Enter a location in the text field above to browse the web</h1></body></HTML>");
        else
            throw new IllegalStateException("No URI specified for fixed location");
    }

    public void setDisplayed(boolean isShowing) 
    {
        super.setDisplayed(isShowing);
        source.setIsShowing(isShowing);
    }

    protected void contentLoaded()
    {
        try
        {
            URI uri = new URI(webView.getEngine().getLocation());
        
            if (isInitialLoad)
            {
                initialURI = uri;
                isInitialLoad = false;
            }
            else if (isFixedURI)
            {
                if (!uri.equals(initialURI))
                {
                    loadURI(initialURI);
                    return;
                }
            }

            controls.setURI(uri);
            synchronized (this)
            {
                componentURI = uri;
            }
        }
        catch (Exception e) {}
    }

    public String getHTMLSourceDirect()
    {
        try
        {
            return (String) webView.getEngine().executeScript("document.documentElement.outerHTML");
        }
        catch (Exception ee) 
        {
            return "<< Error getting source from Web View: "+ee+" >>";
        }
    }

    public void setHTMLSourceDirect(String html)
    {
        try
        {
            html = html.replace("\"", "\\\"");
            html = html.replace("\n", "\\n");
            html = html.replace("\r", "\\r");
            html = html.replace("\t", "\\t");
            
            String lower = html.toLowerCase();
            if (lower.startsWith("<html>"))
                html = html.substring(6);
            if (lower.endsWith("</html>"))
                html = html.substring(0, html.length()-7);
        
            String jsSource = "document.documentElement.innerHTML=\""+html+"\";";
            webView.getEngine().executeScript(jsSource);
        }
        catch (Exception e) {}
    }

    public WebEngine getEngine()
    {
        return webView.getEngine();
    }

    public void loadContent(URI contentReference, String htmlSource)
    {
        initialURI = contentReference;
        isInitialLoad = true;
        if (contentReference != null)
            controls.setURI(contentReference);

        webView.getEngine().loadContent(htmlSource);
        source.setText(htmlSource);
    }

    public void loadURI(URI uri)
    {
        if (uri == null)
            return;

        initialURI = uri;
        isInitialLoad = true;
        controls.setURI(uri);
        webView.getEngine().load(uri.toString());
        source.setText("");
    }

    public void loadHomeURI()
    {
        loadURI(homeURI);
    }

    public void loadInitialURI()
    {
        loadURI(initialURI);
    }

    public void requestFocus()
    {
        source.requestFocus();
        super.requestFocus();
    }

    public void reloadContent()
    {
        loadURI(controls.getURI());
    }

    public synchronized URI getURI()
    {
        return componentURI;
    }

    public URI getLinkForFavIcon()
    {
        try
        {
            StringWriter sw = new StringWriter();
            sw.write("(function (){ ");
            sw.write("var links = document.getElementsByTagName(\"link\"); if (!links) return null;");
            sw.write("for (var i=0; i<links.length; i++) { ");
            sw.write("    var link = links[i];");
            sw.write("    var rel = \"\"+link.rel;");
            sw.write("    if(rel.indexOf(\"icon\") != -1) return link.href;");
            sw.write("}");
            sw.write(" return null;})();");
            
            Object favURI = webView.getEngine().executeScript(sw.toString());
            if (favURI == null)
                return null;
            return new URI(favURI.toString());
        }
        catch (Exception e) {}
        
        return null;
    }

    public void goHistory(int diff)
    {
        WebHistory h = webView.getEngine().getHistory();
        try
        {
            h.go(diff);
        }
        catch (Exception e) {}
    }

    class ControlBox extends FlowPane
    {
        private TextField location;
        private CheckBox showSource;

        ControlBox()
        {
            String fontStyle = "-fx-font-size:12px";
            setStyle("-fx-background-color: linear-gradient(#3366CC 0%, #99CCFF 100%);");
            //setMinWidth(1100);
            setCursor(Cursor.DEFAULT);

            location = new TextField();
            location.setStyle(fontStyle);
            location.setMinWidth(300);
            location.setStyle(fontStyle);
            location.setPromptText("Web Address...");
            location.setOnAction((evt) -> loadURI(getURI()));
            if (isFixedURI)
            {
                location.setEditable(false);
                location.setStyle("-fx-background-color: #F0F0F0;");
            }

            showSource = new CheckBox("Show Source");
            showSource.setStyle("-fx-font-size:12px;-fx-padding-top:5px");
            showSource.setOnAction((evt) -> 
                                   {
                                       if (showSource.isSelected())
                                       {
                                           source.toFront();
                                           if (source.getText().equals(""))
                                               source.setText(getHTMLSourceDirect());
                                           source.refreshView();
                                       }
                                       else
                                       {
                                           webView.toFront();
                                           setHTMLSourceDirect(source.getText());
                                       }
                                   });

            HBox hb = new HBox(10);
            hb.setAlignment(Pos.CENTER);

            Button reload = new Button("Reload");
            reload.setStyle(fontStyle);
            reload.setOnAction((evt) -> reloadContent());
            reload.setPrefWidth(100);

            Button loadExternal = new Button("Launch ->");
            loadExternal.setStyle(fontStyle);
            loadExternal.setOnAction((evt) ->  { JDE.launchExternalBrowser(getURI()); }); 
            loadExternal.setPrefWidth(100);
            
            Button home = new Button("Home");
            home.setStyle(fontStyle);
            home.setOnAction((evt) -> loadHomeURI());
            home.setPrefWidth(100);

            if (!isFixedURI)
            {
                Button back = new Button(" << Back");
                back.setStyle(fontStyle);
                back.setOnAction((evt) -> goHistory(-1));
                back.setPrefWidth(100);

                Button forward = new Button("Forward >>");
                forward.setStyle(fontStyle);
                forward.setOnAction((evt) -> goHistory(1));
                forward.setPrefWidth(100);
                
                hb.getChildren().addAll(back, forward, reload, home);
            }
            else                
                hb.getChildren().addAll(home);

            TextField search = new TextField();
            HBox.setHgrow(search, Priority.ALWAYS);
            search.setStyle(fontStyle);
            search.setMinWidth(40);
            search.setMaxWidth(Integer.MAX_VALUE);
            search.setPromptText("Search page ...");
            
            Button find = new Button("Find");
            search.setOnAction((event) -> findInPage(search.getText()));
            find.setOnAction((event) -> findInPage(search.getText()));
            
            HBox hb2 = new HBox(10);
            hb2.setAlignment(Pos.CENTER);
            hb2.getChildren().addAll(showSource, search, find, loadExternal);

            setPadding(new Insets(5, 5, 5, 5));
            setVgap(5);
            setHgap(15);
            setAlignment(Pos.CENTER_LEFT);
            setPrefWrapLength(300); // preferred width allows for two columns

            getChildren().addAll(hb, location, hb2);
            Platform.runLater(()->{location.prefWidthProperty().bind(widthProperty().subtract(hb2.widthProperty()).subtract(hb.widthProperty()).subtract(60));});
        }

        void findInPage(String text)
        {
            try
            {
                webView.getEngine().executeScript(" window.find('"+text+"', false, false, true, true); window.getSelection().getRangeAt(0).startContainer.parentElement.scrollIntoViewIfNeeded(); ");
            }
            catch (Exception e) {}
        }

        URI getURI()
        {
            try
            {
                String text = location.getText();
                if (!text.startsWith("http") && !text.startsWith("file:"))
                    text = "http://"+text;
                
                return new URI(text);
            }
            catch (Exception e)
            {
                return null;
            }
        }

        void setURI(URI uri)
        {
            location.setText(uri.toString());
            showSource.setSelected(false);
            webView.toFront();
        }
    }

    public static class Test extends Application
    {
        public void start(final Stage primaryStage) throws Exception
        {
            String urlString = getParameters().getRaw().get(0);
            WebBrowser w = new WebBrowser(new URI(urlString));

            //WebView w = new WebView();
            //w.getEngine().load(urlString);

            Scene scene = new Scene(w, 1200, 700, Color.WHITE);
            primaryStage.setScene(scene);
            primaryStage.show();
            Platform.setImplicitExit(true);
        }
    }

    public static void main(String[] args) throws Exception
    {
        Application.launch(Test.class, args);
    }
}
