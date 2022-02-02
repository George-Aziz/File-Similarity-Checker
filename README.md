# File Similarity Checker
**Date created:** 13/08/2021

**Date last modified:** 15/09/2021

## Purpose:
To create a simple GUI to the user that initially asks for the name of a directory, which then proceeds to find all non-empty text  files inside it and all of its subdirectories, and compares each file with each other and produces a similarity score between 0-1, which then gets outputted to the GUI if the similarity is > 0.5, and otherwise all scores are saved into a CSV file. 

The program uses multi-threading via threadpools and blocking queues

## Functionality:
* To compile and run use `./gradlew clean build` first and then `./gradlew run`
