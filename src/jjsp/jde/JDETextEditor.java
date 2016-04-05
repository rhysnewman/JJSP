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

import jjsp.util.*;
import jjsp.engine.*;

public class JDETextEditor extends JDEComponent
{
    public static final int EDITOR_TEXT_SIZE = 18;

    protected TextEditor editor;
    protected BorderPane searchBox;
    protected SearchTools searchTools;
    protected FileChooser fileChooser;
    
    public JDETextEditor(URI uri, SharedTextEditorState sharedState)
    {
        this(uri, sharedState, true);
    }

    public JDETextEditor(URI uri, SharedTextEditorState sharedState, boolean allowReplaceOnSearch)
    {
        super(uri);
        setMinWidth(150);
        searchTools = new SearchTools(allowReplaceOnSearch);
        addEventFilter(KeyEvent.KEY_PRESSED, (event) -> { if (event.getCode() == KeyCode.ESCAPE) { event.consume(); hideSearchBox(); }} );
        init(sharedState);
    }
    
    protected void init(SharedTextEditorState sharedState)
    {
        editor = new TextEditor(EDITOR_TEXT_SIZE);
        editor.setSharedTextEditorState(sharedState);
        searchBox = new BorderPane();
        setCenter(editor);
        setTop(searchBox);
        loadFromURI();
    }

    class SearchTools extends ToolBar
    {
        TextField search, replace;
        
        SearchTools()
        {
            this(true);
        }

        SearchTools(boolean allowReplace)
        {
            String style = "-fx-font-size: 18px";
            setMinWidth(150);

            Button closeSearch = new Button("Close");
            closeSearch.setStyle(style);
            closeSearch.setMinWidth(90);
            closeSearch.setOnAction((event) -> hideSearchBox() );

            search = new TextField();
            HBox.setHgrow(search, Priority.ALWAYS);
            search.setStyle(style);
            search.setMinWidth(300);
            search.setMaxWidth(Integer.MAX_VALUE);
            search.setPromptText("Search text for...");
            search.setOnAction((event) -> {editor.searchForText(search.getText(), true);});
            search.setOnKeyPressed((event) -> 
                                 {
                                     KeyCode code = event.getCode();
                                     if ((KeyCode.KP_DOWN == code) || (KeyCode.DOWN == code))
                                         editor.requestFocus();
                                 });

            replace = new TextField();
            replace.setStyle(style);
            replace.setMinWidth(100);
            replace.setPromptText("Replacement text");
            replace.setOnKeyPressed((event) -> 
                                 {
                                     KeyCode code = event.getCode();
                                     if ((KeyCode.KP_DOWN == code) || (KeyCode.DOWN == code))
                                         editor.requestFocus();
                                 });

            Button findNext = new Button("Next >>");
            findNext.setStyle(style);
            findNext.setMinWidth(140);
            findNext.setOnAction((event) -> {editor.searchForText(search.getText(), true);});
            Button findPrevious = new Button("<< Previous");
            findPrevious.setStyle(style);
            findPrevious.setMinWidth(140);
            findPrevious.setOnAction((event) -> {editor.searchForText(search.getText(), false);});

            Button replaceOnce = new Button("Replace");
            replaceOnce.setStyle(style);
            replaceOnce.setMinWidth(140);
            replaceOnce.setOnAction((event) -> {editor.replaceSelectedText(replace.getText());});

            Button replaceAndSearch = new Button("Replace & Find");
            replaceAndSearch.setStyle(style);
            replaceAndSearch.setMinWidth(140);
            replaceAndSearch.setOnAction((event) -> 
                                         {
                                             String replaceWith = replace.getText();
                                             int selectionStart = editor.getSelectionStart();
                                             if (selectionStart < 0)
                                                 return;
                                             editor.replaceSelectedText(replaceWith); 
                                             editor.searchForText(search.getText(), selectionStart+replaceWith.length(), true);
                                         });

            Button replaceAll = new Button("Replace All");
            replaceAll.setStyle(style);
            replaceAll.setMinWidth(140);
            replaceAll.setOnAction((event) -> 
                                   {
                                       String searchFor = search.getText();
                                       String replaceWith = replace.getText();
                                       int originalSelection = editor.getSelectionStart();

                                       if (originalSelection > 0)
                                       {
                                           editor.replaceSelectedText(replaceWith); 
                                           if (!editor.searchForText(searchFor, originalSelection+replaceWith.length(), true))
                                               return;
                                       }

                                       int nextSelection = editor.getSelectionStart();
                                       while (true) 
                                       {
                                           int selectionStart = editor.getSelectionStart();
                                           if (selectionStart < 0)
                                               break;
                                           if ((selectionStart >= originalSelection) && (selectionStart < nextSelection))
                                               break; // Designed to stop looping forever around a document when the replacement string includes the search string
                                           editor.replaceSelectedText(replaceWith); 
                                           if (!editor.searchForText(searchFor, selectionStart+replaceWith.length(), true))
                                               break;
                                       }
                                   });

            Label l1 = new Label("Search:");
            l1.setStyle(style);
            l1.setMinWidth(70);
            Label l2 = new Label("Replace:");
            l2.setStyle(style);
            l2.setMinWidth(70);
            
            Region r = new Region();
            HBox.setHgrow(r, Priority.ALWAYS);
            r.setMinWidth(20);

            if (allowReplace)
                getItems().addAll(l1, search, findPrevious, findNext, r, l2, replace, replaceOnce, replaceAndSearch, replaceAll, closeSearch);
            else
                getItems().addAll(l1, search, findPrevious, findNext, r, closeSearch);
        }
        
