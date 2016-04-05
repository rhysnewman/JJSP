
package jjsp.jde;

import java.net.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

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
import javafx.scene.web.*;
import javafx.geometry.*;
import javafx.animation.*;
import javafx.util.*;
import javafx.stage.*;
import javafx.collections.*;
import javafx.concurrent.Worker.*;

import netscape.javascript.*;

import jjsp.util.*;

public class TextEditor extends BorderPane 
{
    public static final double SCROLL_GAIN = 2.4;
    public static final long SCROLL_RATE_PAUSE = 100;
    public static final String[] AUTO_CLOSING_INDENTS = {"}"};

    private EditorCanvas editor;
    private Font monospacedFont;
    private EditorScrollBar scrollBar;
    private boolean hasBeedEdited, editable;
    private SharedTextEditorState sharedState;
    private int lineHeight, charWidth, fontBaseline, lineNumberMargin, scrollBarWidth;
    private Color textColour, selectionColour, caretColour, lineNumberColour, lineNumberMarginColour;
    
    private volatile boolean disposed, isShowing;

    public TextEditor(int fontSize)
    {
        this(fontSize, "");
    }

    public TextEditor(int fontSize, String initialText)
    {
        editable = true;
        disposed = false;
        hasBeedEdited = false;
        isShowing = true;

        lineNumberMarginColour = Color.rgb(220, 220, 255);
        lineNumberColour = Color.rgb(50, 50, 50);
        textColour = Color.BLUE;
        caretColour = Color.RED;
        selectionColour = Color.rgb(230, 200, 255);

        setCursor(Cursor.TEXT);
            
        scrollBar = new EditorScrollBar(fontSize);
        setRight(scrollBar);
        BorderPane.setAlignment(scrollBar, Pos.TOP_RIGHT);

        editor = new EditorCanvas();
        setCenter(editor);
        BorderPane.setAlignment(editor, Pos.TOP_LEFT);

        setMinSize(100, 100);
        setStyle("-fx-background-color:#FFF; -fx-font-smoothing-type: lcd;");
        setText(initialText);
        setFontSize(fontSize);

        sharedState = new SharedTextEditorState();

        layoutBoundsProperty().addListener((obs, oldVal, newVal) -> 
                                           {
                                               BoundingBox vp = (BoundingBox) newVal;
            
                                               editor.setWidth(vp.getWidth() - scrollBarWidth);
                                               editor.setHeight(vp.getHeight());
                                               editor.caret.showing = true;
                                               
                                               editor.doLayout();
                                               scrollBar.configureBar();
                                               editor.drawText();
                                           });
    }

    //TODO : decice which one of these methods is the best name!
    public void setDisplayed(boolean value)
    {
        setIsShowing(value);
    }

    public void setIsShowing(boolean value)
    {
        isShowing = value;
    }

    public void setFontSize(int fontSize)
    {
        setFontSize(fontSize, Math.max(fontSize - 18, 0) + 10);
    }
    
    public void setFontSize(int fontSize, int width)
    {
        lineHeight = fontSize+2;
        fontBaseline = fontSize-2;
        charWidth = width;
        scrollBarWidth = Math.max(fontSize, 18);

        lineNumberMargin = 2*charWidth + 10; // start with enough for 99 lines
        monospacedFont = Font.font("monospaced", FontWeight.NORMAL, fontSize);

        BoundingBox vp = (BoundingBox) getLayoutBounds();
        editor.setWidth(vp.getWidth() - scrollBarWidth);
        editor.setHeight(vp.getHeight());

        editor.doLayout();
        scrollBar.correlateWidthToFontsize(scrollBarWidth);
        editor.drawText();
    }

    public void setSharedTextEditorState(SharedTextEditorState state)
    {
        this.sharedState = state;
    }

    public void dispose()
    {
        disposed = true;
    }

    // pass new colours in order: textColour,  selectionColour,  caretColour,  lineNumberColour,  lineNumberMarginColour
    public void setColours(Color... colours)
    {
        try
        {
            this.textColour = colours[0];
            this.selectionColour = colours[1];
            this.caretColour = colours[2];
            this.lineNumberColour = colours[3];
            this.lineNumberMarginColour = colours[4];
        }
        catch (Exception e) {}
        refreshView();
    }

    public void markEdited(boolean value)
    {
        hasBeedEdited = value;
    }

    public boolean hasBeenEdited()
    {
        return hasBeedEdited;
    }

    public boolean isEditable()
    {
        return editable;
    }
    
    public void setEditable(boolean value)
    {
        editable = value;
    }

    public void setText(String text)
    {
        if (text == null)
            text = "";
        editor.setText(text);
        clearSelection();
        markEdited(false);
    }

    public String getText()
    {
        return editor.content.toString();
    }

    public int getTextLength()
    {
        return editor.content.length();
    }

    public int getCaretPosition()
    {
        return editor.getCaretPosition();
    }

    public int getCurrentLine()
    {
        return editor.getCurrentLine();
    }

    public void setCaretPosition(int pos)
    {
        editor.setCaretPosition(pos);
    }

    public double getScrollBarPosition()
    {
        return scrollBar.getValue();
    }

    public void setScrollBarPosition(double pos)
    {
        scrollBar.setValue(pos);
    }

    public void scrollToLine(int lineNumber)
    {
        editor.scrollToLine(lineNumber);
    }

    public int lineInViewport(int line)
    {
        return editor.lineInViewport(line);
    }

    public void setSelectedText(int start, int end)
    {
        editor.setSelectedText(start, end);
    }

    public boolean replaceSelectedText(String newText)
    {
        return editor.replaceSelectedText(newText);
    }

    public int getSelectionStart()
    {
        return editor.getSelectionStart();
    }

    public String getSelectedText()
    {
        return editor.getSelectedText();
    }

    public int getSelectionLineStart()
    {
        return editor.getHighlightLineStart();
    }

    public int getSelectionLineEnd()
    {
        return editor.getHighlightLineEnd();
    }

    public void clearSelection()
    {
        editor.clearSelection();
    }

    public void refreshView()
    {
        editor.drawText();
    }

    public void requestFocus()
    {
        editor.requestFocus();
        refreshView();
    }

    public void highlightLine(int lineNumber)
    {
        highlightLines(lineNumber, lineNumber);
    }
    
    protected void textSelected(String selection)
    {
    }

    public void highlightLines(int startLine, int endLine)
    {
        editor.selectLines(startLine, endLine);
    }

