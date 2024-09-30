package org.example.demo3.splitvote;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class SplitVoteSimulation extends Application {

    private SplitVoteCluster cluster;
    private Pane simulationPane;
    private Timeline simulationTimer;
    private boolean isSimulationRunning = false;
    private Button stopContinueButton;
    private Button restartButton;
    private Button quitButton;
    private Slider nodeSlider;
    private Slider durationSlider;
    private Button startSimulationButton;
    private Label messageLabel; // For global messages
    private Label disclaimerLabel; // Disclaimer label

    @Override
    public void start(Stage primaryStage) {
        // Create the sliders
        Label nodeSliderLabel = new Label("Number of Nodes:");
        nodeSlider = new Slider(4, 5, 4); // Allow only 4 or 5 nodes
        nodeSlider.setMajorTickUnit(1);
        nodeSlider.setMinorTickCount(0);
        nodeSlider.setSnapToTicks(true);
        nodeSlider.setShowTickMarks(true);
        nodeSlider.setShowTickLabels(true);

        Label durationSliderLabel = new Label("Simulation Duration (Seconds):");
        durationSlider = new Slider(30, 300, 60); // Default: 60 seconds
        durationSlider.setMajorTickUnit(30);
        durationSlider.setMinorTickCount(2);
        durationSlider.setSnapToTicks(true);
        durationSlider.setShowTickMarks(true);
        durationSlider.setShowTickLabels(true);

        // Create the start button
        startSimulationButton = new Button("Start Split Vote Simulation");
        startSimulationButton.setOnAction(e -> startSimulation());

        // Disclaimer label
        disclaimerLabel = new Label("Disclaimer: For simplicity, only 4 or 5 nodes are allowed to demonstrate the split vote scenario.");
        disclaimerLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");
        disclaimerLabel.setWrapText(true);
        disclaimerLabel.setPadding(new Insets(10, 0, 0, 0));

        // Layout for sliders and start button
        VBox controlBox = new VBox(10);
        controlBox.setAlignment(Pos.CENTER);
        controlBox.setPadding(new Insets(10));
        controlBox.getChildren().addAll(
                nodeSliderLabel, nodeSlider,
                durationSliderLabel, durationSlider,
                startSimulationButton,
                disclaimerLabel
        );

        // Create control buttons for the simulation
        stopContinueButton = new Button("Stop");
        stopContinueButton.setOnAction(e -> stopOrContinueSimulation());

        restartButton = new Button("Restart");
        restartButton.setOnAction(e -> restartSimulation());

        quitButton = new Button("Quit");
        quitButton.setOnAction(e -> {
            stopSimulation();
            Platform.exit();
        });

        HBox simulationControlBox = new HBox(10);
        simulationControlBox.setAlignment(Pos.CENTER);
        simulationControlBox.setPadding(new Insets(10));
        simulationControlBox.getChildren().addAll(stopContinueButton, restartButton, quitButton);
        simulationControlBox.setVisible(false); // Hide until simulation starts

        // Create the simulation area
        simulationPane = new Pane();
        simulationPane.setPrefSize(800, 400);

        // Create a label for global messages
        messageLabel = new Label();
        messageLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: blue;");
        messageLabel.setAlignment(Pos.CENTER);
        messageLabel.setWrapText(true); // Allow text wrapping

        // Main layout setup
        BorderPane mainLayout = new BorderPane();

        // Combine controlBox and messageLabel
        VBox topBox = new VBox(10);
        topBox.getChildren().addAll(controlBox, messageLabel);
        mainLayout.setTop(topBox);

        mainLayout.setCenter(simulationPane);
        mainLayout.setBottom(simulationControlBox);

        Scene scene = new Scene(mainLayout, 800, 1000);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Raft Simulation - Split Vote Scenario");
        primaryStage.show();
    }

    private void startSimulation() {
        int numNodes = (int) nodeSlider.getValue();
        int simulationDuration = (int) durationSlider.getValue();

        // Ensure the number of nodes is either 4 or 5
        if (numNodes < 4 || numNodes > 5) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Invalid Node Count");
            alert.setHeaderText(null);
            alert.setContentText("The number of nodes must be either 4 or 5.");
            alert.showAndWait();
            return;
        }

        // Clear the simulation pane
        simulationPane.getChildren().clear();

        // Create and start the cluster
        cluster = new SplitVoteCluster(numNodes, simulationPane, messageLabel);
        cluster.start();

        isSimulationRunning = true;

        // Set up the simulation timer
        simulationTimer = new Timeline(new KeyFrame(Duration.seconds(simulationDuration), e -> {
            stopSimulation();
        }));
        simulationTimer.setCycleCount(1);
        simulationTimer.play();

        // Update UI elements
        stopContinueButton.setText("Stop");
        stopContinueButton.setDisable(false);
        restartButton.setDisable(false);

        // Hide controlBox (Sliders and Start button) and show control buttons
        ((VBox) ((BorderPane) startSimulationButton.getScene().getRoot()).getTop()).setVisible(false);
        HBox simulationControlBox = (HBox) ((BorderPane) startSimulationButton.getScene().getRoot()).getBottom();
        simulationControlBox.setVisible(true);
    }

    private void stopSimulation() {
        if (cluster != null) {
            cluster.stop();
        }
        if (simulationTimer != null) {
            simulationTimer.stop();
        }
        isSimulationRunning = false;
        stopContinueButton.setDisable(true);
        stopContinueButton.setText("Stop");
    }

    private void stopOrContinueSimulation() {
        if (isSimulationRunning) {
            // Pause the simulation
            cluster.pause();
            simulationTimer.pause();
            isSimulationRunning = false;
            stopContinueButton.setText("Continue");
        } else {
            // Resume the simulation
            cluster.resume();
            simulationTimer.play();
            isSimulationRunning = true;
            stopContinueButton.setText("Stop");
        }
    }

    private void restartSimulation() {
        stopSimulation();

        // Show controlBox (Sliders and Start button) and hide control buttons
        ((VBox) ((BorderPane) startSimulationButton.getScene().getRoot()).getTop()).setVisible(true);
        HBox simulationControlBox = (HBox) ((BorderPane) startSimulationButton.getScene().getRoot()).getBottom();
        simulationControlBox.setVisible(false);

        // Clear the message label
        Platform.runLater(() -> messageLabel.setText(""));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
