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
import javafx.scene.*;
import javafx.scene.input.*;
import javafx.event.*;
import javafx.scene.paint.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import javafx.geometry.*;
import javafx.stage.*;
import javafx.collections.*;

import jjsp.engine.*;
import jjsp.util.*;
import jjsp.http.*;
import jjsp.http.filters.*;

public class JDE extends Application
{
    public static final int DEFAULT_PORT = 2015;
    public static final String CACHE_DIR_NAME = ".jde";

    private JDEMenus menuBar;
    private AutoSave autoSave;
    private boolean autoSyncEditors;
    private UIUpdater uiUpdater;
    private ImageIconCache iconCache;
    private InternalTree resourceTree;
    private LocalFileServer localServer;
    private SharedTextEditorState sharedState;

    private Stage primaryStage;
    private SplitPane outputSplit;
    private UIStatePersister uiState;
    private OutputTabs outputTabs1, outputTabs2;

    private static File jdeCacheDir;
    private static Environment jjspEnv; 

    class JDEMenus extends HBox
    {
        MenuBar left;
        HTTPLogView httpLog;
        FileChooser fileChooser;
        DirectoryChooser dirChooser;

        JDEMenus(int port)
        {
            left = new MenuBar();
            left.setMinWidth(60);
            left.setUseSystemMenuBar(false);
            HBox.setHgrow(left, Priority.ALWAYS);

            dirChooser = new DirectoryChooser();
            File cwd = new File(System.getProperty("user.dir"));
            File dir = cwd;
            if (dir.getParentFile() != null)
                dir = dir.getParentFile();
            dirChooser.setInitialDirectory(dir);
            
            fileChooser = new FileChooser();
            fileChooser.setInitialDirectory(cwd);

            left.getMenus().addAll(buildFileMenu());

            MenuItem openLocalBrowser = new MenuItem("Open in External Browser");
            openLocalBrowser.setOnAction((evt) -> { try { launchExternalBrowser(new URI("http://localhost:"+port+"/")); } catch (Exception e) {}});

            MenuBar right = new MenuBar();
            Menu mm = new Menu("Local File Server Port: "+port);
            right.getMenus().addAll(new Menu("JJSP Version: "+Utils.getJarVersion()), mm);

            try
            {
                httpLog = new HTTPLogView();
            }
            catch (Exception e) {}
            MenuItem showLog = new MenuItem("Show Server HTTP Log");
            showLog.setOnAction(evt -> showLogPopup());
            mm.getItems().addAll(showLog, openLocalBrowser);

            getChildren().addAll(left, right);
        }

        private void showLogPopup()
        {
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.WINDOW_MODAL);

            BorderPane bp = new BorderPane();
            bp.setCenter(httpLog);
            
            dialogStage.setTitle("JDE HTTP Server Log");
            dialogStage.setScene(new Scene(bp));
            if (iconCache.getJJSPImage() != null)
                dialogStage.getIcons().add(iconCache.getJJSPImage());
            dialogStage.sizeToScene();
            dialogStage.show();
        }

        private void showAddServicePopup()
        {
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.WINDOW_MODAL);

            TextField serviceName = new TextField();
            serviceName.setPromptText("Service Name");
            serviceName.setStyle("-fx-font-size: 16px");

            Button ok = new Button("OK");
            ok.setOnAction((evt) -> 
                           { 
                               dialogStage.close(); 
                               String name = serviceName.getText(); 
                               try
                               {
                                   if ((name != null) && (name.length() > 1)) 
                                       jjspEnv.addService(name);
                               }
                               catch (Exception e) {}
                               
                               resourceTree.forceUpdate();
                               resourceTree.rescanTree();
                           });
            Button cancel = new Button("Cancel");
            cancel.setOnAction((evt) -> dialogStage.close());

            HBox hBox = new HBox(10);
            hBox.setAlignment(Pos.BASELINE_RIGHT);
            hBox.getChildren().addAll(ok, cancel);
            
            BorderPane bp = new BorderPane();
            bp.setCenter(serviceName);
            bp.setBottom(hBox);
            bp.setPrefWidth(300);
            bp.setPrefHeight(20);
            BorderPane.setMargin(hBox, new Insets(10,0,0,0));
            bp.setStyle("-fx-font-size: 16px; -fx-padding:10px");
            
