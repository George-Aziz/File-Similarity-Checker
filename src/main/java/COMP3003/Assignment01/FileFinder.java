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

/*************************************************************************************
 * Author: George Aziz
 * Purpose: Finds all files & compares each file with another to find similarities
 * Date Last Modified: 29/08/2021
 *************************************************************************************/
public class FileFinder
{
    private ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<String>(100);
    private final Object mutex = new Object();
    private static final String POISON = new String();
    private ExecutorService consExec;

    private UserInterface ui;
    private String searchPath;
    private double totalCount;
    private double totalJobsNeeded;
    private double jobCount;

    public FileFinder(UserInterface ui, String searchPath, int threadCount)
    {
        this.searchPath = searchPath;
        this.ui = ui;
        totalCount = -1;
        jobCount = 0;
        totalJobsNeeded = 0;
        consExec = Executors.newFixedThreadPool(threadCount);
    }

    Runnable consumerTask = () ->
    {
            LinkedList<String> fileList = new LinkedList<>();
            while(true) {
                try {
                    String fileStr = queue.take();
                    if (fileStr == POISON) {
                        break;
                    } else {
                        fileList.add(fileStr);
                        for (String curFile : fileList) {
                            consExec.execute(() ->
                            {
                                try {
                                    if (!curFile.equals(fileStr)) {
                                        byte[] file1 = Files.readAllBytes(new File(curFile).toPath());
                                        byte[] file2 = Files.readAllBytes(new File(fileStr).toPath());
                                        double similarity = similarityCheck(file1, file2);
                                        ComparisonResult newResult = new ComparisonResult(curFile, fileStr, similarity);
                                        synchronized (mutex) {
                                            resultOutput(newResult);
                                            progressChecker();
                                        }
                                    }
                                } catch (IOException ex) { /*Ignore it and move on*/ }
                            });
                        }
                    }
                }
                catch (InterruptedException ex) {
                    //Nothing to do if thread gets interrupted other than end gracefully and shut down executor
                    consExec.shutdownNow();
                    break;
                }
            }
        consExec.shutdown();
    };

    Runnable producerTask = () ->
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
                                        file.toString().endsWith(".cs")) {
                                    queue.put(file.toString());
                                    totalCount = totalCount + 1;
                                    totalJobsNeeded += totalCount;
                                }
                            }
                        } catch (InterruptedException e) { }
                        return FileVisitResult.CONTINUE; //Continues
                    }
                });
            }
            catch(IOException e)
            {
                Platform.runLater(() ->  // Runnable to re-enter GUI thread
                {
                    // This error handling is a bit quick-and-dirty, but it will suffice here.
                    ui.showError(e.getClass().getName() + ": " + e.getMessage());
                });
            }
            finally
            {
                queue.put(POISON);
                ui.endProdThread();
            }
        }
        catch(InterruptedException ex) { /*Nothing to do if thread gets interrupted other than end gracefully*/ }
    };

    private void resultOutput(ComparisonResult newResult) throws IOException
    {
        if (newResult.getSimilarity() >= 0.50) {
            Platform.runLater(() ->  // Runnable to re-enter GUI thread
            {
                ui.addResult(newResult);
            });
        }
        //Appends message to file names "FileSimilarities.csv"
        try (PrintWriter writer = new PrintWriter(new FileWriter("FileSimilarities.csv", true))) {
            writer.println(newResult.getFile1() + "," + newResult.getFile2() + "," + newResult.getSimilarity());
        }
        jobCount++;
    }

    private void progressChecker()
    {
        double value = jobCount / totalJobsNeeded;
        Platform.runLater(() ->  // Runnable to re-enter GUI thread
        {
            ui.updateProgress(value);
        });
        //System.out.println("Jobs completed: " + jobCount);
        //System.out.println("Total Jobs so Far: " + totalJobsNeeded);
        //System.out.println("Current Progress %: " + value);
        if (value == 1) {
            //Shuts everything down in case they haven't already
            stopAllThreads();
            System.out.println("Execution complete, ending all threads...");
        }
    }

    private double similarityCheck(byte[] file1, byte[] file2)
    {
        int[][] subsolutions = new int[file1.length+1][file2.length+1];
        boolean[][] directionLeft = new boolean[file1.length+1][file2.length+1];

        //Fill first row and first column of subsolutions with zeros
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

    public void stopAllThreads()
    {
        consExec.shutdownNow();
        ui.endConsThread();
        ui.endProdThread();
    }
}