        void searchShown()
        {
            Platform.runLater(() -> search.requestFocus());
            new Thread(() -> {try{Thread.sleep(200); Platform.runLater(() -> search.requestFocus());}catch (Exception e){}}).start();
        }
    }

    protected void showSearchBox()
    {
        if (searchBox.getTop() == null)
            searchBox.setTop(searchTools);
        searchTools.searchShown();
    }

    protected void hideSearchBox()
    {
        searchBox.setTop(null);
        editor.requestFocus();
    }

    public Menu[] createMenus()
    {
        MenuItem save = new MenuItem("Save");
        save.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        save.setOnAction((event) -> 
                         {
                             if (!saveAs(getURI())) 
                             {
                                 File file = getFileChooser().showSaveDialog(getScene().getWindow());
                                 if (file != null)
                                     saveAs(file.toURI());
                             }
                         });

        MenuItem saveAs = new MenuItem("Save As");
        saveAs.setOnAction((event) -> 
                           { 
                               File file = getFileChooser().showSaveDialog(getScene().getWindow());
                               if (file != null)
                                   saveAs(file.toURI());
                           });

        MenuItem showSearch = new MenuItem("Show Search Box");
        showSearch.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN));
        showSearch.setOnAction((evt)-> showSearchBox());
        
        MenuItem load = new MenuItem("Load"); 
        load.setOnAction((event) -> 
                         {  
                             File ff = getFileChooser().showOpenDialog(getScene().getWindow()); 
                             if (ff != null) 
                                 load(ff.toURI()); 
                         });

        MenuItem revert = new MenuItem("Revert to Last Saved"); 
        revert.setOnAction((event) -> 
                           {
                               load(getURI()); 
                               saveAs(getURI());
                           });
        revert.setAccelerator(new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN));

        MenuItem set10ptFont = new MenuItem("10 px");
        set10ptFont.setOnAction((event) -> { editor.setFontSize(10, 6); });
        MenuItem set12ptFont = new MenuItem("12 px");
        set12ptFont.setOnAction((event) -> { editor.setFontSize(12, 7); });
        MenuItem set14ptFont = new MenuItem("14 px");
        set14ptFont.setOnAction((event) -> { editor.setFontSize(14, 9); });
        MenuItem set18ptFont = new MenuItem("18 px");
        set18ptFont.setOnAction((event) -> { editor.setFontSize(18); });
        MenuItem set24ptFont = new MenuItem("24 px");
        set24ptFont.setOnAction((event) -> { editor.setFontSize(24); });
        MenuItem set28ptFont = new MenuItem("28 px");
        set28ptFont.setOnAction((event) -> { editor.setFontSize(28); });
        MenuItem set32ptFont = new MenuItem("32 px");
        set32ptFont.setOnAction((event) -> { editor.setFontSize(32); });
        MenuItem set36ptFont = new MenuItem("36 px");
        set36ptFont.setOnAction((event) -> { editor.setFontSize(36); });
        
        Menu fontSize = new Menu("Font Size");
        fontSize.getItems().addAll(set10ptFont, set12ptFont, set14ptFont, set18ptFont, set24ptFont, set28ptFont, set32ptFont, set36ptFont);

        Menu actions = new Menu("Editor Actions");
        actions.getItems().addAll(showSearch, new SeparatorMenuItem(), load, revert, new SeparatorMenuItem(), save, saveAs, new SeparatorMenuItem(), fontSize);

        return new Menu[]{actions};
    }

    protected FileChooser initFileChooser()
    {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().addAll(
                                                 new FileChooser.ExtensionFilter("Text Files", "*.txt"), 
                                                 new FileChooser.ExtensionFilter("Java Source", "*.java"), 
                                                 new FileChooser.ExtensionFilter("Javascript", "*.js"), 
                                                 new FileChooser.ExtensionFilter("Cascading Style Sheet", "*.css"), 
                                                 new FileChooser.ExtensionFilter("HTML", "*.html", "*.htm"), 
                                                 new FileChooser.ExtensionFilter("Comma Separated Values", "*.csv"), 
                                                 new FileChooser.ExtensionFilter("Log Files", "*.log")
                                                 );
        return fileChooser;
    }

    protected FileChooser getFileChooser()
    {
        String fileName = "";
        File dir = new File(System.getProperty("user.dir"));

        try
        {
            File file = new File(getURI());
            if (!file.isDirectory())
            {
                dir = file.getParentFile();
                fileName = file.getName();
            }
        }
        catch (Exception e) {}

        if (fileChooser == null)
            fileChooser = initFileChooser();
        fileChooser.setInitialDirectory(dir);
        fileChooser.setInitialFileName(fileName);
        fileChooser.setTitle(getURI().toString());

        return fileChooser;
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
            String text = loadTextFromURI(src);
            componentURI = src;

            editor.setEditable(true);
            editor.setText(text);
            editor.markEdited(false);
            lastModified = getLastModifiedFromURI();
            editor.discardEditHistory(); // Ensure you can't empty the document with an initial undo!
            return true;
        }
        catch (Exception e)
        {
            setStatus("Error Loading "+src, e);
            return false;
        }
    }

    public boolean saveAs(URI target)
    {
        if (target == null)
            return false;
            
        try
        {
            byte[] rawBytes = Utils.getAsciiBytes(editor.getText());
            
            if (target.getScheme().equals("file"))
            {
                OutputStream out = new FileOutputStream(new File(target));
                out.write(rawBytes);
                out.close();
            }
            else
            {
                HttpURLConnection conn = (HttpURLConnection) target.toURL().openConnection();
                conn.setDoOutput(true);

                OutputStream out = conn.getOutputStream();
                out.write(rawBytes);
                out.flush();
                out.close();
                
                if (conn.getResponseCode() != 200)
                    throw new IOException(conn.getResponseCode()+": "+conn.getResponseMessage());
            }
                
            componentURI = target;
            lastModified = getLastModifiedFromURI();
            editor.markEdited(false);
            editor.setEditable(true);
            // Sara doesn't like this: editor.discardEditHistory();
            return true;
        }
        catch (Exception e)
        {
            setStatus("Error Saving "+target, e);
            return false;
        }
    }

    public void requestFocus()
    {
        editor.requestFocus();
    }

    public void setTextColour(Color c)
    {
        editor.setColours(c);
    }

    public void reloadContent()
    {
        if (editor.isEditable() && editor.hasBeenEdited())
            return;
        loadFromURI();
        super.reloadContent();
    }

    public void setDisplayed(boolean isShowing) 
    {
        super.setDisplayed(isShowing);
        if (editor != null)
            editor.setIsShowing(isShowing);
    }
    
    public String getShortTitle()
    {
        String title = super.getShortTitle();
        if (editor.hasBeenEdited())
            return title+" **";
        return title;
    }

    public String getFullTitle()
    {
        String title = super.getFullTitle();
        if (editor.hasBeenEdited())
            return title+" **";
        return title;
    }

    public byte[] getDataForAutoSave()
    {
        if (editor.hasBeenEdited())
            return Utils.getAsciiBytes(editor.getText());
        return null;
    }

    public void loadAutoSavedData(byte[] data)
    {
        super.loadAutoSavedData(data);
        if (data == null)
            return;

        double currentScrollPos = editor.getScrollBarPosition();
        editor.setText(Utils.toString(data));
        editor.markEdited(true);
        editor.setScrollBarPosition(currentScrollPos);
    }

    public static String loadTextFromURI(URI uri) throws Exception
    {
        return Utils.toString(Utils.load(uri.toURL()));
    }

    public static String toString(Throwable err)
    {
        return JJSPRuntime.toString(err);
    }
}
