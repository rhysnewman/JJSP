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
import java.util.logging.*;
import javax.script.*;

import javafx.application.*;
import javafx.event.*;
import javafx.scene.*;
import javafx.beans.*;
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

import jjsp.engine.*;
import jjsp.util.*;
import jjsp.http.*;
import jjsp.http.filters.*;

public class SourcePane extends JDETextEditor
{
    private SplitPane mainSplit;
    private BorderPane leftPane;
    private ColouredSearchableTextOutput jjspEngineOutput;

    private int errorLine;
    private String argList;
    private String siteOutput;
    private String statusMessage;
    private Throwable currentError;

    private HTTPLogView log;
    private JDEEngine jjspEngine;
    private LocalStoreView localStoreView;

    private FileChooser fileChooser;

    public SourcePane(URI initialURI, SharedTextEditorState sharedState)
    {
        super(initialURI, sharedState);
    }

    class ColouredSearchableTextOutput extends JDETextEditor
    {
        ColouredTextArea output;

        ColouredSearchableTextOutput()
        {
            super(null, null, false);
        }

        protected void init(SharedTextEditorState sharedState)
        {
            super.init(sharedState);

            editor = new ColouredTextArea(EDITOR_TEXT_SIZE);
            editor.setColours(Color.web("#007C29"));
            editor.setMinHeight(50);

            setCenter(editor);
            output = (ColouredTextArea) editor;
        }
    }

    protected void showSearchBox()
    {
        super.showSearchBox();
        jjspEngineOutput.showSearchBox();
    }

    protected void hideSearchBox()
    {
        super.hideSearchBox();
        jjspEngineOutput.hideSearchBox();
    }

    protected synchronized void init(SharedTextEditorState sharedState)
    {
        if (getURI().toString().endsWith(".jjsp") || getURI().toString().endsWith(".jet"))
            editor = new JDEditor();
        else
            editor = new JSEditor();

        editor.setSharedTextEditorState(sharedState);
        editor.setMinHeight(100);
        log = new HTTPLogView();
        localStoreView = new LocalStoreView();

        errorLine = -1;
        jjspEngine = null;
        currentError = null;
        statusMessage = "";
        siteOutput = "";
        argList = getDefaultArgs();

        jjspEngineOutput = new ColouredSearchableTextOutput();

        leftPane = new BorderPane();
        leftPane.setCenter(editor);
        leftPane.setMinWidth(550);

        searchBox = new BorderPane();
        leftPane.setTop(searchBox);

        Tab tab1 = new Tab("JJSP Output");
        tab1.setContent(jjspEngineOutput);
        tab1.setClosable(false);
        Tab tab2 = new Tab("HTTP Log");
        tab2.setContent(log);
        tab2.setClosable(false);
        Tab tab3 = new Tab("Local JJSP Store");
        tab3.setContent(localStoreView);
        tab3.setClosable(false);


        TabPane tabs = new TabPane();
        tabs.setSide(Side.BOTTOM);
        tabs.getTabs().addAll(tab1, tab2, tab3);
        tabs.setOnMousePressed((evt) ->
                          {
                              Tab tt = tabs.getSelectionModel().getSelectedItem();
                              if (tt != null)
                                  ((Node) tt.getContent()).requestFocus();
                          });

        mainSplit = new SplitPane();
        mainSplit.setOrientation(Orientation.VERTICAL);
        mainSplit.getItems().addAll(leftPane, tabs);
        mainSplit.setDividerPosition(0, 0.66);
        setCenter(mainSplit);

        clearStatus();
        appendStatus("Ready: "+new Date(), null);
        loadFromURI();
    }

