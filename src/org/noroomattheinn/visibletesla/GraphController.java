/*
 * GraphController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Jul 22, 2013
 */
package org.noroomattheinn.visibletesla;

import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import org.noroomattheinn.fxextensions.TimeBasedChart;
import org.noroomattheinn.fxextensions.VTLineChart;
import org.noroomattheinn.fxextensions.VTSeries;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.timeseries.Row;
import org.noroomattheinn.utils.DefaultedHashMap;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.data.VTData;

/**
 * GraphController: Handles the capture and display of vehicle statistics
 * 
 * NOTES:
 * To add a new Series:
 * 1. If the series requires a new state object:
 *    1.1 Add the declaration of the object
 *    1.2 Initialize the object in prepForVehicle
 *    1.3 In getAndRecordStats: refresh the object and addElement on each series
 * 2. Add the corresponding checkbox
 *    2.1 Add a decalration for the checkbox and compile this source
 *    2.2 Open GraphUI.fxml and add a checkbox to the dropdown list
 * 3. Register the new series in prepSeries()
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class GraphController extends BaseController {

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private boolean displayLines = true;
    private boolean displayMarkers = true;
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML private Label readout;
    @FXML private CheckBox voltageCheckbox;
    @FXML private CheckBox currentCheckbox;
    @FXML private CheckBox rangeCheckbox;
    @FXML private CheckBox socCheckbox;
    @FXML private CheckBox rocCheckbox;
    @FXML private CheckBox powerCheckbox;
    @FXML private CheckBox speedCheckbox;
    @FXML private AnchorPane itemListContent;
    @FXML private Button nowButton;
    @FXML private AnchorPane arrow;
    
    private RadioMenuItem displayLinesMI;
    private RadioMenuItem displayMarkersMI;
    private RadioMenuItem displayBothMI;
    private TimeBasedChart chart;
    private VTLineChart lineChart = null;
    
/*------------------------------------------------------------------------------
 *
 * This section implements UI Actionhandlers
 * 
 *----------------------------------------------------------------------------*/
    
    private void showItemList(boolean visible) {
        itemListContent.setVisible(visible);
        itemListContent.setMouseTransparent(!visible);
        arrow.setStyle(visible ? "-fx-rotate: 0;" : "-fx-rotate: -90;");
    }

    @FXML void nowHandler(ActionEvent event) {
        chart.centerTime(System.currentTimeMillis());

    }

    @FXML void showItemsHandler(ActionEvent event) {
        boolean isVisible = itemListContent.isVisible();
        showItemList(!isVisible);   // Flip whether it's visible
    }

    @FXML void optionCheckboxHandler(ActionEvent event) {
        CheckBox cb = (CheckBox) event.getSource();
        boolean visible = cb.isSelected();
        VTSeries series = cbToSeries.get(cb);
        series.setVisible(visible);
        lineChart.refreshChart();

        // Remember the value for next time we start up
        prefs.storage().putBoolean(vinKey(series.getName()), visible);
    }

/*------------------------------------------------------------------------------
 *
 * VTSeries Handling
 * 
 *----------------------------------------------------------------------------*/
    
    private Map<CheckBox,VTSeries> cbToSeries = new LinkedHashMap<>(); // Preserves insertion order
    private Map<String,VTSeries> typeToSeries = new HashMap<>();
    
    private void prepSeries() {
        VTSeries.Transform<Number> distTransform = 
                vtVehicle.unitType() == Utils.UnitType.Imperial 
                ? VTSeries.idTransform : VTSeries.mToKTransform;
        lineChart.clearSeries();

        cbToSeries.put(voltageCheckbox, lineChart.register(
                new VTSeries(VTData.VoltageKey, VTSeries.millisToSeconds, VTSeries.idTransform)));
        cbToSeries.put(currentCheckbox, lineChart.register(
                new VTSeries(VTData.CurrentKey, VTSeries.millisToSeconds, VTSeries.idTransform)));
        cbToSeries.put(rangeCheckbox, lineChart.register(
                new VTSeries(VTData.EstRangeKey, VTSeries.millisToSeconds, distTransform)));
        cbToSeries.put(socCheckbox, lineChart.register(
                new VTSeries(VTData.SOCKey, VTSeries.millisToSeconds, VTSeries.idTransform)));
        cbToSeries.put(rocCheckbox, lineChart.register(
                new VTSeries(VTData.ROCKey, VTSeries.millisToSeconds, distTransform)));
        cbToSeries.put(powerCheckbox, lineChart.register(
                new VTSeries(VTData.PowerKey, VTSeries.millisToSeconds, VTSeries.idTransform)));
        cbToSeries.put(speedCheckbox, lineChart.register(
                new VTSeries(VTData.SpeedKey, VTSeries.millisToSeconds, distTransform)));
        
        // Make the checkbox colors match the series colors
        int seriesNumber = 0;
        for (Map.Entry<CheckBox,VTSeries> me: cbToSeries.entrySet()) {
            CheckBox cb = me.getKey();
            VTSeries s = me.getValue();
            cb.getStyleClass().add("cb"+seriesNumber++);
            typeToSeries.put(s.getName(), s);
        }
    }

    private void restoreLastSettings() {
        // Restore the last settings of the checkboxes
        for (CheckBox cb : cbToSeries.keySet()) {
            VTSeries s = cbToSeries.get(cb);
            boolean selected = prefs.storage().getBoolean(vinKey(s.getName()), true);
            cb.setSelected(selected);
            s.setVisible(selected);
        }

        // Restore the last display settings (display lines, markers, or both)
        displayLines = prefs.storage().getBoolean(vinKey("DISPLAY_LINES"), true);
        displayMarkers = prefs.storage().getBoolean(vinKey("DISPLAY_MARKERS"), true);

        reflectDisplayOptions();
    }