    public int getHighlightLineStart()
    {
        return editor.getHighlightLineStart();
    }

    public int getHighlightLineEnd()
    {
        return editor.getHighlightLineEnd();
    }

    public boolean searchForText(String searchFor, boolean forwards)
    {
        return searchForText(searchFor, getCaretPosition(), forwards);
    }

    public boolean searchForText(String searchFor, int start, boolean forwards)
    {
        return editor.searchForText(searchFor, start, forwards);
    }

    public void discardEditHistory()
    {
        editor.discardEditHistory();
    }

    class EditorScrollBar extends ScrollBar
    {
        EditorScrollBar(int size)
        {
            // Assume default values 0..100
            setOrientation(Orientation.VERTICAL);
            setVisibleAmount(0);

            setCursor(Cursor.HAND);
            correlateWidthToFontsize(size);
            valueProperty().addListener((obs, old, val) -> {configureBar(); editor.drawText();} );
        }

        void correlateWidthToFontsize(int size)
        {
            setMinWidth(size);
            setMaxWidth(size);
            setPrefWidth(size);
            setStyle("-fx-font-size: "+size+"px;"); //Sets the increment and decrement button sizes  

            scrollBarWidth = size;
            setUnitIncrement(lineHeight);
            try
            {
                configureBar();
            }
            catch (Exception e) {}
        }

        void configureBar()
        {
            double vpHeight = editor.getHeight();
            double textHeight = editor.textHeight;

            if (textHeight <= vpHeight)
            {
                setVisibleAmount(100);
                setValue(0);
            }
            else
            {
                double scrollBarHeight = 100*Math.min(1, Math.max(0, vpHeight / Math.max(1, textHeight)));
                setVisibleAmount(scrollBarHeight);
                double blockIncrement = 100*Math.min(1, Math.max(0, (vpHeight - 2*lineHeight) / Math.max(1, textHeight)));
                setBlockIncrement(blockIncrement);
            }  
            
            textScrolled(getValue());
        }
    }
    
    class EditorCanvas extends Canvas implements EventHandler
    {
        int dragStartPos;
        StringBuffer content;
        int editHistoryPosition;
        ArrayList editHistory;
        CaretAnimator caret;
        LineOfText[] lines;
        double textHeight;

        EditorCanvas()
        {
            super(100, 100);
            setStyle("-fx-font-smoothing-type: lcd;");

            textHeight = 0;
            dragStartPos = -1;
            editHistoryPosition = 0;
            editHistory = new ArrayList();
            content = new StringBuffer();
            caret = new CaretAnimator();
            
            addEventHandler(KeyEvent.KEY_PRESSED, this);
            addEventHandler(KeyEvent.KEY_TYPED, this);

            addEventHandler(MouseEvent.MOUSE_PRESSED, this);
            addEventHandler(MouseEvent.MOUSE_RELEASED, this);
            addEventHandler(MouseEvent.MOUSE_DRAGGED, this);

            addEventHandler(ScrollEvent.SCROLL, this);
            setFocusTraversable(true);
            
            doLayout();
        }

        public void discardEditHistory()
        {
            editHistory = new ArrayList();
            editHistoryPosition = 0;
            saveContentToHistory();
        }

        public void saveContentToHistory()
        {
            while (editHistoryPosition < editHistory.size())
                editHistory.remove(editHistory.size()-1);

            editHistory.add(content.toString());
            editHistory.add(Integer.valueOf(caret.position));
            editHistoryPosition += 2;

            if (editHistory.size() > 1024)
            {
                editHistory.remove(0);
                editHistory.remove(0);
                editHistoryPosition -= 2;
            }
        }
        
        public boolean redoLastUndo()
        {
            if (editHistoryPosition >= editHistory.size())
                return false;

            editHistoryPosition += 2;
            int redoPos = ((Integer) editHistory.get(editHistoryPosition-1)).intValue();
            String redoText = (String) editHistory.get(editHistoryPosition-2);

            content = new StringBuffer(redoText);
            caret.position = redoPos;
            caret.showing = true;

            clearSelection();
            doLayout();
            drawText();
            scrollBar.configureBar();
            scrollToLine(getLineForPosition(caret.position));

            markEdited(true);
            return true;
        }

        public boolean undoLastEdit()
        {
            if (editHistoryPosition <= 2)
                return false;
            if (editHistoryPosition >= 4)
                editHistoryPosition -= 2;

            int previousPos = ((Integer) editHistory.get(editHistoryPosition-1)).intValue();
            String previousText = (String) editHistory.get(editHistoryPosition-2);

            content = new StringBuffer(previousText);
            caret.position = previousPos;
            caret.showing = true;

            clearSelection();
            doLayout();
            drawText();
            scrollBar.configureBar();
            scrollToLine(getLineForPosition(caret.position));

            markEdited(editHistoryPosition > 0);
            return true;
        }

        public int getHighlightLineStart()
        {
            return getLineForPosition(caret.selectionStart);
        }
        
        public int getHighlightLineEnd()
        {
            return getLineForPosition(caret.selectionEnd);
        }

        public int getLineForPosition(int pos)
        {
            return caret.getLineForPosition(pos);
        }

        public int getCurrentLine()
        {
            return caret.getCurrentLineIndex();
        }

        public String getSelectedText()
        {
            return caret.getSelectedText();
        }

        public int getSelectionStart()
        {
            return caret.getSelectionStart();
        }

        public int setSelectedText(int start, int end)
        {
            caret.setSelectedRegion(start, end);
            int line = getLineForPosition(start);
            if (lineInViewport(line) != 0)
                scrollToLine(line);
            return end;
        }

        public void clearSelection()
        {
            caret.clearSelection();
        }

        public void selectLines(int start, int end)
        {
            caret.selectLines(start, end);
        }

        public double getViewportStart()
        {
            return scrollBar.getValue()*Math.max(0, textHeight - getHeight())/100.0;
        }

        public double getViewportEnd()
        {
            return  getViewportStart() + Math.max(lineHeight, getHeight() - lineHeight);
        }

        public int lineInViewport(int line)
        {
            if (line < 0)
                return -1;
            if ((line >= lines.length))
                return 1;

            if (lines[line].yPosition < getViewportStart())
                return -1;
            if (lines[line].yPosition + lines[line].yHeight > getViewportEnd())
                return 1;
            
            return 0;
        }