    protected FileChooser initFileChooser()
    {
        FileChooser fileChooser = new FileChooser();
        if (editor instanceof JDEditor)
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("JJSP Script", "*.jjsp"), new FileChooser.ExtensionFilter("JJSP Source Code", "*.jet"));
        else
            fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("JJSP Code", "*.jf"));
        return fileChooser;
    }

    public synchronized Menu[] createMenus()
    {
        MenuItem compile = new MenuItem("Compile + Run");
        compile.setAccelerator(new KeyCodeCombination(KeyCode.F5));
        compile.setOnAction((evt)-> compile());

        MenuItem reparse = new MenuItem("Reparse JJSP");
        reparse.setAccelerator(new KeyCodeCombination(KeyCode.F4));
        reparse.setOnAction((evt)-> reparseJJSP());

        MenuItem stop = new MenuItem("Stop Server");
        stop.setOnAction((evt)-> stop());

        MenuItem saveArchive = new MenuItem("Save Local Content in ZIP Archive");
        saveArchive.setOnAction((evt)->
                            {
                                if ((jjspEngine == null) || (jjspEngine.getRuntime() == null))
                                {
                                    clearStatus();
                                    appendStatus("No Output available - compile first", null);
                                    return;
                                }

                                FileChooser fc1 = getFileChooser();
                                FileChooser zfc = new FileChooser();
                                zfc.setInitialDirectory(fc1.getInitialDirectory());
                                zfc.setTitle("Save Local Content as ZIP Archive");
                                zfc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("ZIP Archive", "*.zip"));

                                File file = zfc.showSaveDialog(getScene().getWindow());
                                if (file != null)
                                {
                                    try
                                    {
                                        byte[] archiveBytes = jjspEngine.getRuntime().toZIPArchive();
                                        FileOutputStream fout = new FileOutputStream(file);
                                        fout.write(archiveBytes);
                                        fout.close();
                                    }
                                    catch (Exception e)
                                    {
                                        clearStatus();
                                        appendStatus("Error saving Local content as ZIP Output", e);
                                    }
                                }
                            });

        MenuItem clearHTTPLog = new MenuItem("Clear HTTP Log");
        clearHTTPLog.setOnAction((evt)-> log.clear());

        MenuItem extraArgs = new MenuItem("Runtime Args");
        extraArgs.setOnAction((evt)-> showArgsPopup());

        Menu[] mm = super.createMenus();
        mm[0].setText("JJSP Actions");
        mm[0].getItems().addAll(new SeparatorMenuItem(), compile, reparse, new SeparatorMenuItem(), stop, new SeparatorMenuItem(), extraArgs, new SeparatorMenuItem(), saveArchive, clearHTTPLog);

        CheckMenuItem showTranslation = new CheckMenuItem("Show JJSP Script Translation");
        showTranslation.setSelected(mainSplit.getItems().get(0) != leftPane);
        showTranslation.setOnAction((evt)->
                               {
                                   double pos = mainSplit.getDividerPositions()[0];

                                   mainSplit.getItems().set(0, new BorderPane());
                                   if (showTranslation.isSelected())
                                   {
                                       SplitPane hSplit = new SplitPane();
                                       hSplit.setOrientation(Orientation.HORIZONTAL);
                                       hSplit.getItems().addAll(leftPane, ((JDEditor) editor).translatedJJSP);
                                       hSplit.setDividerPosition(0, 0.66);
                                       mainSplit.getItems().set(0, hSplit);
                                   }
                                   else
                                       mainSplit.getItems().set(0, leftPane);
                                   mainSplit.setDividerPosition(0, pos);
                               });

        MenuItem clearError = new MenuItem("Clear Error Status");
        clearError.setOnAction((evt)-> clearError());

        Menu display = new Menu("Further Options");
        if (editor instanceof JDEditor)
            display.getItems().addAll(clearError, showTranslation);
        else
            display.getItems().addAll(clearError);

        return new Menu[]{mm[0], display};
    }

    public Throwable getError()
    {
        return currentError;
    }

    public boolean isShowingError()
    {
        return currentError != null;
    }

    public void clearError()
    {
        if (isShowingError())
        {
            closeServices();
            appendStatus("Error status cleared "+new Date(), null);
        }
    }

    public static int extractJSErrorLine(Throwable t)
    {
        for (Throwable tt = t; tt != null; tt = tt.getCause())
        {
            if (tt instanceof ScriptException)
                return ((ScriptException) tt).getLineNumber();

            int result = -1;
            StackTraceElement[] ss = tt.getStackTrace();
            for (int i=0; i<ss.length; i++)
            {
                StackTraceElement s = ss[i];
                if (!s.getClassName().startsWith("jdk.nashorn.internal.scripts.Script"))
                    continue;

                if (s.getFileName().startsWith("<eval>"))
                    result = s.getLineNumber();
                else if (s.getFileName().startsWith(JJSPRuntime.TOP_LEVEL_SOURCE_PATH))
                    result = s.getLineNumber();
            }

            if (result != -1)
                return result;
        }

        return -1;
    }

    public void clearStatus()
    {
        currentError = null;
        statusMessage = "";
        jjspEngineOutput.output.setText("");
    }

    public void appendStatus(String message, Throwable t)
    {
        Throwable mainCause = t;
        for (Throwable tt = t; tt != null; tt = tt.getCause())
        {
            if (tt instanceof InvocationTargetException)
                mainCause = tt.getCause();
        }

        int line = extractJSErrorLine(t);
        if (line >= 0)
        {
            line = Math.max(0, line-1);
            double scrollPos = setErrorLine(line);
            editor.setScrollBarPosition(scrollPos);
            editor.highlightLine(line);
        }

        // This bit needed to drop multiple blank lines from the output
        String newStatus = statusMessage + message;
        String[] lines = newStatus.split("\n");

        boolean previousBlank = false;
        StringBuffer buf = new StringBuffer();
        for (int i=0; i<lines.length; i++)
        {
            String outLine = lines[i].trim();
            if (outLine.length() == 0)
            {
                if (previousBlank)
                    continue;
                previousBlank = true;
            }
            else
                previousBlank = false;

            buf.append(outLine+"\n");
        }

        statusMessage = buf.toString();
        //statusMessage += message; //an alternative if cleansing multiple blank lines isn't wanted
        currentError = mainCause;

        if (mainCause != null)
        {
            jjspEngineOutput.output.setText("");
            jjspEngineOutput.output.appendColouredText(statusMessage, Color.MAGENTA);
            jjspEngineOutput.output.appendColouredText("\n\n"+toString(mainCause), Color.RED);
        }
        else
            jjspEngineOutput.output.setText(statusMessage);

        jjspEngineOutput.output.setScrollBarPosition(100.0);
        jjspEngineOutput.output.refreshView();
    }

    public String getStatusText()
    {
        jjspEngineOutput.output.refreshView();
        return jjspEngineOutput.output.getText();
    }

    public void appendStatusText(String text)
    {
        if ((text == null) || (text.length() == 0))
            return;
        statusMessage += text;

        jjspEngineOutput.output.setText("");
        jjspEngineOutput.output.appendColouredText(statusMessage, Color.web("#660033"));
        jjspEngineOutput.output.setScrollBarPosition(100.0);
        jjspEngineOutput.output.refreshView();
    }

    public void requestFocus()
    {
        jjspEngineOutput.output.refreshView();
        editor.refreshView();
        editor.requestFocus();
        super.requestFocus();
    }

    public double setErrorLine(int line)
    {
        errorLine = line;
        if (editor instanceof JDEditor)
            return ((JDEditor) editor).setErrorLine(line);
        return -1;
    }

    public double setSourceAndPosition(String jsSrc, int line, double scrollBarPos)
    {
        String editMessage = "<< Source edited since last compilation >>\n";
        String outText = jjspEngineOutput.output.getText();
        if (!outText.startsWith(editMessage))
            jjspEngineOutput.output.insertColouredText(0, editMessage, Color.BLUE);

        if (editor instanceof JDEditor)
            return ((JDEditor) editor).setSourceAndPosition(jsSrc, line, scrollBarPos);
        return -1;
    }

    public synchronized void closeServices()
    {
        setErrorLine(-1);
        generatedOutputs = null;
        //Don't clear the local store view as this leaves things around to be examined
        //localStoreView.setEnvironment(null);

        if ((jjspEngine != null) && !jjspEngine.stopped())
            jjspEngine.stop();

        jjspEngine = null;
        appendStatus(new Date()+" JJSP Engine stopped", null);
    }

    public synchronized void stop()
    {
        closeServices();
        generatedOutputs = new URI[0];
    }

    class JDEEngine extends Engine
    {
        Throwable error;
        ArrayList resultURIs;

        public JDEEngine(String jsSrc, URI srcURI, Environment jjspEnv, Map args)
        {
            super(jsSrc, srcURI, jjspEnv.getRootURI(), jjspEnv.getLocalCacheDir(), args);
            resultURIs = new ArrayList();
            error = null;
        }

        protected void compile(JJSPRuntime runtime, String jsSrc) throws Exception
        {
            Date startTime = new Date();
            println("Compilation Started "+startTime+"\nJJSP Version"+getVersion());
            println();
            super.compile(runtime, jsSrc);

            Date finishTime = new Date();
            long timeTaken = finishTime.getTime() - startTime.getTime();

            println("\n\nCompilation Completed OK "+finishTime);
            println("It took "+timeTaken+" ms.");
            println();
        }

        class JDELogger extends Logger
        {
            JDELogger()
            {
                super("JDE", null);
                setUseParentHandlers(false);
            }

            public void log(LogRecord lr)
            {
                println(new Date(lr.getMillis())+" "+lr.getLevel()+" "+lr.getSourceClassName()+"  "+lr.getSourceMethodName()+"  "+lr.getMessage());
            }
        }

        protected Logger getLogger()
        {
            return new JDELogger();
        }

        protected HTTPServerLogger getHTTPLog(JJSPRuntime runtime) throws Exception
        {
            HTTPServerLogger logger = runtime.getHTTPLogger();
            if (logger == null)
                return log;

            return (logEntry) -> {logger.requestProcessed(logEntry); log.requestProcessed(logEntry);};
        }

        protected void serverListening(HTTPServer server, ServerSocketInfo socketInfo, Exception listenError) throws Exception
        {
            URI serviceRoot = null;
            if (socketInfo.isSecure)
            {
                serviceRoot = new URI("https://localhost:"+socketInfo.port+"/");
                if (socketInfo.port == 443)
                    serviceRoot = new URI("https://localhost/");
            }
            else
            {
                serviceRoot = new URI("http://localhost:"+socketInfo.port+"/");
                if (socketInfo.port == 80)
                    serviceRoot = new URI("http://localhost/");
            }

            println(new Date()+"  Server Listening on "+socketInfo);
            resultURIs.add(serviceRoot);

            if (editor instanceof JDEditor)
            {
                String[] allPaths = getRuntime().listLocal();
                for (int i=0; i<allPaths.length; i++)
                {
                    String path = allPaths[i];
                    path = JJSPRuntime.checkLocalResourcePath(path);
                    resultURIs.add(serviceRoot.resolve(path));
                }
            }
            else // else use a sitemap....
            {
                try
                {
                    URL sitemapURL = serviceRoot.resolve("/sitemap.xml").toURL();
                    URLConnection conn = sitemapURL.openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)");

                    String sitemapXML = Utils.loadText(conn.getInputStream());
                    //System.out.println(sitemapXML);

                    int pos = 0;
                    while (true)
                    {
                        int locIndex = sitemapXML.indexOf("<loc>", pos);
                        if (locIndex < 0)
                            break;
                        int locIndex2 = sitemapXML.indexOf("</loc>", locIndex);
                        if (locIndex2 < 0)
                            break;
                        pos = locIndex2 + 6;

                        String url = sitemapXML.substring(locIndex+5, locIndex2);
                        url = url.replace("\n", "");
                        url = url.replace("\r", "");
                        url = url.replace("\t", "");
                        url = url.replace(" ", "");

                        URI uri = new URI(url);
                        uri = serviceRoot.resolve(uri.getPath());
                        resultURIs.add(uri);
                    }
                }
                catch (Exception e)
                {
                    println("No sitemap.xml found ("+e.getMessage()+")");
                }
            }
        }

        protected synchronized void runtimeError(Throwable t)
        {
            println("\n\nJJSP Error: "+t.getMessage());
            error = t;
        }

        protected void launchComplete(HTTPServer server, JJSPRuntime runtime, boolean isListening) throws Exception
        {
            URI[] uris = new URI[resultURIs.size()];
            resultURIs.toArray(uris);
            generatedOutputs = uris;
            println(new Date()+" JJSP Server Started");

            Platform.runLater(() -> localStoreView.setEnvironment(runtime));
        }

        protected void engineStopped()
        {
            generatedOutputs = null;
            super.engineStopped();
        }
    }

    public String getCompiledJS()
    {
        if (editor instanceof JDEditor)
            return ((JDEditor) editor).getTranslatedText();
        else
            return editor.getText();
    }

    public void reparseJJSP()
    {
        synchronized (this)
        {
            if (jjspEngine == null)
            {
                appendStatus("No JJSP Engine currently running", null);
                return;
            }

            setErrorLine(-1);
            generatedOutputs = null;
            clearStatus();
            log.clear();
        }

        appendStatus(new Date()+" Reparsing JJSP Source", null);
        try
        {
            jjspEngine.restart();
            appendStatus(new Date()+" JJSP Engine restarted", null);
        }
        catch (Exception e)
        {
            appendStatus(new Date()+" Error during JJSP Restart", e);
        }
    }

    public void compile()
    {
        closeServices();
        clearStatus();
        log.clear();

        try
        {
            String jsSrc = getCompiledJS();
            Map args = Args.parseArgs(argList);

            synchronized (this)
            {
                jjspEngine = new JDEEngine(jsSrc, getURI(), JDE.getEnvironment(), args);
                jjspEngine.start();
            }
        }
        catch (Exception e)
        {
            appendStatus("Failed to create JJSP Engine", e);
        }
    }

    public synchronized void setDisplayed(boolean isShowing)
    {
        super.setDisplayed(isShowing);

        JDEEngine je = jjspEngine;
        if ((je != null) && je.stopRequested() && !je.stopped())
            je.stop();
        if ((je != null) && je.stopped())
            closeServices();

        jjspEngineOutput.output.setDisplayed(isShowing);
        editor.setDisplayed(isShowing);

        if (isShowing && (je != null))
        {
            String toAppend = je.getLatestConsoleOutput();
            if ((toAppend != null) && (toAppend.length() > 0) || (je.error != currentError))
                appendStatus(toAppend, je.error);
        }
    }

    class JDEditor extends Editor
    {
        TextEditor translatedJJSP;

        JDEditor()
        {
            super(EDITOR_TEXT_SIZE);
            setMinSize(400, 200);

            translatedJJSP = new TextEditor(EDITOR_TEXT_SIZE);
            translatedJJSP.setEditable(false);
            translateJJSP();
        }

        void translateJJSP()
        {
            String compiledJJSP = getJJSPParser().translateToJavascript();
            translatedJJSP.highlightLine(getCurrentLine());
            double scrollBarPos = getScrollBarPosition();
            setSourceAndPosition(compiledJJSP, getCurrentLine(), scrollBarPos);
        }

        public void requestFocus()
        {
            refreshView();
            super.requestFocus();
        }

        public void refreshView()
        {
            super.refreshView();
            if (translatedJJSP != null)
                translatedJJSP.refreshView();
        }

        public void setDisplayed(boolean isShowing)
        {
            super.setDisplayed(isShowing);
            if (translatedJJSP != null)
                translatedJJSP.setIsShowing(isShowing);
        }

        protected void contentChanged()
        {
            if (translatedJJSP != null)
                translateJJSP();
        }

        protected void caretPositioned(int line, int charPos)
        {
            if (translatedJJSP != null)
                translatedJJSP.highlightLine(line);
        }

        protected void textScrolled(double scrollPosition)
        {
            if (translatedJJSP != null)
                translatedJJSP.setScrollBarPosition(scrollPosition);
        }

        double setErrorLine(int errorLine)
        {
            if (errorLine >= 0)
            {
                translatedJJSP.highlightLine(errorLine);
                int lineStatus = translatedJJSP.lineInViewport(errorLine);
                if (lineStatus != 0)
                    translatedJJSP.scrollToLine(errorLine);
            }
            else
                translatedJJSP.clearSelection();

            return translatedJJSP.getScrollBarPosition();
        }

        double setSourceAndPosition(String jsSrc, int line, double scrollBarPos)
        {
            translatedJJSP.setText(jsSrc);
            translatedJJSP.setScrollBarPosition(scrollBarPos);
            translatedJJSP.highlightLine(line);

            int lineStatus = translatedJJSP.lineInViewport(line);
            if (lineStatus != 0)
                translatedJJSP.scrollToLine(line);

            return translatedJJSP.getScrollBarPosition();
        }

        public String getTranslatedText()
        {
            translateJJSP();
            return translatedJJSP.getText();
        }
    }

    private String getDefaultArgs()
    {
        Environment jjspEnv = JDE.getEnvironment();
        Map args = jjspEnv.getArgs();
        args.put("jde", null);

        return Args.toArgString(args);
    }

    private void showArgsPopup()
    {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.WINDOW_MODAL);
        Button ok = new Button("OK");

        TextField argField = new TextField(argList);
        argField.setOnAction((evt) -> ok.fire());
        argField.setPrefWidth(350);

        Button reset = new Button("Reset");
        reset.setOnAction((evt) -> { argList = getDefaultArgs(); argField.setText(argList); });

        GridPane gp = new GridPane();
        gp.setHgap(10);
        gp.setVgap(10);

        gp.add(new Label("Define JJSP Args"), 0, 0);
        gp.add(argField, 1, 0);

        ok.setOnAction((evt) ->
                       {
                           argList = argField.getText();
                           dialogStage.close();
                       });

        Button cancel = new Button("Cancel");
        cancel.setOnAction((evt) -> dialogStage.close());

        HBox hBox = new HBox(10);
        BorderPane.setMargin(hBox, new Insets(10,0,0,0));
        hBox.setAlignment(Pos.BASELINE_CENTER);
        hBox.getChildren().addAll(reset, cancel, ok);

        BorderPane bp = new BorderPane();
        bp.setStyle("-fx-font-size: 16px; -fx-padding:10px");
        bp.setCenter(gp);
        bp.setBottom(hBox);

        dialogStage.setTitle("JJSP Runtime Arguments");
        dialogStage.setScene(new Scene(bp));
        if (ImageIconCache.getJJSPImage() != null)
            dialogStage.getIcons().add(ImageIconCache.getJJSPImage());
        dialogStage.sizeToScene();
        dialogStage.setResizable(false);
        dialogStage.showAndWait();
    }
}
