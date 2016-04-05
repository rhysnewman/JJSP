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

public class SQLTablePane extends JSONTablePane 
{
    private SQLDriver driver;
    private TextField pageDisplay;

    private DataViewTable table;
    private SQLTableCell editingCell;
    private FileChooser fileChooser;
    private long totalRows, currentStart;
    private int rowsPerPage, primaryKeyColumn;
    private String schemaName, tableName, primaryKeyColName, selectedColumnsList;

    private int tableRowIndex;
    private double beforeY, beforeRowHeight;
    private boolean isRowResizeEvent = false;

    public SQLTablePane(String schemaName, String tableName, SQLDriver sqlDriver) throws Exception
    {
        super(sqlDriver.getDatabaseURI().resolve(schemaName+"/"+tableName));

        this.driver = sqlDriver;
        this.tableName = tableName;
        this.schemaName = schemaName;
        currentStart = totalRows = 0;
    
        editingCell = null;
        beforeRowHeight = 40;
        setStyle("-fx-font-size: 16px;"); //Sets the increment and decrement button sizes 

        fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));

        table = new DataViewTable();
        table.setFixedCellSize(beforeRowHeight);
        table.setOnMouseDragged((evt) -> 
                              {
                                  if (!isRowResizeEvent)
                                      return;

                                  double y = evt.getY();
                                  double scaleFactor = getHeight()/beforeRowHeight;
                                  double diff = (y - beforeY) / scaleFactor * 3;
                                  table.setFixedCellSize(Math.max(30, beforeRowHeight + diff));
                              });
        
        table.setOnMouseMoved((evt) -> 
                              {
                                  beforeY = evt.getY();
                                  beforeRowHeight = table.getFixedCellSize();
                              });
        
        table.setRowFactory((tbl) -> 
                            { 
                                TableRow tr = new TableRow();
                                tr.setOnMouseMoved((evt) -> 
                                                   {
                                                       double y = evt.getY();
                                                       double height = tr.getHeight(); 
                                                       isRowResizeEvent = (y < 7) || (y > height-7);
                                
                                                       if (isRowResizeEvent)
                                                           setCursor(Cursor.N_RESIZE);
                                                       else
                                                           setCursor(Cursor.DEFAULT);
                                                       
                                                       tableRowIndex = tr.getIndex();
                                                   } );
                                tr.setOnMouseExited((evt) -> setCursor(Cursor.DEFAULT));
                                return tr;
                            });

        ResultSet cols = null;
        selectedColumnsList = "";
        SQLDriver.ConnectionWrapper wrapper = null;
        try
        {
            wrapper = driver.getConnection(1000);
            Statement stmt = wrapper.getStatement();
            primaryKeyColName = null;
            primaryKeyColumn = -1;

            if (!isPostgres())
            {
                stmt.execute("use "+schemaName);
                cols = stmt.executeQuery("show columns from "+tableName+";");
            }
            else
                cols = stmt.executeQuery("SELECT column_name,data_type,is_nullable,'xx',ordinal_position  FROM information_schema.columns WHERE table_name = '"+tableName+"';");

            HashMap positions = new HashMap();
            for (int colIndex=0; cols.next(); colIndex++)
            {
                String title = cols.getString(1);
                String typeDesc = cols.getString(2).toLowerCase();
                
                boolean isNullable = cols.getBoolean(3);
                String isPrimaryKey = cols.getString(4);
                if (isPrimaryKey.equals("PRI"))
                {
                    primaryKeyColName = title;
                    primaryKeyColumn = colIndex;
                }
                
                try
                {
                    positions.put(title, cols.getInt(5)-1);
                }
                catch (Exception e) {}
                
                Class type = String.class;
                if (typeDesc.startsWith("int"))
                    type = Long.class;
                else if (typeDesc.startsWith("float") || typeDesc.startsWith("double"))
                    type = Double.class;
                else if (typeDesc.indexOf("blob") >= 0)
                    type = String.class;

                boolean showAsJSON = typeDesc.toLowerCase().equals("json");

                table.getColumns().add(createColumn(title, colIndex, type, showAsJSON));
                if (selectedColumnsList.length() > 0)
                    selectedColumnsList += ", ";

                if (typeDesc.toLowerCase().indexOf("blob") >= 0)
                    selectedColumnsList += "'BLOB' as "+title;
                else 
                    selectedColumnsList += title;
            }

            if (primaryKeyColName == null)
            {
                ResultSet pks = stmt.getConnection().getMetaData().getPrimaryKeys(null, null, tableName);
                while (pks.next())
                {
                    primaryKeyColName = pks.getString(4);
                    primaryKeyColumn = ((Integer) positions.get(primaryKeyColName)).intValue();
                }
            }
        }
        finally
        {
            try
            {
                cols.close();
            }
            catch (Exception e) {}
            driver.returnToPool(wrapper);
        }

        Button previous = new Button("< Previous");
        previous.setOnAction((evt) -> showRows(currentStart - this.rowsPerPage, this.rowsPerPage));
        Button next = new Button("Next >");
        next.setOnAction((evt) -> showRows(currentStart + this.rowsPerPage, this.rowsPerPage));
        Button first = new Button("<< First");
        first.setOnAction((evt) -> showRows(0, this.rowsPerPage));
        Button last = new Button("Last >>");
        last.setOnAction((evt) -> showRows(totalRows - this.rowsPerPage, this.rowsPerPage));

        Button deleteRow = new Button("Delete Row");
        deleteRow.setOnAction((event) -> 
                              {
                                  List rows = table.getSelectionModel().getSelectedIndices();
                                  if (rows.size() == 0)
                                      return;
                                  int row = ((Integer) rows.get(0)).intValue();
                                  Object[] rowData = (Object[]) table.getItems().get(row);
                                  Object primaryKey = rowData[primaryKeyColumn];
                                  
                                  Button del = new Button("Confirm Delete");
                                  del.setOnAction((evt) -> 
                                                  {
                                                      SQLDriver.ConnectionWrapper wrapper2 = null;
                                                      try
                                                      {
                                                          wrapper2 = driver.getConnection(1000);
                                                          Statement stmt = wrapper2.getStatement();
                                                          if (!isPostgres())
                                                              stmt.execute("use "+schemaName);

                                                          table.getSelectionModel().clearSelection();
                                                          stmt.executeUpdate("delete from "+tableName+" where "+primaryKeyColName+" = "+primaryKey);
                                                          wrapper2.commit();

                                                          showRows(currentStart, rowsPerPage);
                                                      }
                                                      catch (Exception e)
                                                      {
                                                          showError("Error deleting data", e);
                                                      }
                                                      finally
                                                      { 
                                                          driver.returnToPool(wrapper2);
                                                          clearAlert();
                                                      }
                                                  });
                                  
                                  Button cancel = new Button("Do not delete");
                                  cancel.setOnAction((evt) ->  clearAlert());
                                  
                                  HBox hb = new HBox(20);
                                  hb.getChildren().addAll(del, cancel);
                                  hb.setAlignment(Pos.CENTER);
                                  
                                  BorderPane overlay = new BorderPane();
                                  overlay.setCenter(new Label("Are you sure you want to delete row "+primaryKey+" ?"));
                                  overlay.setBottom(hb);
                                  overlay.setMaxHeight(140);
                                  overlay.setMaxWidth(500);
                                  
                                  overlay.setStyle("-fx-background-color: white; -fx-padding:20px; -fx-border-radius: 10 10 10 10; -fx-background-radius: 10 10 10 10;");
                                  overlayAlert(overlay);
                              });
        
        Button addRow = new Button("Add Row");
        addRow.setOnAction((evt) -> 
                           {
                               SQLDriver.ConnectionWrapper wrapper2 = null;
                               try
                               {
                                   wrapper2 = driver.getConnection(1000);
                                   Statement stmt = wrapper2.getStatement();
                                   
                                   if (!isPostgres())
                                       stmt.execute("use "+schemaName);

                                   table.getSelectionModel().clearSelection();
                                   if (isPostgres())
                                       stmt.executeUpdate("insert into "+tableName+" DEFAULT VALUES");
                                   else
                                       stmt.executeUpdate("insert into "+tableName+" () VALUES()");

                                   wrapper2.commit();
                                   if (totalRows > rowsPerPage)
                                       showRows(totalRows, rowsPerPage);
                                   else
                                       showRows(0, rowsPerPage);
                               }
                               catch (Exception e)
                               {
                                   driver.returnToPool(wrapper2);
                                   showError("Error inserting row", e);
                               }
                           });
        
        ComboBox pageSizeSelection = new ComboBox();
        pageSizeSelection.getItems().addAll(10, 50, 100, 500, 1000, 10000);
        rowsPerPage = 1000;
        pageSizeSelection.getSelectionModel().select(4);
        pageSizeSelection.setOnAction((evt) -> 
                                      { 
                                          this.rowsPerPage = ((Integer) pageSizeSelection.getSelectionModel().getSelectedItem()).intValue(); 
                                          showRows(currentStart, this.rowsPerPage);
                                      });
        
        pageDisplay = new TextField("");
        pageDisplay.setMinWidth(200);
        pageDisplay.setOnAction((evt) -> 
                                { 
                                    String prevText = pageDisplay.getText().trim();
                                    try 
                                    { 
                                        long row = Long.parseLong(prevText); 
                                        showRows(row, this.rowsPerPage); 
                                    }
                                    catch (Exception e) 
                                    {
                                        showError("Invalid number format", e);
                                    }
                                });
        pageDisplay.focusedProperty().addListener((evt) -> {  Platform.runLater(() -> pageDisplay.selectAll()); });
        
        HBox hBox = new HBox(10);
        HBox.setHgrow(pageDisplay, Priority.ALWAYS);
        BorderPane.setMargin(hBox, new Insets(10,0,0,0));
        hBox.setAlignment(Pos.BASELINE_CENTER);
        hBox.getChildren().addAll(addRow, deleteRow, pageDisplay, first, last, new Label("Rows per page:"), pageSizeSelection, previous, next);
        
        mainPane.setCenter(table);
        mainPane.setBottom(hBox);

        setMinWidth(400);
        setMinHeight(220);
        showRows(0, rowsPerPage);
    }

    public boolean isDisposed()
    {
        return driver.isClosed();
    }
    
    private TableColumn createColumn(String title, int columnIndex, Class type, boolean showAsJSON)
    {
        TableColumn col = new TableColumn(title);
        col.setStyle("-fx-alignment: CENTER-LEFT;");
        col.setMinWidth(100);
        col.setPrefWidth(200);
        col.setCellValueFactory((element) -> 
                                { 
                                    Object[] row = (Object[]) ((TableColumn.CellDataFeatures) element).getValue();
                                    return new SimpleObjectProperty(row[columnIndex]);
                                });

        col.setCellFactory((cell) -> { return new SQLTableCell(title, columnIndex, type, showAsJSON); });
        return col;
    }

    class SQLTableCell extends TableCell
    {
        int colIndex;
        Class colClass;
        String colTitle;
        boolean showAsJSON;
        ImageView imageView;
        TextField editingField;

        SQLTableCell(String title, int colIndex, Class colCls, boolean showAsJSON)
        {
            this.colIndex = colIndex;
            this.colClass = colCls;
            this.colTitle = title;
            this.showAsJSON = showAsJSON;

            imageView = new ImageView();
            imageView.setPreserveRatio(true);
            imageView.setFitHeight(beforeRowHeight);
            imageView.setCache(true);

            editingField = new TextField();
            editingField.setStyle("-fx-alignment: baseline-right;");
            setEditable(colIndex != primaryKeyColumn);

            setStyle("-fx-alignment: baseline-right;");
            if (colCls == byte[].class)
            {
                setStyle("-fx-alignment: center; -fx-row-valignment: center");
                setText(null);
            }

            heightProperty().addListener((evt) -> imageView.setFitHeight(getHeight()));
        }

        String valueOf(Object obj)
        {
            if (obj == null)
                return "";
            return obj.toString();
        }
        
        public void startEdit()
        {
            super.startEdit();
            
            if (!isEditable())
            {
                super.cancelEdit();
                return;
            }

            if (showAsJSON)
            {
                super.cancelEdit();
                showJSONOverlay(valueOf(getItem()));
                return;
            }

            editingCell = this;
            setText(null);

            if (colClass == byte[].class)
            {
                Button load = new Button("Load New Data");
                load.setOnAction((evt) -> 
                                 {
                                     File selected = fileChooser.showOpenDialog(getScene().getWindow()); 
                                     if (selected == null) 
                                     {
                                         cancelEdit(true);
                                         return;
                                     }

                                     int row = getIndex();
                                     Object[] rowData = (Object[]) table.getItems().get(row);
                                     Object primaryKey = rowData[primaryKeyColumn];
                                     String sql = "update "+tableName+" SET "+colTitle+" = ? where "+primaryKeyColName+" = "+primaryKey;
                                     
                                     SQLDriver.ConnectionWrapper wrapper = null;
                                     try
                                     {
                                         byte[] rawData = Utils.load(selected);

                                         wrapper = driver.getConnection(1000);
                                         PreparedStatement ps = wrapper.getPreparedStatement(sql);

                                         ps.setBlob(1, new ByteArrayInputStream(rawData));
                                         ps.executeUpdate();

                                         wrapper.commit();
                                         pageDisplay.setText("Updated Row "+primaryKey+" with new data");

                                         rowData[colIndex] = rawData;
                                         table.getItems().set(row, rowData);
                                     }
                                     catch (Exception e)
                                     {
                                         showError("Error loading data", e);
                                     }
                                     finally
                                     {
                                         if (wrapper != null)
                                             wrapper.disposePreparedStatement(sql);
                                         driver.returnToPool(wrapper);
                                     }

                                     cancelEdit();
                                 });

                setGraphic(load);
                return;
            }
            
            editingField.setText(valueOf(getItem()));
            setGraphic(editingField);
            editingField.setOnKeyPressed((evt) -> 
                                         { 
                                             if (evt.getCode() != KeyCode.ESCAPE) 
                                                 return;
                                             cancelEdit(true);
                                         });

            Platform.runLater(() -> {editingField.requestFocus(); editingField.selectAll();});
        }

        public void cancelEdit(boolean revert)
        {
            if (isEditing())
                editingField.setText(valueOf(getItem()));
            cancelEdit();
        }

        public void cancelEdit()
        {
            super.cancelEdit();
            if (editingCell == this)
                editingCell = null;

            if (colClass == byte[].class) 
            {
                updateItem(getItem(), false);
                return;
            }
            
            Object currentValue = getItem();
            Object newValue = editingField.getText();
            if (!valueOf(currentValue).equals(valueOf(newValue)))
            {
                int row = getIndex();
                String val = newValue.toString();
                if (colClass == String.class)
                    val = "'"+val.replace("'", "''")+"'";
                else
                {
                    try
                    {
                        Double.parseDouble(val);
                    }
                    catch (Exception e) 
                    {
                        showError("Invalid Number format for column", e);
                        val = null;
                    }
                }

                if (val != null)
                {
                    SQLDriver.ConnectionWrapper wrapper = null;
                    try
                    {
                        wrapper = driver.getConnection(1000);
                        Statement stmt = wrapper.getStatement();
                        if (!isPostgres())
                            stmt.execute("use "+schemaName);

                        Object[] rowData = (Object[]) table.getItems().get(row);
                        Object primaryKey = rowData[primaryKeyColumn];
                        stmt.executeUpdate("update "+tableName+" SET "+colTitle+" = "+val+" where "+primaryKeyColName+" = "+primaryKey);
                        
                        wrapper.commit();
                        pageDisplay.setText("Updated Row "+primaryKey+" to "+val);

                        rowData[colIndex] = newValue.toString();
                        table.getItems().set(row, rowData);
                    }
                    catch (Exception e)
                    {
                        showError("Error saving data", e);
                        editingField.setText(valueOf(getItem()));
                    }
                    finally
                    {
                        driver.returnToPool(wrapper);
                    }
                }
            }

            setGraphic(null);
            setText(editingField.getText());
        }

        protected void updateItem(Object item, boolean isEmpty)
        {
            super.updateItem(item, isEmpty);

            if (colClass == byte[].class) 
            {
                if (item == null)
                {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                
                byte[] raw = (byte[]) item;
                try
                {
                    Image im = new Image(new ByteArrayInputStream(raw));
                    imageView.setImage(im);
                    imageView.setFitHeight(getHeight());
                    setGraphic(imageView);
                    setText(null);
                }
                catch (Exception e) 
                {
                    setGraphic(null);
                    setText("Binary "+raw.length+" bytes");
                }
                return;
            }
                
            if (isEditing() && isEditable())
                editingField.setText(valueOf(item));
            else 
            {
                setGraphic(null);
                setText(valueOf(item));
            }
        }
    }
    
    public void reloadContent()
    {
        showRows(currentStart, rowsPerPage);
    }

    public boolean isPostgres()
    {
        return getURI().toString().toLowerCase().startsWith("postgres");
    }

    public boolean showRows(long startRow, int pageSize)
    {
        ResultSet rss = null;
        SQLDriver.ConnectionWrapper wrapper = null;
        startRow = Math.max(0, startRow);
        
        try
        {
            if (editingCell != null)
                editingCell.cancelEdit(true);

            wrapper = driver.getConnection(1000);
            
            Connection conn = wrapper.getConnection();
            //Statement good for big rows of BLOBS
            //Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            //stmt.setFetchSize(Integer.MIN_VALUE);

            Statement stmt = wrapper.getStatement();
            if (!isPostgres())
                stmt.execute("use "+schemaName);

            rss = stmt.executeQuery("select count(*) from "+tableName+";");
            rss.next();
            totalRows = rss.getLong(1);
            rss.close();

            startRow = Math.max(0, Math.min(totalRows-1, startRow));
            int cols = table.getColumns().size();
            String sqlString = "select "+selectedColumnsList+" from "+tableName+" order by "+primaryKeyColName+" limit "+pageSize+" offset "+startRow;
            rss = stmt.executeQuery(sqlString);
            
            ObservableList rowList = FXCollections.observableArrayList();
            for (int r=0; rss.next(); r++)
            {
                Object[] row = new Object[cols];
                for (int i=0; i<cols; i++)
                {
                    try
                    {
                        row[i] = rss.getObject(i+1);
                    }
                    catch (Exception e) {}
                }

                rowList.add(row);
            }
            rss.close();

            table.setItems(rowList);
            currentStart = startRow;
            pageDisplay.setText("Showing rows "+currentStart+" to "+(currentStart+rowList.size())+" of "+totalRows);
            return true;
        }
        catch (Exception e)
        {
            showError("Error loading data", e);
            return false;
        }
        finally
        {
            try
            {
                rss.close();
            }
            catch (Exception e) {}
            driver.returnToPool(wrapper);
        }
    }

    class DataViewTable extends TableView 
    {
        DataViewTable()
        {
            setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
            setEditable(true);
            setItems(FXCollections.observableArrayList());
        }
    }

    public static String getDBTableName(URI dbURI)
    {
        String s = dbURI.toString();

        int lastSlash = s.lastIndexOf("/");
        if (lastSlash < 0)
            return null;

        return s.substring(lastSlash+1);
    }

    public static String getDBSchemaName(URI dbURI)
    {
        String s = dbURI.toString();
        int lastSlash = s.lastIndexOf("/");
        if (lastSlash < 0)
            return null;
        
        int lastSlash2 = s.lastIndexOf("/", lastSlash-1);
        if (lastSlash2 < 0)
            return null;

        return s.substring(lastSlash2+1, lastSlash);
    }

}