        public int getFirstLineInViewport()
        {
            for (int i=0; i<lines.length; i++)
                if (lineInViewport(i) == 0)
                    return i;
            return 0;
        }

        public int getLastLineInViewport()
        {
            for (int i=lines.length-1; i>=0; i--)
                if (lineInViewport(i) == 0)
                    return i;
            return lines.length-1;
        }

        public void scrollLines(int lineDelta)
        {
            double val = scrollBar.getValue() + lineHeight*lineDelta*100/Math.max(getHeight(), textHeight - getHeight());
            val = Math.max(Math.min(100, val), 0);
            scrollBar.setValue(val);
            drawText();
        }

        public void scrollToLine(int line)
        {
            scrollToLine(line, true);
        }

        public void scrollToLine(int line, boolean onlyIfOffScreen)
        {
            line = Math.min(lines.length-1, Math.max(0, line));

            if (onlyIfOffScreen)
            {
                int first = getFirstLineInViewport();
                int last = getLastLineInViewport();
                if ((line >= first) && (line <=last))
                    return;
            }

            double val = 100*lines[line].yPosition/Math.max(getHeight(), textHeight - getHeight());
            val = Math.max(Math.min(100, val), 0);
            scrollBar.setValue(val);
            drawText();
        }

        public int getCaretPosition()
        {
            return caret.position;
        }

        public void setCaretPosition(int pos)
        {
            caret.setCaretPosition(pos);
        }

        public void requestFocus()
        {
            caret.showing = true;
            super.requestFocus();
        }

        public int getLineEndPosition(int lineNumber)
        {
            int start = getLineStartPosition(lineNumber);
            String content = editor.content.toString();

            for (int i=start; i<content.length(); i++)
            {
                if (content.charAt(i) == '\n')
                    return i+1;
            }

            return content.length();
        }

        public int getLineStartPosition(int lineNumber)
        {
            if (lineNumber == 0)
                return 0;

            int line = 0, lineStartPos=0;
            String content = editor.content.toString();
            
            for (int i=0; i<content.length(); i++)
            {
                if (content.charAt(i) == '\n')
                    line++;
                if (line == lineNumber)
                    return i+1;
            }
            
            return content.length();
        }

        public boolean searchForText(String searchFor, int start, boolean forwards)
        {
            if (searchFor == null)
                return false;

            searchFor = searchFor.toLowerCase().trim();
            if (searchFor.length() == 0)
                return false;

            String lowerCaseContent = content.toString().toLowerCase();
            int pos = getCaretPosition();
            int nextIndex = -1;
            
            if (forwards)
            {
                nextIndex = lowerCaseContent.indexOf(searchFor, Math.max(pos, 0));
                if (nextIndex < 0)
                    nextIndex = lowerCaseContent.indexOf(searchFor);
            }
            else
            {
                if (pos >= searchFor.length())
                    nextIndex = lowerCaseContent.lastIndexOf(searchFor, Math.max(pos-searchFor.length()-1, 0));
                if (nextIndex < 0)
                    nextIndex = lowerCaseContent.lastIndexOf(searchFor, content.length());
            }

            if (nextIndex >= 0)
            {
                int newPos = setSelectedText(nextIndex, nextIndex + searchFor.length());
                setCaretPosition(newPos);
            }

            return nextIndex >= 0;
        }

        public boolean replaceSelectedText(String newText)
        {
            if ((caret.selectionStart < 0) || (caret.selectionEnd < 0))
                return false;
            if (!editable)
                return false;
       
            caret.alterContent(caret.selectionStart, caret.selectionEnd, caret.selectionStart, newText, caret.selectionStart + newText.length());
            caret.selectionEnd = caret.position;

            doLayout();
            drawText();
            contentChanged();
            return true;
        }
        
        public void handle(Event event)
        {
            caret.showing = true;

            if (event instanceof MouseEvent)     
            {       
                event.consume();
                MouseEvent evt = (MouseEvent) event;
                double mx = evt.getX();
                double my = evt.getY();

                if (evt.getEventType() == MouseEvent.MOUSE_PRESSED)
                {
                    requestFocus();
                    if (evt.isSecondaryButtonDown())
                        return;

                    if (evt.isMiddleButtonDown())
                    {
                        if (!editable)
                            return;
                        int insertLen = sharedState.lastSelectedText.length();
                        if (insertLen == 0)
                            return;
                        
                        int position = caret.findTextPositionFromScreenPoint(mx, my);
                        position = Math.min(Math.max(0, position), content.length()-1);
                        caret.alterContent(-1, -1, position, sharedState.lastSelectedText, position);

                        if (position < caret.position)
                            setCaretPosition(caret.position + insertLen);
                        if ((caret.selectionStart >= 0) && (position < caret.selectionStart))
                            setSelectedText(caret.selectionStart + insertLen, caret.selectionEnd + insertLen);
                                              
                        doLayout();
                    }
                    else
                    {
                        if (!evt.isShiftDown())
                            caret.clearSelection();
                        else if (caret.selectionStart < 0)
                            dragStartPos = caret.selectionStart = caret.selectionEnd = caret.position;

                        caret.locateCaret(mx, my);

                        if (evt.isShiftDown())
                        {
                            if (caret.selectionStart < caret.position)
                                caret.setSelectedRegion(caret.selectionStart, caret.position);
                            else
                                caret.setSelectedRegion(caret.position, caret.selectionEnd, caret.position);
                        }
                        else if (evt.getClickCount() == 2)
                            caret.highlightWordAt(mx, my);
                        else if (evt.getClickCount() > 2)
                        {
                            int line = caret.getCurrentLineIndex();
                            caret.selectLines(line, line);
                        }
                    }
                }
                else if (evt.getEventType() == MouseEvent.MOUSE_DRAGGED)
                {
                    caret.locateCaret(mx, my);
                    if (dragStartPos < 0)
                        dragStartPos = caret.position;
                    else
                    {
                        if (dragStartPos < caret.position)
                            caret.setSelectedRegion(dragStartPos, caret.position, caret.position);
                        else
                            caret.setSelectedRegion(caret.position, dragStartPos, caret.position);
                    }

                    if (my < 0)
                        caret.autoScrollRate = (int) (SCROLL_GAIN * (-1 - Math.abs(my / lineHeight)));
                    else if (my >= getHeight())
                        caret.autoScrollRate = (int) (SCROLL_GAIN * (1 + Math.abs((my - getHeight())/lineHeight)));
                    else
                        caret.autoScrollRate = 0;
                }
                else
                {
                    caret.autoScrollRate = 0;
                    dragStartPos = -1;
                }
            }
            else if (event instanceof KeyEvent)
            {
                KeyEvent evt = (KeyEvent) event;
                if (caret.handleKeyEvent(evt))
                    event.consume();
            }   
            else if (event instanceof ScrollEvent)
            {
                ScrollEvent evt = (ScrollEvent) event;
                double val = -100*(5*evt.getDeltaY()/40)*lineHeight/textHeight;
                val = Math.max(Math.min(100, scrollBar.getValue() + val), 0);
                scrollBar.setValue(val);
                event.consume();
            }
            
            drawText();
        }

