package COMP3003.Assignment01;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**************************************************************************************
 * Author: George Aziz
 * Purpose: Basic GUI (JavaFX) to compare files
 * Date Last Modified: 01/09/2021
 * NOTE: The JavaFX code used within this class has been taken from provided demo code
 * and slightly modified to my needs
 **************************************************************************************/
public class UserInterface {
    //Table used for output to GUI for similarities over 50%
    private TableView<ComparisonResult> resultTable = new TableView<>();
    private ProgressBar progressBar = new ProgressBar();
    private Button compareBtn, stopBtn;
    private Text threadPoolCountLabel;
    private Label threadPoolCount;
    private Slider threadPoolCountSlider;
    private ToolBar toolBar;
    //Thread fields
    private Thread producerThread = null;
    private Thread consumerThread = null;
    private FileChecker fileChecker = null;

    public UserInterface(){ }

    //Start of GUI - Contains all setup for JavaFX
    public void start(Stage stage)
    {
        stage.setTitle("File Similarity Checker");
        stage.setMinWidth(600);

        // Create toolbar
        compareBtn = new Button("Compare...");
        stopBtn = new Button("Stop");
        threadPoolCountLabel = new Text("Thread Pool Count:");
        threadPoolCount = new Label("3");
        threadPoolCountSlider = new Slider(1,10,3);
        toolBar = new ToolBar(compareBtn, stopBtn, threadPoolCountLabel, threadPoolCountSlider, threadPoolCount);

        // Set up button event handlers.
        compareBtn.setOnAction(event -> crossCompare(stage, threadPoolCount));
        stopBtn.setOnAction(event -> stopComparison());

        //Ensures integers are only selected for slider
        threadPoolCountSlider.valueProperty().addListener((obs, oldVal, newVal) ->
                threadPoolCount.textProperty().setValue(String.valueOf((int)threadPoolCountSlider.getValue())));

        TableColumn<ComparisonResult,String> file1Col = new TableColumn<>("File 1");
        TableColumn<ComparisonResult,String> file2Col = new TableColumn<>("File 2");
        TableColumn<ComparisonResult,String> similarityCol = new TableColumn<>("Similarity");

        // The following tells JavaFX how to extract information from a ComparisonResult
        // object and put it into the three table columns.
        file1Col.setCellValueFactory(
                (cell) -> new SimpleStringProperty(cell.getValue().getFile1()) );

        file2Col.setCellValueFactory(
                (cell) -> new SimpleStringProperty(cell.getValue().getFile2()) );

        similarityCol.setCellValueFactory(
                (cell) -> new SimpleStringProperty(
                        String.format("%.1f%%", cell.getValue().getSimilarity() * 100.0)) );

        // Set and adjust table column widths.
        file1Col.prefWidthProperty().bind(resultTable.widthProperty().multiply(0.40));
        file2Col.prefWidthProperty().bind(resultTable.widthProperty().multiply(0.40));
        similarityCol.prefWidthProperty().bind(resultTable.widthProperty().multiply(0.20));

        // Add the columns to the table.
        resultTable.getColumns().add(file1Col);
        resultTable.getColumns().add(file2Col);
        resultTable.getColumns().add(similarityCol);

        // Add the main parts of the UI to the window.
        BorderPane mainBox = new BorderPane();
        // Initialise progressbar
        progressBar.setProgress(0.0);
        progressBar.prefWidthProperty().bind(mainBox.widthProperty().multiply(1.00));

        mainBox.setTop(toolBar);
        mainBox.setCenter(resultTable);
        mainBox.setBottom(progressBar);
        Scene scene = new Scene(mainBox);
        stage.setScene(scene);
        stage.show();
    }

    //Starts comparison execution
    private void crossCompare(Stage stage, Label threadPoolCount)
    {
        if(producerThread == null && consumerThread == null) {
            //Updates GUI and program to initial state
            resultTable.getItems().clear();
            progressBar.setProgress(0.0);
            try (PrintWriter writer = new PrintWriter(new FileWriter("FileSimilarities.csv", false))) {
                writer.flush();
            } catch (IOException e) { System.out.println("Unable to clear out csv file"); }

            //Prompts user to select a directory to start comparisons from
            DirectoryChooser dc = new DirectoryChooser();
            dc.setInitialDirectory(new File("."));
            dc.setTitle("Choose directory");
            File directory = dc.showDialog(stage);
            if(directory!= null) { //Only starts new threads if a directory has been selected by user
                try {
                    //After directory has been selected ...
                    System.out.println("Comparing files within " + directory.getPath() + "...");
                    fileChecker = new FileChecker(this, directory.getPath(), Integer.parseInt(threadPoolCount.getText()));
                    producerThread = new Thread(fileChecker::producerTask, "Producer-Thread");
                    consumerThread = new Thread(fileChecker::consumerTask, "Consumer-Thread");
                    producerThread.start();
                    consumerThread.start();
                    //Disables comparison button to prevent another execution being executed while one is already running
                    compareBtn.setDisable(true);
                    threadPoolCountSlider.setDisable(true);
                } catch(NumberFormatException ex) {
                    showError("Please ensure you've inputted an integer (Whole Number) into thread count text box");
                }
            }
        }
    }

    //Adds a new result into GUI view
    public void addResult(ComparisonResult newResult)
    {
        resultTable.getItems().add(newResult);
    }
    //Sets progress bar to new progress
    public void updateProgress(double progress)
    {
        progressBar.setProgress(progress);
    }

    //Re-enables GUI
    public void enableGUI()
    {
        compareBtn.setDisable(false);
        threadPoolCountSlider.setDisable(false);
    }

    //Ends Production thread, called by Threads
    public void endProdThread()
    {
        if(producerThread != null) {
            producerThread.interrupt();
            producerThread = null;
            System.out.println("Producer thread ending...");
        }
    }

    //Ends Consumer thread, called by Threads
    public void endConsThread()
    {
        if(consumerThread != null) {
            consumerThread.interrupt();
            consumerThread = null;
            System.out.println("Consumer threads ending...");
        }
    }

    //Gets called when "Stop" button is pressed to end all threads
    private void stopComparison()
    {
        if(fileChecker != null) {
            fileChecker.stopAllThreads();
            fileChecker = null;
            System.out.println("Stopping comparison...");
        }
    }

    //GUI Error prompt with error message
    public void showError(String message)
    {
        Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.CLOSE);
        a.setResizable(true);
        a.showAndWait();
    }
}
