package COMP3003.Assignment01;

import javafx.application.Platform;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.concurrent.*;

/********************************************************************************************
 * Author: George Aziz
 * Purpose: Finds all files & compares each file with another to find similarities
 * Date Last Modified: 01/09/2021
 * NOTE: similarityCheck() algorithm has been provided and used from Assignment Specification
 ********************************************************************************************/
public class FileChecker {
    //Thread Fields
    private ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<String>(50);
    private final Object mutex = new Object();
    private static final String POISON = new String();
    private ExecutorService consExec;
    //Other Fields
    private UserInterface ui;
    private String searchPath;
    private double producerCount;
    private double totalJobs;
    private double jobCount;
    private boolean nonEmptyFileExist;

    //Constructor
    public FileChecker(UserInterface ui, String searchPath, int threadCount)
    {
        this.searchPath = searchPath;
        this.ui = ui;
        producerCount = -1;
        jobCount = 0;
        totalJobs = 0;
        consExec = Executors.newFixedThreadPool(threadCount);
        nonEmptyFileExist = false;
    }

    //Task that goes through directory tree and adds all files into blocking queue
    public void producerTask()
    {
        try {
            try {
                // Recurse through the directory tree
                Files.walkFileTree(Paths.get(searchPath), new SimpleFileVisitor<Path>()
                {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    {
                        try {
                            //Only adds file if it is not empty
                            if (file.toFile().length() > 0) {
                                if (file.toString().endsWith(".txt") ||
                                        file.toString().endsWith(".md") ||
                                        file.toString().endsWith(".java") ||
                                        file.toString().endsWith(".cs") ||
                                        file.toString().endsWith(".csv")) {
                                    nonEmptyFileExist = true;
                                    queue.put(file.toString());
                                    producerCount = producerCount + 1;
                                    totalJobs += producerCount;
                                }
                            }
                        } catch (InterruptedException e) { }
                        return FileVisitResult.CONTINUE; //Continues
                    }
                });
            } catch(IOException ex) {
                Platform.runLater(() ->  // Runnable to re-enter GUI thread
                {
                    ui.showError(ex.getClass().getName() + ": " + ex.getMessage());
                });
            } finally { //Puts POISON value for Consumer thread and signals to GUI that the thread has ended
                queue.put(POISON);
                ui.endProdThread();
            }
        }
        catch(InterruptedException ex) { /*Nothing to do if thread gets interrupted other than end gracefully*/ }
    }

    //Task that takes files from blocking queue and puts it into list that is used for further comparisons
    //Task also executes similarity check + progress bar update after it in a thread pool
    public void consumerTask()
    {
        LinkedList<String> fileList = new LinkedList<>();
        while (true) {
            try {
                String fileStr = queue.take();
                if (fileStr == POISON) {
                    if(nonEmptyFileExist) { //If a file exists, the thread pool will handle stopping all threads
                        ui.endConsThread();
                    } else { //If no file to be processed then end all threads if they haven't already
                        stopAllThreads();
                    }
                    break;
                } else { //New file found
                    //File gets added as that is what stores all previous files before current
                    fileList.add(fileStr);
                    for (String curFile : fileList) { //For each file, execute task into thread pool
                        consExec.execute(() ->
                        {
                            try {
                                if (!curFile.equals(fileStr)) { //Can't do a check on its own
                                    //Reads all bytes from each file and calculates similarity
                                    byte[] file1 = Files.readAllBytes(new File(curFile).toPath());
                                    byte[] file2 = Files.readAllBytes(new File(fileStr).toPath());
                                    double similarity = similarityCheck(file1, file2);
                                    ComparisonResult newResult = new ComparisonResult(curFile, fileStr, similarity);
                                    synchronized (mutex) { //Synchronises File writing + GUI Output
                                        resultOutput(newResult); //Writes to file and output to GUI if above 50%
                                        progressChecker(); //Checks current progress and updated progress bar
                                    }
                                }
                            } catch (IOException ex) {
                                Platform.runLater(() ->  // Runnable to re-enter GUI thread
                                {
                                    ui.showError(ex.getClass().getName() + ": " + ex.getMessage());
                                });
                            }
                        });
                    }
                }
            } catch (InterruptedException ex) {
                //Nothing to do if thread gets interrupted other than end gracefully and shut down executor
                consExec.shutdownNow();
                break;
            }
        }
    }

    //Outputs result either to CSV and/or GUI if above 50%
    private void resultOutput(ComparisonResult newResult) throws IOException
    {
        if (newResult.getSimilarity() >= 0.50) { //Output to GUI if above 50%
            Platform.runLater(() ->  // Runnable to re-enter GUI thread
            {
                ui.addResult(newResult);
            });
        }
        //Appends message to file names "FileSimilarities.csv" regardless of %
        try (PrintWriter writer = new PrintWriter(new FileWriter("FileSimilarities.csv", true))) {
            writer.println(newResult.getFile1() + "," + newResult.getFile2() + "," + newResult.getSimilarity());
        }
        jobCount++; //Increases count of how many jobs have been processed
    }

    //Updates GUI with current progress of program
    private void progressChecker()
    {
        double value = jobCount / totalJobs;
        Platform.runLater(() ->  // Runnable to re-enter GUI thread
        {
            ui.updateProgress(value);
        });
        if (value == 1) { //Program has finished with no more tasks to be processed
            //Shuts everything down in case they haven't already
            System.out.println("Execution complete, ending all threads...");
            stopAllThreads();
        }
    }

    //LCS Algorithm to retrieve similarities between two files
    private double similarityCheck(byte[] file1, byte[] file2)
    {
        int[][] subsolutions = new int[file1.length+1][file2.length+1];
        boolean[][] directionLeft = new boolean[file1.length+1][file2.length+1];
        for(int i = 0; i < file1.length; i++) { subsolutions[i][0] = 0; }
        for(int i = 0; i < file2.length; i++) { subsolutions[0][i] = 0; }
        for(int i = 1; i <= file1.length; i++) {
            for(int j = 1 ; j <= file2.length; j++) {
                if(file1[i-1] == file2[j-1]) {
                    subsolutions[i][j] = subsolutions[i - 1][j - 1] + 1;
                } else if (subsolutions[i-1][j] > subsolutions[i][j-1]) {
                    subsolutions[i][j] = subsolutions[i-1][j];
                    directionLeft[i][j] = true;
                } else {
                    subsolutions[i][j] = subsolutions[i][j-1];
                    directionLeft[i][j] = false;
                }
            }
        }
        double matches = 0;
        int i = file1.length;
        int j = file2.length;
        while(i > 0 && j >0)
        {
            if(file1[i - 1] == file2[j - 1])
            {
                matches += 1;
                i -= 1;
                j -= 1;
            }
            else if (directionLeft[i][j]) { i -= 1; }
            else { j -= 1; }
        }
        return (matches * 2) / (file1.length + file2.length);
    }

    //Stops all threads and shuts down thread pool
    public void stopAllThreads()
    {
        //Only ends threads if thread pool hasn't ended which means program was still going
        if(consExec != null && queue != null) {
            consExec.shutdownNow(); //Thread pool end
            ui.endConsThread();
            ui.endProdThread();
            ui.enableGUI(); //Re-enables button to compare for another comaprison to start
            consExec = null;
            queue = null;
        }
    }
}