        class CaretAnimator implements Runnable
        {
            int position, savedColumn;
            double yPosition, xPosition;
            boolean showing, controlDown, wasEdited;
            int selectionStart, selectionEnd, autoScrollRate, blinkCountdown;

            CaretAnimator()
            {
                showing = true;
                controlDown = false;
                wasEdited = false;
                savedColumn = -1;
                selectionStart = selectionEnd = -1;
                position = 0;
                yPosition = 0;
                xPosition = 0;
                autoScrollRate = 0;

                Thread t = new Thread(() -> 
                                      {
                                          while (!disposed)
                                          {
                                              try
                                              {
                                                  Thread.sleep(SCROLL_RATE_PAUSE);
                                              }
                                              catch (Exception e) {}
                                              
                                              if (isShowing)
                                                  Platform.runLater(CaretAnimator.this);
                                          }
                                      });
                
                t.setDaemon(true);
                t.start();
            }
            
            public void run()
            {
                if (!isShowing || (getScene() == null))
                    return;

                if (blinkCountdown <= 0)
                {
                    drawText();
                    showing = !showing;
                    blinkCountdown = 7;
                }
                else
                    blinkCountdown--;

                if (autoScrollRate != 0)
                {
                    scrollLines(autoScrollRate);
                    try
                    {
                        if (autoScrollRate > 0)
                            caret.selectionEnd = lines[getLastLineInViewport()].end;
                        else
                            caret.selectionStart = lines[getFirstLineInViewport()].start;
                    }
                    catch (Exception e) {}
                }
            }

            void setLastSelection(String selection)
            {
                sharedState.lastSelectedText = selection;
            }

            public int getSelectionStart()
            {
                return selectionStart;
            }
            
            public int getCurrentLineIndex()
            {
                return getLineForPosition(position);
            }

            public int getLineForPosition(int pos)
            {
                if (pos < 0)
                    return -1;

                for (int i=0; i<lines.length; i++)
                {
                    int s = lines[i].start;
                    int e = lines[i].end;
                    if ((pos < s) || (pos > e))
                        continue;
                    return i;
                }
                
                if ((pos < 0) || (lines.length == 0))
                    return -1;
                return lines.length-1;
            }

            private String getMatchingLinePrefix(int lineOffset)
            {
                int line = getCurrentLineIndex();
                if (line < lineOffset)
                    return "";
                
                LineOfText refLine = lines[line-lineOffset];
                for (int l=line-lineOffset; l>=0; l--)
                {
                    if (lines[l].isAllWhitespace())
                        continue;
                    refLine = lines[l];
                    break;
                }

                StringBuffer sp = new StringBuffer();
                if (content.length() > 0)
                {
                    for (int i=refLine.start; i<refLine.end; i++)
                        if (Character.isWhitespace(content.charAt(i)))
                            sp.append(" ");
                        else
                            break;
                }

                return sp.toString();
            }
            
            private int getWhitespaceIndentPosition()
            {
                int line = getCurrentLineIndex();
                int end = Math.min(content.length(), lines[line].end);
                for (int i=lines[line].start; i<end; i++)
                    if (!Character.isWhitespace(content.charAt(i)))
                        return i;
                return end;
            }

            boolean alterContent(int removeStart, int removeEnd, int insertAt, String toInsert, int finalPosition)
            {
                return alterContent(removeStart, removeEnd, insertAt, toInsert, finalPosition, true);
            }

            boolean alterContent(int removeStart, int removeEnd, int insertAt, String toInsert, int finalPosition, boolean clearSelection)
            {
                if (removeStart >= 0)
                {
                    if (removeEnd <= removeStart)
                        removeEnd = removeStart + 1;
                    content.delete(removeStart, removeEnd);
                }
               
                if ((insertAt >= 0) && (toInsert != null))
                {
                    if (insertAt >= content.length())
                        content.append(toInsert);
                    else
                        content.insert(insertAt, toInsert);
                }

                position = finalPosition;
                wasEdited = true;
                if (clearSelection)
                    clearSelection();

                saveContentToHistory();
                return true;
            }

            private boolean removeMatchingLinePrefix()
            {
                if (position <= 1)
                    return false;
                if (getWhitespaceIndentPosition() != position)
                    return false;
                int line = getCurrentLineIndex();
                if (line < 1)
                    return false;

                String prevPrefix = getMatchingLinePrefix(1);
                if (prevPrefix.length() == 0)
                    return false;
                int currentCol = position - lines[line].start;
                int limit = Math.min(currentCol, prevPrefix.length());
                if (currentCol > prevPrefix.length())
                    limit++;

                for (int i=line-1; i>=0; i--)
                {
                    if (lines[i].end <= lines[i].start+1) // simple blank lines with no content
                        continue;

                    for (int j=lines[i].start, col=0; (col < limit) && (j<lines[i].end); j++, col++)
                    {
                        char ch = content.charAt(j);
                        if (!Character.isWhitespace(ch))
                            return alterContent(lines[line].start + col, position, -1, null, lines[line].start + col);
                    }
                }
                
                return false;
            }

            private void contractIndentation()
            {
                for (int i=0; i<AUTO_CLOSING_INDENTS.length; i++)
                {
                    String toMatch = AUTO_CLOSING_INDENTS[i];
                    int refPos = position - toMatch.length();

                    if ((position < toMatch.length()) || (refPos < 0))
                        continue;
                    position -= toMatch.length();

                    try
                    {
                        if (getWhitespaceIndentPosition() != refPos)
                            continue;

                        boolean match = true;
                        for (int j=0; j<toMatch.length(); j++)
                            if (content.charAt(refPos+j) != toMatch.charAt(j))
                            {
                                match = false;
                                break;
                            }
                        
                        if (match)
                            removeMatchingLinePrefix();
                    }
                    finally
                    {
                        position += toMatch.length();
                    }
                }
            }