/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    @Override protected void initializeState() {
        // This is a hack!! For some reason this is the only way I can get styles
        // to work for ToolTips. I should be able to just choose the appropriate
        // css class decalratively, but that doesn't work and no one seems to
        // know why. This is a workaround.
        URL url = getClass().getClassLoader().getResource("org/noroomattheinn/styles/tooltip.css");
        app.stage.getScene().getStylesheets().add(url.toExternalForm());
        
        prepSeries();
        loadExistingData();
        // Register for additions to the list - Handle the new list on the JFX
        // thread to avoid ConcurrentModificationExceptions in the series list
        App.addTracker(vtData.lastStoredChargeState, chargeHandler);
        App.addTracker(vtData.lastStoredStreamState, streamHandler);

        
        setGap();
        prefs.ignoreGraphGaps.addListener(new ChangeListener<Boolean>() {
            @Override public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) {
                setGap(); lineChart.refreshChart();
            }
        });
        prefs.graphGapTime.addListener(new ChangeListener<Number>() {
            @Override public void changed(ObservableValue<? extends Number> ov, Number t, Number t1) {
                setGap(); lineChart.refreshChart();
            }
        });
    }
    
    @Override protected void activateTab() { }

    @Override protected void refresh() { }

    @Override protected void fxInitialize() {
        nowButton.setVisible(false);
        refreshButton.setDisable(true);
        refreshButton.setVisible(false);
        progressIndicator.setVisible(false);
        chart = new TimeBasedChart(root, readout);
        lineChart = chart.getChart();
        createContextMenu();
        showItemList(false);
        root.getChildren().add(0, lineChart);
        nowButton.setVisible(true);
    }

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods for attaching a ContextMenu to the LineChart
 * 
 *----------------------------------------------------------------------------*/
    
    private void createContextMenu() {
        final ContextMenu contextMenu = new ContextMenu();
        final ToggleGroup toggleGroup = new ToggleGroup();

        displayLinesMI = new RadioMenuItem("Display Only Lines");
        displayLinesMI.setOnAction(displayMIHandler);
        displayLinesMI.setSelected(displayLines);
        displayLinesMI.setToggleGroup(toggleGroup);
        displayMarkersMI = new RadioMenuItem("Display Only Markers");
        displayMarkersMI.setOnAction(displayMIHandler);
        displayMarkersMI.setSelected(displayMarkers);
        displayMarkersMI.setToggleGroup(toggleGroup);
        displayBothMI = new RadioMenuItem("Display Both");
        displayBothMI.setOnAction(displayMIHandler);
        displayBothMI.setSelected(displayMarkers);
        displayBothMI.setToggleGroup(toggleGroup);

        if (displayLines && displayMarkers) {   displayBothMI.setSelected(true);
        } else if (displayLines) {              displayLinesMI.setSelected(true);
        } else if (displayMarkers) {            displayMarkersMI.setSelected(true);
        }

        contextMenu.getItems().addAll(displayLinesMI, displayMarkersMI, displayBothMI);
        chart.addContextMenu(contextMenu);
    }
    
    private EventHandler<ActionEvent> displayMIHandler = new EventHandler<ActionEvent>() {
        @Override
        public void handle(ActionEvent event) {
            RadioMenuItem target = (RadioMenuItem) event.getTarget();
            if (target == displayLinesMI) {
                displayLines = true;
                displayMarkers = false;
            } else if (target == displayMarkersMI) {
                displayLines = false;
                displayMarkers = true;
            } else {
                displayLines = true;
                displayMarkers = true;
            }

            reflectDisplayOptions();

            prefs.storage().putBoolean(vinKey("DISPLAY_LINES"), displayLines);
            prefs.storage().putBoolean(vinKey("DISPLAY_MARKERS"), displayMarkers);
        }
    };

    private void reflectDisplayOptions() {        
        if (displayMarkers && displayLines) {
            displayBothMI.setSelected(true);
            lineChart.setDisplayMode(VTLineChart.DisplayMode.Both);
        } else if (displayLines) {
            displayLinesMI.setSelected(true);
            displayBothMI.setSelected(false);
            lineChart.setDisplayMode(VTLineChart.DisplayMode.LinesOnly);
        } else {
            displayMarkersMI.setSelected(true);
            displayBothMI.setSelected(false);
            lineChart.setDisplayMode(VTLineChart.DisplayMode.MarkersOnly);
        }
    }

    private void setGap() {
        lineChart.setIgnoreGap(prefs.ignoreGraphGaps.get(),
                               prefs.graphGapTime.get());
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Loading existing list into the Series
 * 
 *----------------------------------------------------------------------------*/
        
    private void loadExistingData() {
        Map<Long,Row> rows = vtData.getAllLoadedRows();
        Map<String,ObservableList<XYChart.Data<Number,Number>>> typeToList = new HashMap<>();
        
        for (String type : typeToSeries.keySet()) {
            ObservableList<XYChart.Data<Number,Number>> data =
                    FXCollections.<XYChart.Data<Number,Number>>observableArrayList();
            typeToList.put(type, data);
        }
        
        
        DefaultedHashMap<String,Long> lastTimeForType = new DefaultedHashMap<>(0L);
        DefaultedHashMap<String,Double> lastValForType = new DefaultedHashMap<>(0.0);
        for (Row row : rows.values()) {
            long time = row.timestamp;
            long bit = 1;
            for (int i = 0; i < row.values.length; i++) {
                if (row.includes(bit)) {
                    String type = VTData.schema.columnNames[i];
                    VTSeries vts = typeToSeries.get(type);
                    if (vts != null) {  // It's a column that we're graphing
                        double value = row.values[i];
                        ObservableList<XYChart.Data<Number,Number>> list = typeToList.get(type);
                        // Don't overload the graph. Make sure that samples are
                        // At least 5 seconds apart unless they represent a huge 
                        // swing in values: greater than 50%
                        if (time - lastTimeForType.get(type) >= 5 * 1000 ||
                            Utils.percentChange(value, lastValForType.get(type)) > 0.5) {
                            if (type.equals(VTData.SpeedKey) && add0Speed(time, value)) {
                                vts.addToData(list, time - (5 * 1000), 0);
                            }
                            vts.addToData(list, time, value);
                            lastTimeForType.put(type, time);
                            lastValForType.put(type, value);
                        }
                    }
                }
                bit = bit << 1;
            }
        }
        
        for (Map.Entry<String,VTSeries> entry : typeToSeries.entrySet()) {
            VTSeries vts = entry.getValue();
            String type = entry.getKey();
            vts.setData(typeToList.get(type));
        }
        
        lineChart.applySeriesToChart();
        restoreLastSettings();
    }
        
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Listen for and add new list points to the graph
 * 
 *----------------------------------------------------------------------------*/
    
    private void addElement(final VTSeries series, final long time, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) value = 0;
        series.addToSeries(time, value);
    }
    
    private final Runnable chargeHandler = new Runnable() {
        @Override public void run() {
            ChargeState cs = vtData.lastStoredChargeState.get();
            addElement(typeToSeries.get(VTData.VoltageKey), cs.timestamp, cs.chargerVoltage);
            addElement(typeToSeries.get(VTData.CurrentKey), cs.timestamp, cs.chargerActualCurrent);
            addElement(typeToSeries.get(VTData.EstRangeKey), cs.timestamp, cs.range);
            addElement(typeToSeries.get(VTData.SOCKey), cs.timestamp, cs.batteryPercent);
            addElement(typeToSeries.get(VTData.ROCKey), cs.timestamp, cs.chargeRate);
        }
    };
    
    private final Runnable streamHandler = new Runnable() {
        @Override public void run() {
            StreamState ss = vtData.lastStoredStreamState.get();
            addElement(typeToSeries.get(VTData.PowerKey), ss.timestamp, ss.power);
            
            if (add0Speed(ss.timestamp, ss.speed)) {
                addElement(typeToSeries.get(VTData.SpeedKey), ss.timestamp  - (5 * 1000), 0);                
            }
            addElement(typeToSeries.get(VTData.SpeedKey), ss.timestamp, ss.speed);
        }
    };
    
    private long lastTime = 0;
    private double lastSpeed = -1.0;
    private boolean add0Speed(long curTime, double curSpeed) {
        boolean add0 = false;
        if (lastSpeed == 0.0 && curSpeed != 0.0) {
            if (curTime - lastTime > 60 * 1000L) {
                add0 = true;
            }
        }
        lastTime = curTime;
        lastSpeed = curSpeed;
        return add0;
    }
    
}
