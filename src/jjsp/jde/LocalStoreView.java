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
import javafx.scene.control.cell.*;
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

public class LocalStoreView extends BorderPane
{
    SplitPane split;
    LocalStoreTable table;
    BorderPane detailsPane;

    public LocalStoreView()
    {
        split = new SplitPane();
        detailsPane = new BorderPane();

        table = new LocalStoreTable();
        table.setMinWidth(300);
        SplitPane.setResizableWithParent(table, false);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        split.getItems().addAll(table, detailsPane);

        setCenter(split);
        split.setDividerPosition(0, 0.30);
        setMinWidth(500);
    }

    public void setEnvironment(Environment env)
    {
        table.setEnv(env);
    }

    public void requestFocus()
    {
        super.requestFocus();
        table.requestFocus();
        detailsPane.requestFocus();
    }

    class LocalStoreTable extends TableView 
    {
        Environment jenv;
        ObservableList logList;

        LocalStoreTable()
        {
            setEditable(false);
            
            TableColumn name = createColumn("Resource Name", 150, 300, (element) -> 
                                     { 
                                         String resourceName = (String) ((TableColumn.CellDataFeatures) element).getValue();
                                         return new ReadOnlyStringWrapper(resourceName); 
                                     });

            TableColumn size = createColumn("Size in bytes", 50, 100, (element) -> 
                                     { 
                                         String resourceName = (String) ((TableColumn.CellDataFeatures) element).getValue();
                                         byte[] data = jenv.getLocal(resourceName);
                                         if (data == null)
                                             return new ReadOnlyLongWrapper(0);
                                         return new ReadOnlyLongWrapper(data.length); 
                                     });
            size.setStyle("-fx-alignment: CENTER-RIGHT;");

            getColumns().addAll(name, size);
            getSelectionModel().selectedItemProperty().addListener((evt) -> { updateDetails((String) getSelectionModel().getSelectedItem()); });

        }

        private void showContextMenu(MouseEvent evt, TextFieldTableCell cell)
        {
            if (!evt.isPopupTrigger())
                return;

            List<Integer> ll = getSelectionModel().getSelectedIndices();
            if (ll.size() == 0)
                return;

            Object target = logList.get(ll.get(0));
            
            byte[] data = jenv.getLocal(target.toString());
            String textVersion = Utils.toAsciiString(data);
            
            MenuItem openWeb = new MenuItem("Show in Web View");
            openWeb.setOnAction((evt1) ->
                                {
                                    WebView webView = new WebView();
                                    detailsPane.setCenter(webView);
                                    webView.getEngine().loadContent(textVersion);
                                });
            
            MenuItem openRaw = new MenuItem("Show Raw Text");
            openRaw.setOnAction((evt1) ->
                                {
                                    TextArea editor = new TextArea(textVersion);
                                    editor.setEditable(false);
                                    editor.setFont(Font.font("monospaced", FontWeight.NORMAL, 12));
                                    detailsPane.setCenter(editor);
                                });

            ContextMenu cm = new ContextMenu();
            cm.getItems().addAll(openWeb, openRaw);
            cm.show(cell, Side.TOP, evt.getX(), evt.getY());
        }

        void setEnv(Environment jenv)
        {
            this.jenv = jenv;
            logList = FXCollections.observableArrayList();

            if (jenv != null)
            {
                String[] names = jenv.listLocal();
                for (int i=0; i<names.length; i++)
                    logList.add(names[i]);
            }

            setItems(logList);
        }

        private TableColumn createColumn(String title, int minWidth, int prefWidth, Callback cb)
        {
            TableColumn result = new TableColumn(title);
            result.setMinWidth(minWidth);
            if (prefWidth < 0)
            {
                result.setPrefWidth(-prefWidth);
                result.setMaxWidth(-prefWidth);
            }
            else
                result.setPrefWidth(prefWidth);
                
            result.setStyle("-fx-alignment: CENTER-LEFT;");
            result.setCellValueFactory(cb);

            result.setCellFactory((column) ->
                                  {
                                      TextFieldTableCell cell = new TextFieldTableCell();
                                      
                                      cell.setOnMousePressed(evt -> showContextMenu(evt, cell));
                                      cell.setOnMouseReleased(evt -> showContextMenu(evt, cell));
                                      
                                      return cell;
                                  });
            
            return result;
        }

        private void updateDetails(String resourceName)
        {
            if (jenv == null)
                return;
            byte[] data = jenv.getLocal(resourceName);
            String textVersion = Utils.toAsciiString(data);
            
            String mime = HTTPHeaders.guessMIMEType(resourceName);
            if ((mime.indexOf("html") >= 0) || textVersion.toLowerCase().indexOf("<html>") >= 0)
            {
//                WebView webView = new WebView();
//                detailsPane.setCenter(webView);
//                webView.getEngine().loadContent(textVersion);
                
                String contents = "";
                contents = textVersion;
                TextArea editor = new TextArea(contents);
                editor.setEditable(false);
                editor.setFont(Font.font("monospaced", FontWeight.NORMAL, 12));
                
                detailsPane.setCenter(editor);
            }
            else if (resourceName.endsWith(".svg"))
            {
                String rawSVG = textVersion;
                int start = rawSVG.indexOf("<svg ");
                if (start >= 0)
                    rawSVG = rawSVG.substring(start);

                WebView webView = new WebView();
                detailsPane.setCenter(webView);
                webView.getEngine().loadContent(rawSVG);
            }
            else if (mime.startsWith("image"))
            {
                Image im = new Image(new ByteArrayInputStream(data));
                
                ImageView iv = new ImageView(im);
                iv.setFitWidth(200);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);

                detailsPane.setCenter(new ScrollPane(iv));
            }
            else 
            {
                String contents = "";
                if (mime.startsWith("text") || resourceName.endsWith(".js") || (mime.indexOf("json") >= 0) || (mime.indexOf("css") >= 0))
                    contents = textVersion;
                else
                {
                    try
                    {
                        contents = BinaryDataViewer.formatBinaryData(new ByteArrayInputStream(data), 10*1024);
                    }
                    catch (Exception e) {}
                }

                TextArea editor = new TextArea(contents);
                editor.setEditable(false);
                editor.setFont(Font.font("monospaced", FontWeight.NORMAL, 12));
                
                detailsPane.setCenter(editor);
            }
        }
    }

    public static class Test extends Application
    {
        public void start(final Stage primaryStage) throws Exception
        {
            LocalStoreView v = new LocalStoreView();

            Scene scene = new Scene(v, 1200, 700, Color.WHITE);
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