            private boolean handleKeyboardNavigation(KeyEvent evt)
            {
                if ((content.length() == 0) || (position < 0))
                    return false;

                KeyCode code = evt.getCode();            
                int newPos = position;

                if ((KeyCode.KP_UP == code) || (KeyCode.UP == code))
                {
                    int line = getCurrentLineIndex();
                    if (line <= 0)
                        return true;

                    int col = position - lines[line].start;
                    savedColumn = col = savedColumn < 0 ? col : savedColumn;
                    newPos = Math.min(lines[line-1].start + col, lines[line-1].end);
                }
                else if ((KeyCode.KP_DOWN == code) || (KeyCode.DOWN == code))
                {
                    int line = getCurrentLineIndex();
                    if (line < 0)
                        return true;
                    
                    int col = position - lines[line].start;
                    savedColumn = savedColumn < 0 ? col : savedColumn;
                    if (line < lines.length-1)
                        newPos = Math.min(lines[line+1].start + savedColumn, lines[line+1].end);
                    else
                        newPos = lines[lines.length-1].end;
                }
                else if (KeyCode.PAGE_DOWN == code)
                {
                    int line = getCurrentLineIndex();
                    if (line < 0)
                        return true;

                    int lowestLine = Math.min(lines.length-1, line + (int) (editor.getHeight()/lineHeight));
                    int col = position - lines[line].start;
                    savedColumn = savedColumn < 0 ? col : savedColumn;
                    newPos = Math.min(lines[lowestLine].start + savedColumn, lines[lowestLine].end);
                }
                else if (KeyCode.PAGE_UP == code)
                {
                    int line = getCurrentLineIndex();
                    if (line < 0)
                        return true;
                    
                    int topLine = Math.max(0, line - (int) (editor.getHeight()/lineHeight));
                    int col = position - lines[line].start;
                    savedColumn = savedColumn < 0 ? col : savedColumn;
                    newPos = Math.min(lines[topLine].start + savedColumn, lines[topLine].end);
                }
                else if (KeyCode.HOME == code)
                {
                    if (evt.isShortcutDown())
                        newPos = 0;
                    else
                    {
                        int line = getCurrentLineIndex();
                        if (line >= 0)
                            newPos = lines[line].start;
                        else
                            newPos = 0;
                    }
                    savedColumn = -1;
                }
                else if (KeyCode.END == code)
                {
                    if (evt.isShortcutDown())
                        newPos = content.length();
                    else
                    {
                        int line = getCurrentLineIndex();
                        if (line >= 0)
                            newPos = lines[line].end;
                        else
                            newPos = content.length();
                    }
                    savedColumn = -1;
                }
                else if ((KeyCode.KP_LEFT == code) || (KeyCode.LEFT == code))
                {
                    newPos = Math.max(position-1, 0);
                    savedColumn = -1;
                }
                else if ((KeyCode.KP_RIGHT == code) || (KeyCode.RIGHT == code))
                {
                    newPos = Math.min(position+1, content.length());
                    savedColumn = -1;
                }
                else
                {
                    savedColumn = -1;
                    return false;
                }

                if (evt.isShiftDown())
                {
                    if (selectionStart < 0)
                    {
                        selectionStart = Math.min(position, newPos);
                        selectionEnd = Math.max(position, newPos);
                        position = newPos;
                    }
                    else
                    {
                        if (position == selectionEnd)
                            position = selectionEnd = newPos;
                        else if (position == selectionStart)
                            position = selectionStart = newPos;
                        else
                            position = newPos;
                        
                        int ss = Math.min(selectionStart, selectionEnd);
                        int mm = Math.max(selectionStart, selectionEnd);
                        selectionStart = ss;
                        selectionEnd = mm;
                    }
                }
                else
                {
                    clearSelection();
                    position = newPos;
                }
                    
                String ss = getSelectedText();
                if ((ss != null) && (ss.length() > 0))                    
                    setLastSelection(ss);
                return true;
            }

            private boolean handleCharacter(KeyEvent evt)
            {
                if (!editable)
                    return false;

                KeyCode code = evt.getCode();
                if ((KeyCode.BACK_SPACE == code) || (KeyCode.DELETE == code))
                {
                    if ((selectionStart >= 0) && (selectionEnd >= 0))
                        return alterContent(selectionStart, selectionEnd, -1, null, selectionStart);
                    else if (KeyCode.BACK_SPACE == code)
                    {
                        if (!removeMatchingLinePrefix() && (position > 0))
                            return alterContent(Math.min(position-1, content.length()-1), -1, -1, null, Math.max(position-1, 0));
                    }
                    else if (KeyCode.DELETE == code)
                    {
                        if (position < content.length())
                            return alterContent(position, -1, -1, null, Math.min(position, content.length()));
                    }

                    return true;
                }
                
                if (evt.getEventType() != KeyEvent.KEY_TYPED)
                {
                    if (KeyCode.TAB == code)
                        return true;
                    return false;
                }
                
                String chString = evt.getCharacter();
                if ((chString == null) || (chString.length() == 0))
                    return false;

                char ch = chString.charAt(0);
                if ((ch == 0) || (ch == '\b') || (ch == 127) || (ch == 27))
                    return false;
                if (ch == '\r')
                    ch = '\n';

                if (selectionStart >= 0)
                    position = selectionStart;

                if (ch == '\n')
                {
                    String toInsert = "";
                    if (position >= 0)
                    {
                        int line = getCurrentLineIndex();
                        int start = lines[line].start;
                        toInsert = getMatchingLinePrefix(0);
                        int col = position - lines[line].start;
                        if (col < toInsert.length())
                            toInsert = toInsert.substring(0, col);
                    }

                    return alterContent(selectionStart, selectionEnd, Math.max(0, position), ch+toInsert, position + toInsert.length()+1);
                }
                else if (ch == '\t')
                {
                    if (evt.isShiftDown())
                    {
                        if (!removeMatchingLinePrefix() && (position > 0))
                            return alterContent(Math.min(position-1, content.length()-1), -1, -1, null, Math.max(position-1, 0));
                    }
                    else
                    {
                        if ((position > 0) && (getWhitespaceIndentPosition() == position))
                        {
                            int line = getCurrentLineIndex();
                            int start = lines[line].start;
                            String toInsert = getMatchingLinePrefix(1);
                            int col = position - lines[line].start;

                            if ((col >= 0) && (toInsert.length() > col) && (line >= 0))
                            {
                                toInsert = toInsert.substring(col);
                                return alterContent(-1, -1, position, toInsert, position + toInsert.length());
                            }
                        }
                    
                        return alterContent(-1, -1, Math.max(0, position), "    ", position + 4);
                    }
                }
                else
                {
                    int originalPos = position;
                    alterContent(selectionStart, selectionEnd, Math.max(0, position), ""+ch, position+1);
                    contractIndentation();
                    if (originalPos < 0)
                        position = content.length();
                }

                return true;
            }

