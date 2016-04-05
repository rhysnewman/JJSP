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

public class HTTPLogView extends BorderPane implements HTTPServerLogger
{
    TextArea editor;
    HTTPUtils utils;
    StackPane stack;
    HTTPLogTable table;
    Text detailsPaneTitle;
    BorderPane detailsPane;
    boolean detailsShowing;

    public HTTPLogView()
    {
        detailsShowing = false;
        utils = HTTPUtils.getUtils();

        table = new HTTPLogTable();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        editor = new TextArea();
        editor.setEditable(false);
        editor.setFont(Font.font("monospaced", FontWeight.NORMAL, 12));

        Button closeDetails = new Button("Close Detail View");
        closeDetails.setFont(new Font(10));
        closeDetails.setMinWidth(120);
        closeDetails.setMaxHeight(12);
        closeDetails.setOnAction((event) -> { detailsShowing = false; detailsPane.toBack(); });

        detailsPaneTitle = new Text("Log Entry Details");
        detailsPaneTitle.setFill(Color.WHITE);

        BorderPane bp2 = new BorderPane();
        bp2.setStyle("-fx-background-color: #0052A3;");
        bp2.setMaxHeight(25);
        bp2.setMinHeight(25);
        bp2.setCenter(detailsPaneTitle);
        bp2.setRight(closeDetails);

        detailsPane = new BorderPane();
        detailsPane.setTop(bp2);
        detailsPane.setCenter(editor);
        detailsPane.setMaxWidth(400);
        detailsPane.setMinWidth(400);
        detailsPane.setStyle("-fx-border-width: 2px; -fx-border-color: #0052A3");
        
        widthProperty().addListener((evt) -> 
                                    { 
                                        double w = Math.max(50, getWidth() - 473); 
                                        if (w < 250) 
                                            detailsPane.toBack(); 
                                        else if (detailsShowing) 
                                            detailsPane.toFront(); 
                                        detailsPane.setMaxWidth(w); 
                                        detailsPane.setMinWidth(w);
                                    });
        
        stack = new StackPane(table, detailsPane);
        StackPane.setAlignment(detailsPane, Pos.CENTER_RIGHT);

        setCenter(stack);
        setMinWidth(500);
        detailsPane.toBack();
    }

    public void requestFocus()
    {
        super.requestFocus();
        editor.requestFocus();
        table.requestFocus();
    }
        
    public void requestProcessed(HTTPLogEntry logEntry)
    {
        if (Platform.isFxApplicationThread())
            table.logList.add(logEntry);
        else
            Platform.runLater(() -> {table.logList.add(logEntry);});
    }

    public void clear()
    {
        table.logList.clear();
        detailsShowing = false; 
        detailsPane.toBack(); 
    }

    private void updateDetails(HTTPLogEntry entry)
    {
        if (entry == null)
        {
            detailsPaneTitle.setText("Log Entry Details");
            editor.setText("");
            return;
        }

        StringBuffer buf = new StringBuffer();
        buf.append("Time:             "+utils.formatHTTPDate(entry.requestReceived)+"\n");
        buf.append("Process Time:     "+entry.totalRequestTime()+" ms\n");
        buf.append("Server Time:      "+entry.responseTime()+" ms\n");
        buf.append("Bytes read/write: "+entry.bytesRead+" / "+entry.bytesWritten+"\n");
        buf.append("Client Address:   "+entry.clientAddress+"\n");
        buf.append("Is Secure:        "+entry.isSecure+"\n");
        buf.append("\n");
        buf.append("Request:          "+entry.getRequestMainLine()+"\n");
        Iterator itt = entry.reqHeaders.entrySet().iterator();
        while (itt.hasNext())
        {
            Map.Entry e = (Map.Entry) itt.next();
            String key = (String) e.getKey();
            if (key.equals(HTTPHeaders.MAIN_LINE))
                continue;
            buf.append("    "+key+" : "+e.getValue()+"\n");
        }
        
        buf.append("\n");
        buf.append("Response:          "+entry.getResponseMainLine()+"\n");
        Iterator itt2 = entry.respHeaders.entrySet().iterator();
        while (itt2.hasNext())
        {
            Map.Entry e = (Map.Entry) itt2.next();
            String key = (String) e.getKey();
            if (key.equals(HTTPHeaders.MAIN_LINE))
                continue;
            buf.append("    "+key+" : "+e.getValue()+"\n");
        }

        buf.append("\n");
        buf.append("Filter Chain: \n");

        for (HTTPFilterChain chain = entry.filterChain; chain != null; chain = chain.previous)
        {
            buf.append("    "+chain.linkName+"  ");
            if (chain.report != null)
                buf.append("["+chain.report+"] ");
            if (chain.error != null)
            {
                buf.append(chain.error.toString()+"\n");
                String errString = JDETextEditor.toString(chain.error);
                errString = errString.replace("\n", "\n          ");
                buf.append(errString);
            }  
                
            buf.append("\n");
        }

        detailsPaneTitle.setText(entry.getRequestMainLine());
        editor.setText(buf.toString());
        //editor.scrollToLine(0);
    }