            dialogStage.setTitle("New Service Name:");
            dialogStage.setScene(new Scene(bp));
            if (iconCache.getJJSPImage() != null)
                dialogStage.getIcons().add(iconCache.getJJSPImage());
            dialogStage.sizeToScene();
            dialogStage.setResizable(false);
            serviceName.setOnAction((evt) -> ok.fire());
            dialogStage.show();
            cancel.requestFocus();
        }

        Menu buildFileMenu()
        {
            MenuItem openBrowser = new MenuItem("New Browser Tab");
            openBrowser.setOnAction(event -> { try { openNewURITab(new URI("http://localhost/")); } catch (Exception e) {} });

            MenuItem openDirectory = new MenuItem("Open Local Directory");
            openDirectory.setOnAction(event -> 
                                      { 
                                          File selected = dirChooser.showDialog(getScene().getWindow()); 
                                          if (selected == null) 
                                              return;
                                          
                                          resourceTree.addURI(selected.toURI());  
                                          resourceTree.expandAndShow(selected.toURI());
                                      });

            MenuItem openFile = new MenuItem("Open Local File");
            openFile.setOnAction(event -> 
                                    { 
                                        fileChooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("All Files", "*.*"));
                                        File selected = fileChooser.showOpenDialog(getScene().getWindow()); 
                                        if (selected == null) 
                                            return;

                                        resourceTree.addURI(selected.getParentFile().toURI()); 
                                        resourceTree.addURI(selected.toURI());  
                                        resourceTree.expandAndShow(selected.toURI());
                                        openNewURITab(selected.toURI());
                                    });

            MenuItem newService = new MenuItem("New Service");
            newService.setOnAction(event -> showAddServicePopup());

            MenuItem newFile = new MenuItem("New File");
            newFile.setOnAction(event -> 
                                    { 
                                        fileChooser.getExtensionFilters().setAll(
                                                                                 new FileChooser.ExtensionFilter("JJSP Script", "*.jjsp"),
                                                                                 new FileChooser.ExtensionFilter("JJSP Code", "*.jf"), 
                                                                                 new FileChooser.ExtensionFilter("Javascript", "*.js"), 
                                                                                 new FileChooser.ExtensionFilter("Text Files", "*.txt"), 
                                                                                 new FileChooser.ExtensionFilter("Cascading Style Sheet", "*.css"), 
                                                                                 new FileChooser.ExtensionFilter("HTML", "*.html", "*.htm"), 
                                                                                 new FileChooser.ExtensionFilter("Comma Separated Values", "*.csv"),
                                                                                 new FileChooser.ExtensionFilter("SQL Script", "*.sql"),
                                                                                 new FileChooser.ExtensionFilter("Jet Source Code", "*.jet")
                                                                                 );

                                        File selected = fileChooser.showSaveDialog(getScene().getWindow()); 
                                        if (selected == null) 
                                            return;
                                        
                                        try
                                        {
                                            selected.createNewFile();
                                        }
                                        catch (Exception e) {}
                                        
                                        resourceTree.addURI(selected.toURI());  
                                        resourceTree.expandAndShow(selected.toURI());
                                        openNewURITab(selected.toURI());
                                        
                                        resourceTree.forceUpdate();
                                        resourceTree.rescanTree();
                                    });

            MenuItem addLibrary = new MenuItem("Add Library");
            addLibrary.setOnAction(event -> 
                                     {
                                         fileChooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("Java Archive (JAR)", "*.jar"));
                                         
                                         File selected = fileChooser.showOpenDialog(getScene().getWindow()); 
                                         if (selected == null) 
                                             return;
                                         try
                                         {
                                             jjspEnv.addLibrary(selected.getName(), Utils.load(selected));
                                         }
                                         catch (Exception e) {}

                                         resourceTree.forceUpdate();
                                         resourceTree.rescanTree();
                                     });

            CheckMenuItem autoSync = new CheckMenuItem("Auto Reload Editor Content");
            autoSync.setSelected(autoSyncEditors);
            autoSync.setOnAction((evt) -> autoSyncEditors = !autoSyncEditors);

            MenuItem single = new MenuItem("Move All Tabs to Left Pane");
            single.setOnAction((evt) -> clearRightTabs());
            MenuItem moveLeft = new MenuItem("Move to Left Pane");
            moveLeft.setOnAction((evt) -> moveToLeftPane());
            MenuItem moveRight = new MenuItem("Move to Right Pane");
            moveRight.setOnAction((evt) -> moveToRightPane());

            MenuItem quit = new MenuItem("Quit");
            quit.setOnAction(evt -> stop());

            MenuItem closeAll = new MenuItem("Close All");
            closeAll.setOnAction(evt -> {outputTabs1.removeAll(); outputTabs2.removeAll();});
            
            Menu fileMenu = new Menu("JDE");
            //file.setStyle("-fx-font-size: 16px");
            fileMenu.getItems().addAll(newFile, openBrowser, newService, new SeparatorMenuItem(), openFile, openDirectory, addLibrary, new SeparatorMenuItem(), moveRight, moveLeft, single, closeAll, autoSync, new SeparatorMenuItem(), quit);
            return fileMenu;
        }

        void setJDEMenus(Menu[] menus)
        {
            left.getMenus().setAll(buildFileMenu());
            if (menus != null)
            {
                for (int i=0; i<menus.length; i++)
                    left.getMenus().add(menus[i]);
            }
        }
    }

    class InternalTree extends URIResourceTree
    {
        FileChooser fileChooser;

        InternalTree(ImageIconCache iconCache)
        {
            super(iconCache);
            File cwd = new File(System.getProperty("user.dir"));
            fileChooser = new FileChooser();
            fileChooser.setInitialDirectory(cwd);
        }

        protected MenuItem[] additionalPopupItems(URI uri, TreeCell target)
        {
            if (!uri.getScheme().equals("file"))
                return null;

            try
            {
                File svcDir = new File(uri);
                if (!svcDir.getParentFile().toURI().equals(jjspEnv.getServicesURI()))
                    return null;

                String serviceName = svcDir.getName();
                MenuItem addServiceFiles = new MenuItem("Add Service Files");
                addServiceFiles.setOnAction(event -> 
                                            {
                                                fileChooser.getExtensionFilters().removeAll();
                                         
                                                List selectedFiles = fileChooser.showOpenMultipleDialog(getScene().getWindow()); 
                                                if (selectedFiles == null) 
                                                    return;
                                                try
                                                {
                                                    for (int i=0; i<selectedFiles.size(); i++)
                                                    {
                                                        File ff = (File) selectedFiles.get(i);
                                                        byte[] data = Utils.load(ff);
                                                        jjspEnv.addServiceFile(serviceName, ff.getName(), data);
                                                    }
                                                }
                                                catch (Exception e) {}
                                                forceUpdate();
                                                rescanTree();
                                            });

                return new MenuItem[]{addServiceFiles};
            }
            catch (Exception e) {}

            return null;
        }
        
        public boolean removeURI(URI uri)
        {
            outputTabs1.removeURI(uri);
            outputTabs2.removeURI(uri);
            return super.removeURI(uri);
        }
        
        public boolean addURI(URI uri)
        {
            boolean result = super.addURI(uri);
            if (result && isRoot(uri) && uri.getScheme().equals("file"))
            {
                URITreeItem item = getTreeItemFor(uri);
                if (item != null)
                    item.setExpanded(true);
            }
            return result;
        }

        public boolean removeURIAndLeaveTabs(URI uri)
        {
            return super.removeURI(uri);
        }

        protected void showItem(URI uri)
        {
            openNewURITab(uri);
        }

        protected void showItemAsText(URI uri)
        {
            openNewTextTab(uri);
        }
    }

    class UIStatePersister
    {
        private static final long MAGIC_NUMBER = 0xF78998AABBB28128l;

        private ReplicatedFile rfile;

        LinkedHashSet tabURIs1, tabURIs2;
        double screenX, screenY, screenWidth, screenHeight, splitPosition;

        UIStatePersister()
        {
            tabURIs1 = new LinkedHashSet();
            tabURIs2 = new LinkedHashSet();

            Screen screen = Screen.getPrimary();
            Rectangle2D bounds = screen.getVisualBounds();
            screenX = 100;
            screenY = 100;
            screenHeight = Math.min(bounds.getHeight(), 1024);
            screenWidth = Math.min(bounds.getWidth(), 2400)-20;
            splitPosition = 1.0;
            
            try
            {
                rfile = new ReplicatedFile(new File(jdeCacheDir, "JDEState.dat"), MAGIC_NUMBER);

                DataInputStream din = new DataInputStream(new ByteArrayInputStream(rfile.getLatestEntryData()));
                screenX = din.readDouble();
                screenY = din.readDouble();
                screenWidth = din.readDouble();
                screenHeight = din.readDouble();

                int tabCount1 = din.readInt();
                for (int i=0; i<tabCount1; i++)
                    tabURIs1.add(new URI(din.readUTF()));

                int tabCount2 = din.readInt();
                for (int i=0; i<tabCount2; i++)
                    tabURIs2.add(new URI(din.readUTF()));

                splitPosition = din.readDouble();
            }
            catch (Exception e) {}
        }

        void startAutoPersisting()
        {
            Thread t = new Thread(() -> 
                                  {
                                      while (true)
                                      {
                                          try
                                          {
                                              Thread.sleep(10000);
                                              Platform.runLater(()->safePersistUIData());
                                          }
                                          catch (Exception e) {}
                                      }
                                  });

            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY-3);
            t.start();
        }

        URI[] getOuput1URIs()
        {
            URI[] result = new URI[tabURIs1.size()];
            tabURIs1.toArray(result);
            return result;
        }

        URI[] getOuput2URIs()
        {
            URI[] result = new URI[tabURIs2.size()];
            tabURIs2.toArray(result);
            return result;
        }

        void safePersistUIData()
        {
            try
            {
                persistUIData();
            }
            catch (Exception e) 
            {
                e.printStackTrace();
            }
        }

        void persistUIData() throws Exception
        {
            persistUIData(true);
        }

        void persistUIData(boolean saveInBackground) throws Exception
        {
            boolean dataAltered = false;
            LinkedHashSet newSet1 = new LinkedHashSet();
            LinkedHashSet newSet2 = new LinkedHashSet();
            
            URI[] uris1 = outputTabs1.getJDEComponentURIs();
            for (int i=0; i<uris1.length; i++)
            {
                if (!tabURIs1.contains(uris1[i]))
                    dataAltered = true;
                newSet1.add(uris1[i]);
            }

            URI[] uris2 = outputTabs2.getJDEComponentURIs();
            for (int i=0; i<uris2.length; i++)
            {
                if (!tabURIs2.contains(uris2[i]))
                    dataAltered = true;
                newSet2.add(uris2[i]);
            }

            double x = primaryStage.getX();
            double y = primaryStage.getY();
            double width = primaryStage.getWidth();
            double height = primaryStage.getHeight();
            
            double split = outputSplit.getDividerPositions()[0];
            
            dataAltered = dataAltered | (x != screenX) | (y != screenY) | (screenWidth != width) | (screenHeight != height) | (split != splitPosition);

            if (!dataAltered)
                return;
            tabURIs1 = newSet1;
            tabURIs2 = newSet2;
            screenX = x;
            screenY = y;
            screenHeight = height;
            screenWidth = width;
            splitPosition = split;
            
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(bout);
            dout.writeDouble(screenX);
            dout.writeDouble(screenY);
            dout.writeDouble(screenWidth);
            dout.writeDouble(screenHeight);
            
            dout.writeInt(uris1.length);
            for (int i=0; i<uris1.length; i++)
                dout.writeUTF(uris1[i].toString());

            dout.writeInt(uris2.length);
            for (int i=0; i<uris2.length; i++)
                dout.writeUTF(uris2[i].toString());

            dout.writeDouble(splitPosition);
            
            dout.close();
            
            new Thread(()->{try {rfile.storeData(bout.toByteArray());} catch (Exception e) {}}).start();
        }
    }

    class JDESQLPane extends SQLPane
    {
        JDESQLPane(URI sql, SharedTextEditorState state)
        {
            super(sql, state);
        }

        protected ClassLoader getClassLoaderForDatabaseDriver() throws Exception
        {
            return jjspEnv.createLibraryLoader();
        }
    }
    
    class OutputTabs extends URITabPane
    {
        HashMap generatedURIMap;

        OutputTabs(ImageIconCache iconCache)
        {
            super(iconCache);
            generatedURIMap = new HashMap();
        }
        
        protected void selectedComponentChanged(JDEComponent jde)
        {
            if (jde == null)
                menuBar.setJDEMenus(null);
            else    
                menuBar.setJDEMenus(jde.createMenus());
        }
        
        protected void refreshTab(boolean gainedFocus, boolean isDisplayed, boolean wasUpdated, Tab tab, JDEComponent jde)
        {
            if (gainedFocus)
                menuBar.setJDEMenus(jde.createMenus());

            URI uri = jde.getURI();
            String scheme = uri.getScheme();
            boolean isLocalFile = scheme.equals("file");
            boolean isHTTP = scheme.equals("http") ||  scheme.equals("https");

            if (isLocalFile)
            {
                File ff = new File(uri);
                byte[] data = jde.getDataForAutoSave();
                if (data != null)
                    autoSave.save(data, ff);
                
                if (autoSyncEditors)
                {
                    try
                    {
                        if (ff.lastModified() > jde.getLastModified())
                            jde.loadAutoSavedData(Utils.getAsciiBytes(JDETextEditor.loadTextFromURI(uri)));
                    }
                    catch (Exception e) {}
                }
            }

            if (!wasUpdated)
                return;
            if (isHTTP)
                resourceTree.addURI(URIResourceTree.getRootURI(uri));
            resourceTree.addURI(uri);

            try
            {
                URI[] existingURIs = null;
                synchronized (generatedURIMap)
                {
                    existingURIs = (URI[]) generatedURIMap.get(uri);
                    if (existingURIs == null)
                        existingURIs = new URI[0];
                }
                
                URI[] newOutputs = jde.getGeneratedOutputs();
                if (newOutputs == null)
                    return;

                HashSet uriSet = new HashSet();
                for (int i=0; i<newOutputs.length; i++)
                    uriSet.add(newOutputs[i]);

                for (int i=0; i<existingURIs.length; i++)
                {
                    if (!uriSet.contains(existingURIs[i]))
                        resourceTree.removeURIAndLeaveTabs(existingURIs[i]);
                }

                for (int i=0; i<newOutputs.length; i++)
                    resourceTree.addURI(newOutputs[i]);

                synchronized (generatedURIMap)
                {
                    generatedURIMap.put(uri, newOutputs);
                }
                
                outputTabs1.reloadMatchingTabs(newOutputs);
                outputTabs2.reloadMatchingTabs(newOutputs);
            }
            catch (Throwable e) 
            {
                e.printStackTrace();
            }
        }

        void reloadMatchingTabs(URI[] newOutputs)
        {
            ObservableList<Tab> tabs = getTabs();
            for (int i=0; i<tabs.size(); i++)
            {
                Tab tt = tabs.get(i);
                JDEComponent jde2 = (JDEComponent) tt.getContent();
                String uriString = jde2.getURI().toString();
                    
                for (int j=0; j<newOutputs.length; j++)
                {
                    String uri2String = newOutputs[j].toString();
                    if (!uriString.startsWith(uri2String))
                        continue;

                    resourceTree.addURI(jde2.getURI());
                    resourceTree.expandAndShow(jde2.getURI());
                    jde2.reloadContent();
                    break;
                }
            }
        }

        protected void tabRemoved(Tab tab, JDEComponent jde)
        {
            URI src = jde.getURI();
            jde.closeServices();

            synchronized (generatedURIMap)
            {
                URI[] related = (URI[]) generatedURIMap.get(src);
                if (related == null)
                    return;
                generatedURIMap.remove(src);

                for (int i=0; i<related.length; i++)
                    resourceTree.removeURI(related[i]);
                
                if (resourceTree.isRoot(src))
                    resourceTree.removeURI(src);
            }
        }

        boolean openNewTextTab(URI uri)
        {
            JDEComponent addedComponent = null;

            // The HTML FX editor does seem a bit limited sadly....
            /*String urlString = uri.toString().toLowerCase();
            if (urlString.endsWith(".html") || urlString.endsWith(".htm"))
                addedComponent = addNewTab(new JDEHTMLEditor(uri));
            else
            addedComponent = addNewTab(new JDETextEditor(uri, sharedState));*/

            addedComponent = addNewTab(new JDETextEditor(uri, sharedState));

            byte[] savedData = null;
            boolean isFile = uri.getScheme().equals("file");

            if (isFile)
            {
                File ff = new File(uri);
                if (ff.exists())
                {
                    long modTime = autoSave.getModificationTimeOf(ff);
                    if (modTime > ff.lastModified())
                        savedData = autoSave.getLatest(ff);
                }
            }

            if (!isFile)
            {
                URI root = resourceTree.getRootURI(uri);
                resourceTree.addURI(root);
            }

            resourceTree.addURI(uri);
            if (addedComponent != null)
                addedComponent.loadAutoSavedData(savedData);
            return true;
        }

        boolean isMostlyTextContent(URI uri)
        {
            try
            {
                int asciiCount = 0;
                
                byte[] raw = Utils.load(uri);
                for (int i=0; i<raw.length; i++)
                {
                    int ch = 0xFF & raw[i];
                    if ((ch >= 32) && (ch <= 126) || (ch == 11) || (ch == 9) || (ch == 13))
                        asciiCount++;
                }
                
                return asciiCount*100/raw.length > 95;
            }
            catch (Exception e) {}
                
            return false;
        }

        boolean useTextEditorFor(URI uri)
        {
            String fullPath = uri.toString().toLowerCase();
            return fullPath.endsWith(".sh") || fullPath.endsWith(".txt") || fullPath.endsWith(".java") || fullPath.endsWith(".css") || fullPath.endsWith(".jjsp") || fullPath.endsWith(".jet") || fullPath.endsWith(".md") || fullPath.endsWith("makefile");
        }

        boolean openNewURITab(URI uri)
        {
            if (showTab(uri) != null)
                return false;

            byte[] savedData = null;
            JDEComponent addedComponent = null;
            String fullPath = uri.toString().toLowerCase();
            String scheme = uri.getScheme().toLowerCase();
            
            boolean isFile = scheme.equals("file");
            boolean isHTTP = scheme.equals("http") || scheme.equals("https");
            boolean isDatabase = !isFile && !isHTTP;

            if (isDatabase)
            {
                String tableName = SQLTablePane.getDBTableName(uri);
                String schemaName = SQLTablePane.getDBSchemaName(uri);
                if ((tableName == null) || (schemaName == null))
                    return false;
                
                JDEComponent[] comps = getJDEComponents();
                for (int i=0; i<comps.length; i++)
                {
                    if (!(comps[i] instanceof SQLPane))
                        continue;

                    SQLPane sp = (SQLPane) comps[i];
                    if (sp.getDriver() == null) 
                        continue;
                    
                    if (sp.isOriginatorOf(uri))
                    {
                        try
                        {
                            addedComponent = addNewTab(new SQLTablePane(schemaName, tableName, sp.getDriver()));
                            resourceTree.addURI(uri);
                            return true;
                        }
                        catch (Exception e) 
                        {
                            e.printStackTrace();
                        }
                    }
                }

                return false;
            }

            if (isFile)
            {
                File ff = new File(uri);
                if (ff.exists())
                {
                    long modTime = autoSave.getModificationTimeOf(ff);
                    if (modTime > ff.lastModified())
                        savedData = autoSave.getLatest(ff);
                }
            }

            if (fullPath.endsWith(".jjsp") || fullPath.endsWith(".jet"))
                addedComponent = addNewTab(new SourcePane(uri, sharedState));
            else if (fullPath.endsWith(".jf"))
                addedComponent = addNewTab(new SourcePane(uri, sharedState));
            else if (fullPath.endsWith(".js"))
                addedComponent = addNewTab(new JavascriptPane(uri, sharedState));
            else if (fullPath.endsWith(".sql"))
                addedComponent = addNewTab(new JDESQLPane(uri, sharedState));
            else if (fullPath.endsWith(".jar") || fullPath.endsWith(".zip"))
                addedComponent = addNewTab(new JARViewer(uri, sharedState));
            else if (fullPath.endsWith(".html") || fullPath.endsWith(".htm") || fullPath.endsWith(".svg"))
                addedComponent = addNewTab(new WebBrowser(isFile, uri));
            else if (useTextEditorFor(uri) || (!isHTTP && isMostlyTextContent(uri)))
                addedComponent = addNewTab(new JDETextEditor(uri, sharedState));
            else if (fullPath.endsWith(".ico"))
                addedComponent = addNewTab(new BinaryDataViewer(uri));
            else 
            {
                String mimeType = HTTPHeaders.guessMIMEType(fullPath);

                if (mimeType.startsWith("image"))
                    addedComponent = addNewTab(new JDEImageEditor(uri));
                else
                {
                    if (isFile)
                    {
                        if (mimeType.startsWith("text"))
                            addedComponent = addNewTab(new JDETextEditor(uri, sharedState));
                        else
                            addedComponent = addNewTab(new BinaryDataViewer(uri));
                    }
                    else
                        addedComponent = addNewTab(new WebBrowser(uri));
                }
            }

            if (!isFile)
            {
                URI root = resourceTree.getRootURI(uri);
                resourceTree.addURI(root);
            }

            resourceTree.addURI(uri);
            if (addedComponent != null)
                addedComponent.loadAutoSavedData(savedData);
            return true;
        }

        protected void moveTabLeft(URITab tab)
        {
            if (this == outputTabs2)
            {
                outputTabs2.removeURI(tab.jdeComponent.getURI());
                outputTabs1.addNewTab(tab.jdeComponent);
            }
        }
        
        protected void moveTabRight(URITab tab)
        {
            if (this == outputTabs1)
            {
                outputTabs1.removeURI(tab.jdeComponent.getURI());
                outputTabs2.addNewTab(tab.jdeComponent);
            }
        }

        protected void markedAsMain(URITab tab)
        {
            outputTabs1.clearMainMarkers();
            outputTabs2.clearMainMarkers();
            super.markedAsMain(tab);
        }
    }

    class UIUpdater implements Runnable
    {
        Stage primaryStage;

        UIUpdater(Stage primaryStage)
        {
            this.primaryStage = primaryStage;

            Thread tt = new Thread(this, "UI Updater");
            tt.setDaemon(true);
            tt.start();
        }

        private void updateUI()
        {
            String title = primaryStage.getTitle();

            String tabTitle1 = outputTabs1.getSelectedTabTitle(); 
            String tabTitle2 = outputTabs2.getSelectedTabTitle(); 

            String desiredTitle = "JDE";
            if (tabTitle1 != null)
                desiredTitle = tabTitle1;
            if (!isSingleViewMode() && (tabTitle2 != null))
                desiredTitle = "Left: "+desiredTitle+"   Right: "+tabTitle2;

            if (!desiredTitle.equals(title))
                primaryStage.setTitle(desiredTitle);
        
            outputTabs1.refreshTabs();
            outputTabs2.refreshTabs();
            resourceTree.rescanTree();
        }

        public void run()
        {
            if (Platform.isFxApplicationThread())
            {
                try
                {
                    updateUI();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                return;
            }

            while (true)
            {
                Platform.runLater(this);

                try
                {
                    Thread.sleep(200);
                }
                catch (Exception e) {}
            }
        }
    }

    class LocalFileServer extends AbstractRequestFilter
    {
        int port;
        HTTPServer server;

        LocalFileServer(int port) throws IOException
        {
            super("JDE Server", null);
            this.port = port;

            server = new HTTPServer(this, menuBar.httpLog);
            server.listenOn(port);
        }
        
        protected boolean handleRequest(HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException
        {
            String path = request.getHeaders().getPath();
            URI[] roots = resourceTree.getURIRoots();

            if (path.equals("/"))
            {
                ArrayList buf = new ArrayList();
                for (int i=0; i<roots.length; i++)
                {
                    URI uri = roots[i];
                    if (!uri.getScheme().equals("file"))
                        continue;
                    buf.add(new File(uri));
                }

                File[] ff = new File[buf.size()];
                buf.toArray(ff);
                
                response.getHeaders().configureAsOK();
                response.getHeaders().setContentType("text/html");
                response.getHeaders().configureToPreventCaching();
                response.sendContent(DirectoryFilter.generateHTMLDirectoryListing("", null, ff));
                
                return true;
            }

            for (int i=0; i<roots.length; i++)
            {
                URI uri = roots[i];
                if (!uri.getScheme().equals("file"))
                    continue;

                try
                {
                    File root = new File(uri);
                    String rootPath = DirectoryFilter.relativePath(null, root);
                    if (!path.startsWith(rootPath))
                        continue;
                    path = path.substring(rootPath.length());
                    if (!path.startsWith("/"))
                        path = "/"+path;

                    File ff = new File(root, path);
                    
                    if (!ff.exists())
                        response.getHeaders().configureAsNotFound();
                    else if (ff.isDirectory())
                    {
                        response.getHeaders().configureAsOK();
                        response.getHeaders().setContentType("text/html");
                        response.getHeaders().configureToPreventCaching();
                        response.sendContent(DirectoryFilter.generateHTMLDirectoryListing("", null, ff.listFiles()));
                    }
                    else if (ff.isFile())
                    {
                        response.getHeaders().configureAsOK();
                        response.getHeaders().guessAndSetContentType(ff.getName(), "text/plain");
                        response.getHeaders().configureToPreventCaching();
                        
                        byte[] buffer = new byte[100*1024];
                        response.prepareToSendContent(ff.length(), false);
                        FileInputStream fin = new FileInputStream(ff);
                        while (true)
                        {
                            int r = fin.read(buffer);
                            if (r < 0)
                                break;
                            response.write(buffer, 0, r);
                        }
                        fin.close();
                        response.close();
                    }
                    else
                        return false;

                    return true;
                }
                catch (Exception e) {}
            }

            return false;
        }
    }

    public boolean isSingleViewMode()
    {
        double pos = outputSplit.getDividerPositions()[0];
        return (pos > 0.9);
    }

    public void clearRightTabs()
    {
        outputSplit.setDividerPosition(0, 1.0);
        URI[] tab2URIs = outputTabs2.removeAll();
        for (int i=0; i<tab2URIs.length; i++)
            outputTabs1.openNewURITab(tab2URIs[i]);
    }
    
    public void moveToLeftPane()
    {
        URITabPane.URITab target = outputTabs2.getSelectedTab();
        if (target == null)
            return;
        outputTabs2.removeURI(target.jdeComponent.getURI());
        outputTabs1.addNewTab(target.jdeComponent);
    }
    
    public void moveToRightPane()
    {
        URITabPane.URITab target = outputTabs1.getSelectedTab();
        if (target == null)
            return;
        outputTabs1.removeURI(target.jdeComponent.getURI());
        outputTabs2.addNewTab(target.jdeComponent);
    }

    public boolean openNewURITab(URI uri)
    {
        if (isSingleViewMode())
            return outputTabs1.openNewURITab(uri);
        
        if (uri.getScheme().equals("http") || uri.getScheme().equals("https"))
            return outputTabs2.openNewURITab(uri);
        else
            return outputTabs1.openNewURITab(uri);
    }

    public boolean openNewTextTab(URI uri)
    {
        return outputTabs1.openNewTextTab(uri);
    }

    public void runMainTab()
    {
        URITabPane tgt = outputTabs1;
        URITabPane.URITab mainTab = outputTabs1.getMainMarkedTab();
        if (mainTab == null)
        {
            mainTab = outputTabs2.getMainMarkedTab();
            tgt = outputTabs2;
        }
        
        if (mainTab == null)
            return;

        tgt.showTab(mainTab.getURI());
        JDEComponent comp = mainTab.jdeComponent;
        if (comp instanceof SourcePane)
            ((SourcePane) comp).compile();
    }    
    
    public void start(final Stage primaryStage) throws Exception
    {
        try
        {
            this.primaryStage = primaryStage;

            File autoSaveFile = new File(jdeCacheDir, "AutoSave.bin");
            autoSave = new AutoSave(autoSaveFile);
            autoSyncEditors = true;
            uiState = new UIStatePersister();

            sharedState = new SharedTextEditorState();
            Args.parse(getParameters().getRaw().toArray(new String[0]));

            File iconCacheDir = new File(jdeCacheDir, "icons");
            iconCacheDir.mkdir();

            iconCache = new ImageIconCache(iconCacheDir);
            resourceTree = new InternalTree(iconCache);
            outputTabs1 = new OutputTabs(iconCache);
            outputTabs1.setMinWidth(800);
            outputTabs2 = new OutputTabs(iconCache);
        
            int basicFilePort = Args.getInt("port", DEFAULT_PORT);
            basicFilePort = Utils.getFreeSocket(null, basicFilePort, basicFilePort+128);
            if (basicFilePort <= 0)
                throw new IOException("No local port available for JDE services");
            menuBar = new JDEMenus(basicFilePort);

            outputSplit = new SplitPane();
            outputSplit.setMinWidth(600);
            outputSplit.setOrientation(Orientation.HORIZONTAL);
            outputSplit.getItems().addAll(outputTabs1, outputTabs2);
            outputSplit.setDividerPosition(0, 1.0);

            SplitPane hSplit = new SplitPane();
            hSplit.setOrientation(Orientation.HORIZONTAL);
            hSplit.getItems().addAll(resourceTree, outputSplit);
            hSplit.setDividerPosition(0, resourceTree.getPrefWidth()/uiState.screenWidth);
            SplitPane.setResizableWithParent(resourceTree, Boolean.FALSE);
            SplitPane.setResizableWithParent(outputTabs2, Boolean.FALSE);

            BorderPane main = new BorderPane();
            main.setCenter(hSplit);
            main.setTop(menuBar);

            Scene scene = new Scene(main, uiState.screenWidth, uiState.screenHeight, Color.WHITE);
            final URL resource = getClass().getResource("/resources/ui-styles.css");
            scene.getStylesheets().add(resource.toExternalForm());

            scene.addEventFilter(KeyEvent.KEY_RELEASED, (keyEvent) -> {if (keyEvent.getCode() == KeyCode.F6) {keyEvent.consume(); runMainTab(); }});
 
            primaryStage.setTitle("JJSP Browser");
            if (iconCache.getJJSPImage() != null)
                primaryStage.getIcons().add(iconCache.getJJSPImage());
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(700);
            primaryStage.setMinHeight(400);
            primaryStage.setX(uiState.screenX);
            primaryStage.setY(uiState.screenY);

            Platform.setImplicitExit(false);
            primaryStage.setOnCloseRequest((evt) -> stop());

            primaryStage.show();

            uiUpdater = new UIUpdater(primaryStage);
            localServer = new LocalFileServer(basicFilePort);

            String[] rawOps = getParameters().getRaw().toArray(new String[0]);
            String jfFileName = Args.getFirstArg(new String[]{"source", "jf", "src"}, null);
            if ((jfFileName == null) && (rawOps.length > 0))
                jfFileName = rawOps[0];

            resourceTree.addURI(new File(System.getProperty("user.dir")).toURI());
            if (jfFileName != null)
                resourceTree.addURI(new File(jfFileName).toURI());

            URI[] leftURIs = uiState.getOuput1URIs();
            for (int i=0; i<leftURIs.length; i++)
                outputTabs1.openNewURITab(leftURIs[i]);

            URI[] rightURIs = uiState.getOuput2URIs();
            for (int i=0; i<rightURIs.length; i++)
                outputTabs2.openNewURITab(rightURIs[i]);
            
            outputSplit.setDividerPosition(0,(float) uiState.splitPosition);

            uiState.startAutoPersisting();
        }
        catch (Throwable e)
        {
            e.printStackTrace();
            throw new Exception("Exception in FX Start", e);
        }
    }

    public void stop()
    {
        try
        {
            autoSave.flushToDisk();
        }
        catch (Throwable t) {} 
        
        try
        {
            uiState.persistUIData(false);
        }
        catch (Exception e) {}

        Platform.exit();
        System.exit(0);
    }

    public static void launchExternalBrowser(URI targetURI)
    {
        new Thread(() -> {try { Thread.sleep(500); java.awt.Desktop.getDesktop().browse(targetURI); } catch (Throwable e) {}}).start();
    }

    public static Environment getEnvironment()
    {
        return jjspEnv;
    }

    public static void main(String[] args) throws Exception
    { 
        jjspEnv = new Environment(Args.parse(args));
        File cacheDir = jjspEnv.getLocalCacheDir();
        jdeCacheDir = new File(cacheDir, ".jde");
        jdeCacheDir.mkdirs();

        launch(args);
    }
}
