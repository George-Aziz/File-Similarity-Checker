package COMP3003.Assignment01;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.*;
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
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**************************************************************************************
 * Author: George Aziz
 * Purpose: Basic GUI (JavaFX) to compare files
 * Date Last Modified: 29/08/2021
 * NOTE: The JavaFX code used within this class has been taken from provided demo code
 * and slightly modified to my needs
 **************************************************************************************/
public class UserInterface {

    //Table & List used for output to GUI for similarities over 50%
    private List<ComparisonResult> newResults = new LinkedList<>();
    private TableView<ComparisonResult> resultTable = new TableView<>();
    private ProgressBar progressBar = new ProgressBar();
    private Thread producerThread = null;
    private Thread consumerThread = null;

    private FileFinder fileFinder = null;

    public UserInterface(){ }

    //Start of GUI - Contains all setup for JavaFX
    public void start(Stage stage)
    {
        stage.setTitle("File Similarity Checker");
        stage.setMinWidth(600);

        // Create toolbar
        Button compareBtn = new Button("Compare...");
        Button stopBtn = new Button("Stop");
        Button clearBtn = new Button("Clear");
        Text threadPoolCountLabel = new Text("Thread Pool Count:");
        TextField threadPoolCountBox = new TextField("3");
        ToolBar toolBar = new ToolBar(compareBtn, stopBtn, clearBtn, threadPoolCountLabel, threadPoolCountBox);

        // Set up button event handlers.
        compareBtn.setOnAction(event -> crossCompare(stage, threadPoolCountBox));
        stopBtn.setOnAction(event -> stopComparison());
        clearBtn.setOnAction(event -> clearGui());

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

    //Method call to begin file comparisons
    private void crossCompare(Stage stage, TextField threadPoolCount)
    {
        if(producerThread == null && consumerThread == null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter("FileSimilarities.csv", false))) {
                writer.flush();
            } catch (IOException e) { System.out.println("Unable to clear out csv file"); }
            progressBar.setProgress(0.0);
            DirectoryChooser dc = new DirectoryChooser();
            dc.setInitialDirectory(new File("."));
            dc.setTitle("Choose directory");
            File directory = dc.showDialog(stage);
            if(directory!= null) {
                try {
                    //After directory has been selected ...
                    System.out.println("Comparing files within " + directory.getPath() + "...");
                    fileFinder = new FileFinder(this, directory.getPath(), Integer.parseInt(threadPoolCount.getText()));
                    producerThread = new Thread(fileFinder.producerTask, "Producer-Thread");
                    consumerThread = new Thread(fileFinder.consumerTask, "Consumer-Thread");
                    producerThread.start();
                    consumerThread.start();
                } catch(NumberFormatException ex) {
                    showError("Please ensure you've inputted an integer (Whole Number) into thread count text box");
                }
            }
        }
    }

    public void addResult(ComparisonResult newResult)
    {
        newResults.add(newResult);
        resultTable.getItems().setAll(newResults);
    }

    public void updateProgress(double progress)
    {
        progressBar.setProgress(progress);
    }

    public void endProdThread()
    {
        if(producerThread != null) {
            producerThread.interrupt();
            producerThread = null;
            System.out.println("Producer thread ending...");
        }
    }

    public void endConsThread()
    {
        if(consumerThread != null) {
            consumerThread.interrupt();
            consumerThread = null;
            System.out.println("Consumer threads ending...");
        }
    }

    private void clearGui()
    {
        //Updates GUI to initial state
        resultTable.getItems().clear();
        progressBar.setProgress(0.0);
    }

    private void stopComparison()
    {
        fileFinder.stopAllThreads();
        System.out.println("Stopping comparison...");
    }

    public void showError(String message)
    {
        Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.CLOSE);
        a.setResizable(true);
        a.showAndWait();
    }
}