    class HTTPLogTable extends TableView 
    {
        ObservableList logList;

        HTTPLogTable()
        {
            setEditable(false);
            
            TableColumn time = createColumn("Time", 200, -200, (element) -> 
                                     { 
                                         HTTPLogEntry entry = (HTTPLogEntry) ((TableColumn.CellDataFeatures) element).getValue();
                                         return new ReadOnlyStringWrapper(utils.formatHTTPDate(entry.requestReceived)); 
                                     });

            TableColumn delay = createColumn("Delay", 70, -70, (element) -> 
                                     { 
                                         HTTPLogEntry entry = (HTTPLogEntry) ((TableColumn.CellDataFeatures) element).getValue();
                                         return new ReadOnlyLongWrapper(entry.totalRequestTime()); 
                                     });
            delay.setStyle("-fx-alignment: CENTER-RIGHT;");

            TableColumn address = createColumn("Client Address", 200, -200, (element) -> 
                                     { 
                                         HTTPLogEntry entry = (HTTPLogEntry) ((TableColumn.CellDataFeatures) element).getValue();
                                         return new ReadOnlyStringWrapper(entry.clientAddress.toString()); 
                                     });

            TableColumn req = createColumn("HTTP Request", 150, 400, (element) -> 
                                     { 
                                         HTTPLogEntry entry = (HTTPLogEntry) ((TableColumn.CellDataFeatures) element).getValue();
                                         return new ReadOnlyStringWrapper(entry.getRequestMainLine()); 
                                     });
            

            TableColumn resp = createColumn("HTTP Response", 150, 400, (element) -> 
                                     { 
                                         HTTPLogEntry entry = (HTTPLogEntry) ((TableColumn.CellDataFeatures) element).getValue();
                                         return new ReadOnlyStringWrapper(entry.getResponseMainLine()); 
                                     });

            TableColumn chain = createColumn("Filter Chain", 70, 200, (element) -> 
                                     { 
                                         HTTPLogEntry entry = (HTTPLogEntry) ((TableColumn.CellDataFeatures) element).getValue();
                                         return new ReadOnlyStringWrapper(entry.filterChain.getPath()); 
                                     });

            getColumns().addAll(req, time, resp, address, chain, delay);

            logList = FXCollections.observableArrayList();
            setItems(logList);

            getSelectionModel().selectedItemProperty().addListener((evt) -> { updateDetails((HTTPLogEntry) getSelectionModel().getSelectedItem()); });
            
            setOnMouseClicked((evt) -> { if (evt.getClickCount() < 2) return; detailsShowing = true; detailsPane.toFront();});
        }

        private TableColumn createColumn(String title, int minWidth, int prefWidth,Callback cb)
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
            return result;
        }
    }

    public static class Test extends Application
    {
        public void start(final Stage primaryStage) throws Exception
        {
            HTTPLogView v = new HTTPLogView();

            Scene scene = new Scene(v, 1200, 700, Color.WHITE);
            primaryStage.setScene(scene);
            primaryStage.show();
            Platform.setImplicitExit(true);

            byte[] raw = Utils.getAsciiBytes("GET /test.html HTTP/1.1\r\nUser-Agent: tester\r\n\r\n");
            HTTPRequestHeaders req = new HTTPRequestHeaders();
            req.readHeadersFromStream(new ByteArrayInputStream(raw));

            HTTPResponseHeaders resp = new HTTPResponseHeaders();
            resp.configureAsOK();
            resp.setContentType("text/test");

            long now = System.currentTimeMillis();
            HTTPFilterChain chain = new HTTPFilterChain("link1", null);
            for (int i=0; i<10; i++)
            {
                now += 3600*1000;
                HTTPLogEntry entry = new HTTPLogEntry(false, InetAddress.getLocalHost().getHostAddress(), now, now+3*i, now+10*i, now+12*i, 20*i, 200*i, chain, req, resp);
                v.requestProcessed(entry);

                Thread.sleep(10);
            }
        }
    }

    public static void main(String[] args) throws Exception
    {
        Application.launch(Test.class, args);
    }
}
