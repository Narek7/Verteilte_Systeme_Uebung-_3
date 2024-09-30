package org.example.demo3;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;

public class BestCaseSimulation extends Application {

    private Cluster cluster;
    private Pane simulationPane;
    private Timeline simulationTimer;
    private boolean isSimulationRunning = false;
    private Button stopContinueButton;
    private Button restartButton;
    private Button quitButton;
    private Slider nodeSlider;
    private Slider durationSlider;
    private Button startSimulationButton;
    private Label messageLabel; // For displaying global messages

    @Override
    public void start(Stage primaryStage) {
        // Create sliders
        Label nodeSliderLabel = new Label("Number of Nodes:");
        nodeSlider = new Slider(3, 10, 5); // Default value is 5 nodes
        nodeSlider.setMajorTickUnit(1);
        nodeSlider.setMinorTickCount(0);
        nodeSlider.setSnapToTicks(true);
        nodeSlider.setShowTickMarks(true);
        nodeSlider.setShowTickLabels(true);

        Label durationSliderLabel = new Label("Simulation Duration (seconds):");
        durationSlider = new Slider(15, 60, 20); // Default value is 20 seconds
        durationSlider.setMajorTickUnit(5);
        durationSlider.setMinorTickCount(4);
        durationSlider.setSnapToTicks(true);
        durationSlider.setShowTickMarks(true);
        durationSlider.setShowTickLabels(true);

        // Create Start Simulation button
        startSimulationButton = new Button("Start Simulation");
        startSimulationButton.setOnAction(e -> startSimulation());

        // Layout for sliders and start button
        VBox controlBox = new VBox(10);
        controlBox.setAlignment(Pos.CENTER);
        controlBox.setPadding(new Insets(10));
        controlBox.getChildren().addAll(
                nodeSliderLabel, nodeSlider,
                durationSliderLabel, durationSlider,
                startSimulationButton
        );

        // Create buttons for simulation controls
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

        // Create simulation pane
        simulationPane = new Pane();
        simulationPane.setPrefSize(800, 600);

        // Create a Label for global messages
        messageLabel = new Label();
        messageLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: blue;");
        messageLabel.setAlignment(Pos.CENTER);
        messageLabel.setWrapText(true); // Allow text to wrap

        // Main layout adjustments
        BorderPane mainLayout = new BorderPane();

        // Combine controlBox and messageLabel
        VBox topBox = new VBox(10);
        topBox.getChildren().addAll(controlBox, messageLabel);
        mainLayout.setTop(topBox);

        mainLayout.setCenter(simulationPane);
        mainLayout.setBottom(simulationControlBox);

        Scene scene = new Scene(mainLayout, 800, 1000);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Raft Simulation - Best Case");
        primaryStage.show();
    }

    private void startSimulation() {
        int numNodes = (int) nodeSlider.getValue();
        int simulationDuration = (int) durationSlider.getValue();

        // Clear previous simulation if any
        simulationPane.getChildren().clear();

        // Create and start the cluster
        cluster = new Cluster(numNodes, simulationPane, false, messageLabel); // Pass messageLabel
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

        // Hide controlBox (sliders and start button) and show simulation controls
        ((BorderPane) startSimulationButton.getScene().getRoot()).getTop().setVisible(false);
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
            // Stop the simulation
            cluster.pause();
            simulationTimer.pause();
            isSimulationRunning = false;
            stopContinueButton.setText("Continue");
        } else {
            // Continue the simulation
            cluster.resume();
            simulationTimer.play();
            isSimulationRunning = true;
            stopContinueButton.setText("Stop");
        }
    }

    private void restartSimulation() {
        stopSimulation();

        // Show controlBox (sliders and start button) and hide simulation controls
        ((BorderPane) startSimulationButton.getScene().getRoot()).getTop().setVisible(true);
        HBox simulationControlBox = (HBox) ((BorderPane) startSimulationButton.getScene().getRoot()).getBottom();
        simulationControlBox.setVisible(false);

        // Clear the message label
        Platform.runLater(() -> messageLabel.setText(""));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