            private boolean handleKeyboardEdit(KeyEvent evt)
            {
                KeyCode code = evt.getCode();
                
                if (evt.isAltDown())
                {
                    if (!editable)
                        return false;
                    
                    int line = getCurrentLineIndex();
                    int start = getLineStartPosition(line);
                    int end = getLineEndPosition(line);
                    int columnOffset = position - start;

                    if (code == KeyCode.UP)
                    {
                        int prevStart = getLineStartPosition(line-1);
                        if ((prevStart < 0) || (start <= 0) || (line == 0))
                            return true;

                        String lineText = content.substring(start, end);
                        return alterContent(start, end, prevStart, lineText, prevStart + columnOffset);
                    }
                    else if (code == KeyCode.DOWN)
                    {   
                        String lineText = content.substring(start, end);
                        alterContent(start, end, -1, null, position);
                        int nextStart = getLineStartPosition(line+1);

                        if (nextStart >= content.length())
                            return alterContent(-1, -1, content.length(), "\n"+lineText, nextStart+1+columnOffset);
                        else
                            return alterContent(-1, -1, nextStart, lineText, nextStart+columnOffset);
                    }
                }

                char ch = 0;
                String charString = evt.getCharacter();
                if ((charString != null) && (charString.length() > 0))
                    ch = charString.charAt(0);

                if ((code == KeyCode.TAB) || (ch == '\t'))
                {
                    if (!editable || (selectionStart < 0) || (selectionEnd < 0))
                        return false;
                    if (evt.getEventType() != KeyEvent.KEY_TYPED)
                        return true;

                    if (evt.isShiftDown())
                    {
                        if (selectionStart > 4)
                        {
                            if (content.substring(selectionStart-4, selectionStart).equals("    "))
                            {
                                selectionStart -= 4;
                                selectionEnd -= 4;
                                alterContent(selectionStart, selectionStart+4, -1, null, position-4, false);
                            }
                            else if (content.substring(selectionStart, selectionStart+4).equals("    "))
                            {
                                selectionEnd -= 4;
                                alterContent(selectionStart, selectionStart+4, -1, null, position-4, false);
                            }
                        }
                        
                        for (int i=selectionStart; i<selectionEnd-5; i++)
                        {
                            if (content.substring(i, i+5).equals("\n    "))
                            {
                                selectionEnd -= 5;
                                alterContent(i+1, i+5, -1, null, position-4, false);
                            }
                        }
                    }
                    else
                    {
                        alterContent(-1, -1, selectionStart, "    ", position+4, false);
                        selectionStart += 4;
                        selectionEnd += 4;

                        for (int i=selectionStart; i<selectionEnd; i++)
                        {
                            if (content.charAt(i) == '\n')
                            {
                                alterContent(-1, -1, i+1, "    ", position+4, false);
                                i+=5;
                                selectionEnd += 4;
                            }
                        }
                    }
                    
                    return true;
                }

                if (evt.isShortcutDown())
                {
                    if (code == KeyCode.C)
                    {
                        if (selectionStart >= 0)
                        {
                            String selection = content.substring(selectionStart, selectionEnd);
                            Clipboard clipboard = Clipboard.getSystemClipboard();

                            ClipboardContent clipContent = new ClipboardContent();
                            clipContent.putString(selection);
                            clipboard.setContent(clipContent);
                            return true;
                        }
                    }
                    else if ((code == KeyCode.X) && editable)
                    {
                        if (selectionStart >= 0)
                        {
                            String selection = content.substring(selectionStart, selectionEnd);
                            Clipboard clipboard = Clipboard.getSystemClipboard();

                            ClipboardContent cutText = new ClipboardContent();
                            cutText.putString(selection);
                            clipboard.setContent(cutText);

                            return alterContent(selectionStart, selectionEnd, -1, null, selectionStart);
                        }
                    }
                    else if ((code == KeyCode.V) && editable)
                    {
                        Clipboard clipboard = Clipboard.getSystemClipboard();
                    
                        Object toPaste = clipboard.getContent(DataFormat.PLAIN_TEXT);
                        if (toPaste == null)
                            toPaste = clipboard.getContent(DataFormat.URL);
                        if (toPaste == null)
                            toPaste = clipboard.getContent(DataFormat.HTML);
                    
                        if (toPaste == null)
                            return true;
                        
                        if ((selectionStart >= 0) && (selectionEnd >= 0))
                            position = selectionStart;
                    
                        String str = Utils.removeUnprintableChars(toPaste.toString());
                        return alterContent(selectionStart, selectionEnd, Math.max(0, position), str, position + str.length());
                    } 
                    else if ((code == KeyCode.Z) && editable)
                    {
                        if (evt.isShiftDown())
                            redoLastUndo();
                        else
                            undoLastEdit();
                        wasEdited = true;
                        return true;
                    }
                    else if ((code == KeyCode.R) && editable)
                    {
                        redoLastUndo();
                        wasEdited = true;
                        return true;
                    }
                    else if (code == KeyCode.A)
                    {
                        setSelectedRegion(0, content.length());
                        return true;
                    }
                    else if ((code == KeyCode.D) && editable)
                    {
                        int line = getCurrentLineIndex();
                        if (line < 0)
                            return true;
                   
                        int start = getLineStartPosition(line);
                        int end = getLineEndPosition(line);
                        if (start >= end)
                            return true;

                        if (evt.isShiftDown())
                            return alterContent(-1, -1, end, content.substring(start, end), end);
                        else
                            return alterContent(start, end, -1, null, start);
                    }
                }

                return false;
            }

