package COMP3003.Assignment01;

import javafx.application.Application;
import javafx.stage.Stage;

/******************************************************************************
 * Author: George Aziz
 * Purpose: Beginning of the program that starts the UI Class (JavaFX GUI)
 * Date Last Modified: 24/08/2021
 *****************************************************************************/
public class FileSimilarityChecker extends Application {
    public static void main(String[] args)
    {
        launch(args);
    }

    @Override
    public void start(Stage stage)
    {
        new UserInterface().start(stage); //Starts the UI
    }
}