            public boolean handleKeyEvent(KeyEvent evt)
            {
                KeyCode code = evt.getCode();
                if ((code == KeyCode.SHIFT) || (code == KeyCode.CONTROL) || (code == KeyCode.ALT_GRAPH) || (code == KeyCode.CAPS) || (code == KeyCode.NUM_LOCK) || (code == KeyCode.ESCAPE) || (code == KeyCode.COMMAND) || (code == KeyCode.ALT) || (code == KeyCode.SHORTCUT))
                    return true;

                wasEdited = false;
                boolean initialEditedState = hasBeedEdited; 
                try
                {
                    showing = true;
                    if (handleKeyboardEdit(evt))
                        return true;
                    if (handleKeyboardNavigation(evt))
                        return true;
                    if (evt.isShortcutDown())
                        return false;

                    return handleCharacter(evt);
                }
                finally
                {
                    repaintView();
                    if (wasEdited)
                    {
                        contentChanged();
                        if (!initialEditedState)
                            markEdited(true);
                    }
                }
            }

            private void repaintView()
            {
                doLayout();

                double minVisibleY = getViewportStart();
                double maxVisibleY = minVisibleY + Math.max(lineHeight, getHeight() - lineHeight);
                
                if (yPosition < minVisibleY)
                    scrollBar.setValue(100*Math.min(1, yPosition/textHeight));
                else if (yPosition >= maxVisibleY)
                    scrollBar.setValue(100*Math.min(1, (yPosition+lineHeight)/textHeight));
                
                scrollBar.configureBar();
                drawText();
            }

            private int findTextPositionFromScreenPoint(double x, double y)
            {
                y += getViewportStart();
                if (y <= 0)
                    return 0;

                double w = getWidth();
                for (int i=0; i<lines.length; i++)
                {
                    int pos = lines[i].locateCaret(x, y, w);
                    if (pos < 0)
                        continue;

                    return pos;
                }

                if (content.length() >= 0)
                    return content.length();
                return 0;
            }

            void setCaretPosition(int pos)
            {
                savedColumn = -1;
                position = Math.min(content.length(), Math.max(0, pos));
                positionCaret();
            }

            void locateCaret(double x, double y)
            {
                savedColumn = -1;
                position = findTextPositionFromScreenPoint(x, y);
                positionCaret();
            }

            void clearSelection()
            {
                selectionStart = selectionEnd = -1;
            }

            String getSelectedText()
            {
                if ((selectionStart == -1) || (selectionEnd < selectionStart))
                    return null;
                return content.substring(selectionStart, selectionEnd);
            }

            void setSelectedRegion(int start, int end)
            {
                setSelectedRegion(start, end, -1);
            }

            void setSelectedRegion(int start, int end, int newPos)
            {
                showing = true;
                selectionStart = Math.max(0, start);
                selectionEnd = Math.min(end, content.length());
                selectionEnd = Math.max(selectionStart, selectionEnd);
                if (newPos < 0)
                    position = selectionEnd;
                setLastSelection(getSelectedText());
                drawText();
            }

            void selectLines(int startLine, int endLine)
            {
                int line = 0, lineStartPos=0;
                for (int i=0; i<content.length(); i++)
                {
                    if (content.charAt(i) != '\n')
                        continue;
                    
                    if (line < startLine)
                        lineStartPos = i+1;
                    else if (line >= endLine)
                    {
                        setSelectedText(lineStartPos, i);
                        return;
                    }
                    line++;
                }
                
                if (lineStartPos >= 0)
                    setSelectedText(lineStartPos, content.length());
            }

            int getHighlightLineStart()
            {
                if (selectionStart < 0)
                    return -1;
                
                int result = 0;
                for (int i=0; i<content.length(); i++)
                {
                    if (content.charAt(i) == '\n')
                        result++;
                    
                    if (i >= selectionStart)
                        break;
                }
                return result;
            }
            
            int getHighlightLineEnd()
            {
                if (selectionEnd < 0)
                    return -1;
                
                int result = 0;
                for (int i=0; i<content.length(); i++)
                {
                    if (content.charAt(i) == '\n')
                        result++;
                    
                    if (i >= selectionEnd)
                        break;
                }
                return result;
            }

            void highlightWordAt(double x1, double y1)
            {
                clearSelection();
                int pos = findTextPositionFromScreenPoint(x1, y1);
                if (pos >= content.length())
                    return;
                if ((pos < 0) || !Character.isLetterOrDigit(content.charAt(pos)))
                    return;
                
                selectionStart = pos;
                for (selectionStart = pos; selectionStart>0; selectionStart--)
                    if (!Character.isLetterOrDigit(content.charAt(selectionStart)))
                    {
                        selectionStart++;
                        break;
                    }
                
                selectionEnd = pos;
                for (selectionEnd = pos; selectionEnd<content.length(); selectionEnd++)
                    if (!Character.isLetterOrDigit(content.charAt(selectionEnd)))
                        break;

                position = selectionEnd;
                positionCaret();
                setLastSelection(getSelectedText());
            }

            void locateSelection(double x1, double y1, double x2, double y2)
            {
                if ((x1 < 0) || (y1 < 0) || (x2 < 0) || (y2 < 0))
                {
                    clearSelection();
                    return;
                }

                int p1 = findTextPositionFromScreenPoint(x1, y1);
                int p2 = findTextPositionFromScreenPoint(x2, y2);
                if (p1 == p2)
                    clearSelection();
                else
                {
                    selectionStart = Math.min(p1, p2);
                    selectionEnd = Math.max(p1, p2);
                    setLastSelection(getSelectedText());
                }
            }

            void positionCaret()
            {
                int charsPerLine = (int) (Math.max(1,  (getWidth()-lineNumberMargin) / charWidth));
                int lineIndex = getCurrentLineIndex();

                if (lineIndex < 0)
                {
                    yPosition = 0;
                    xPosition = lineNumberMargin;
                    caretPositioned(0, 0);
                }
                else
                {
                    LineOfText ll = lines[lineIndex];
                    int pos = Math.max(0, position - ll.start);
                    int row = pos / charsPerLine;
                    int col = pos % charsPerLine;
                
                    yPosition = ll.yPosition + lineHeight*row;
                    xPosition = lineNumberMargin + col*charWidth -1;
                    caretPositioned(lineIndex, col);
                }
            }

            void drawCaret(GraphicsContext gc)
            {
                if (!isFocused() || !showing)
                    return;

                double yPos = yPosition - getViewportStart();
                if ((yPos < -lineHeight) || (yPos >= getHeight() + lineHeight))
                    return;
                gc.fillRect(xPosition, yPos, 2, lineHeight);
            }
        }

        class LineOfText 
        {
            int lineNumber, start, end;
            double yPosition, yHeight;
            
            LineOfText(int number, int start, int end)
            {
                this.start = start;
                this.end = end;
                lineNumber = number;
            }

            boolean isAllWhitespace()
            {
                if (content.length() == 0)
                    return true;
                for (int i=start; i<end; i++)
                    if (!Character.isWhitespace(content.charAt(Math.max(0, Math.min(content.length()-1, i)))))
                        return false;
                return true;
            }

            double positionLine(double yStart, double totalWidth)
            {
                int charsPerLine = (int) (Math.max(1, totalWidth / charWidth));
                yHeight = ((end-start)/charsPerLine + 1)*lineHeight;
                yPosition = yStart;
                return yHeight;
            }

            int locateCaret(double x, double y, double totalWidth)
            {
                if ((y < yPosition) || (y >= yPosition + yHeight))
                    return -1;
                x += charWidth/3;

                int charsPerLine = (int) (Math.max(1, (totalWidth - lineNumberMargin) / charWidth));
                int row = (int) ((y - yPosition)/lineHeight);
                int col = Math.max(0, ((int) (x - lineNumberMargin))/charWidth);
                
                return Math.max(start, Math.min(end, start + col + row*charsPerLine));
            }
            
            double drawLine(GraphicsContext gc, double yPosition, double totalWidth, double viewportPos, double viewportHeight)
            {  
                this.yPosition = yPosition;
                yHeight = lineHeight;
                double yPos = yPosition;
                double xPos = lineNumberMargin;

                if (start == end)
                {
                    boolean isSelected = (start>=caret.selectionStart) && (start<caret.selectionEnd);
                    if (isSelected)
                    {
                        gc.setFill(selectionColour);
                        gc.fillRect(xPos, yPos-viewportPos, charWidth, lineHeight);
                    }
                }
                else
                {
                    for (int i=start; i<=end; xPos += charWidth, i++)
                    {
                        if (i >= content.length())
                            break;

                        char ch = content.charAt(i);
                        if (xPos + charWidth > totalWidth)
                        {
                            yHeight += lineHeight;
                            yPos += lineHeight;
                            xPos = lineNumberMargin;
                        }

                        if (ch == '\n')
                            break;                        
                        if ((ch < 0x20) || (ch > 0x7E))
                            continue;

                        double yy = yPos + fontBaseline - viewportPos;
                        if ((yy < -lineHeight) || (yy >= viewportHeight + lineHeight))
                            continue;
                        
                        boolean isSelected = (i>=caret.selectionStart) && (i<caret.selectionEnd);
                        if (isSelected)
                        {
                            gc.setFill(selectionColour);
                            gc.fillRect(xPos, Math.max(0, yPos-viewportPos), charWidth, lineHeight+1);
                        }
                        
                        setStyleForCharacter(content, lineNumber, i, isSelected, caret.position, gc);
                        gc.fillText(String.valueOf(ch), xPos, yy);
                    }
                }

                return yHeight;
            }

            void drawLineNumber(GraphicsContext gc, double viewportPos, double viewportHeight)
            {
                double yy = yPosition + fontBaseline - viewportPos;
                if ((yy < -lineHeight) || (yy >= viewportHeight + lineHeight))
                    return;
                
                setStyleForLineNumber(content, lineNumber, caret.position, gc);
                int n = lineNumber;
                int xPos = lineNumberMargin - charWidth - 7;
                while (n > 0)
                {
                    int digit = n % 10;
                    gc.fillText(""+digit, xPos, yy);
                    n /= 10;
                    xPos -= charWidth;
                }
            }

            public String toString()
            {
                return content.substring(start, end);
            }
        }
        
        public void setText(String src)
        {
            caret.position = 0;
            caret.showing = true;
            content = new StringBuffer(Utils.removeUnprintableChars(src));

            scrollBar.setValue(0);
            doLayout();
            drawText();
            scrollBar.configureBar();
            caretPositioned(0, 0);

            saveContentToHistory();
            contentChanged();
        }

        private void doLayout()
        {
            int start = 0;
            int lineNumber = 1;
            ArrayList buffer = new ArrayList();
            for (int i=0; i<content.length(); i++)
            {
                char ch = content.charAt(i);
                if (ch == '\n')
                {
                    buffer.add(new LineOfText(lineNumber++, start, i));
                    start = i+1;
                }
            }

            if (start <= content.length())
                buffer.add(new LineOfText(lineNumber++, start, content.length()));
            
            lines = new LineOfText[buffer.size()];
            buffer.toArray(lines);

            lineNumberMargin = Math.max(2, 1 + (int) Math.log10(lineNumber))*charWidth + 10; 

            double w = getWidth() - lineNumberMargin;
            textHeight = 0;
            for (int i=0; i<lines.length; i++)
                textHeight += lines[i].positionLine(textHeight, w);

            caret.positionCaret();
            layoutRefreshed(content);
        }

        private void drawText()
        {
            GraphicsContext gc = getGraphicsContext2D();
            double width = getWidth();
            double height = getHeight();
            setClip(new Rectangle(0, 0, width, height));
            gc.clearRect(0, 0, (int) width, (int) height);

            gc.setFont(monospacedFont);
            double yPos = 0;
            double viewportStart = getViewportStart();

            for (int i=0; i<lines.length; i++)
                yPos += lines[i].drawLine(gc, yPos, width, viewportStart, height);

            gc.setFill(lineNumberMarginColour);
            gc.fillRect(0, 0, lineNumberMargin-5, height);
            for (int i=0; i<lines.length; i++)
                lines[i].drawLineNumber(gc, viewportStart, height);

            gc.setFill(caretColour);
            caret.drawCaret(gc);

            gc.setFill(Color.BLACK);
            gc.strokeRect(0, 0, width, height);
        }
    }

    protected void textScrolled(double scrollPosition) {}

    protected void caretPositioned(int line, int charPos) {}

    protected void contentChanged() {}

    protected void layoutRefreshed(CharSequence content) {}

    protected void setStyleForCharacter(CharSequence content, int lineNumber, int charPos, boolean isSelected, int caretPos, GraphicsContext gc)
    {
        gc.setFill(textColour);
    }
    
    protected void setStyleForLineNumber(CharSequence content, int lineNumber, int caretPos, GraphicsContext gc)
    {
        gc.setFill(lineNumberColour);
    }
}
