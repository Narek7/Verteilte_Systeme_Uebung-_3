package org.example.demo3;

import javafx.animation.KeyFrame;
import javafx.animation.PathTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Cluster {
    private List<Node> nodes = new ArrayList<>();
    private Pane pane;

    private Node leaderNode;

    private List<Timeline> timelines = new ArrayList<>();

    private boolean isSplitVote;

    private Label messageLabel; // For displaying global messages

    public Cluster(int numNodes, Pane pane, boolean isSplitVote, Label messageLabel) {
        this.pane = pane;
        this.isSplitVote = isSplitVote;
        this.messageLabel = messageLabel;

        // Arrange nodes in a circle
        double centerX = pane.getPrefWidth() / 2;
        double centerY = pane.getPrefHeight() / 2;
        double radius = Math.min(centerX, centerY) - 50;

        for (int i = 0; i < numNodes; i++) {
            double angle = 2 * Math.PI * i / numNodes;
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
            Node node = new Node(i + 1, this, x, y);
            nodes.add(node);

            // Add node visualization to pane
            pane.getChildren().addAll(node.getCircle(), node.getLabel(), node.getDownText());
        }

        // Draw network lines (dashed lines)
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Line line = new Line();
                line.setStartX(nodes.get(i).getCircle().getCenterX());
                line.setStartY(nodes.get(i).getCircle().getCenterY());
                line.setEndX(nodes.get(j).getCircle().getCenterX());
                line.setEndY(nodes.get(j).getCircle().getCenterY());
                line.getStrokeDashArray().addAll(10d, 5d);
                line.setStroke(Color.GRAY);
                pane.getChildren().add(0, line); // Add behind nodes
            }
        }

        // Handle scenarios
        if (isSplitVote) {
            prepareSplitVoteScenario();
        } else {
            prepareBestCaseScenario();
        }
    }

    private void prepareBestCaseScenario() {
        // Randomly select one node to have the shortest election timeout
        Random rand = new Random();
        Node candidateNode = nodes.get(rand.nextInt(nodes.size()));

        long candidateTimeout = 5000; // 5 seconds
        long minOtherTimeout = 8000;   // 8 seconds
        long maxOtherTimeout = 10000;  // 10 seconds

        for (Node node : nodes) {
            if (node == candidateNode) {
                node.setElectionTimeout(candidateTimeout);
                node.setLastHeartbeat(System.currentTimeMillis());
                log("Node n" + node.getId() + " will become candidate first with a timeout of " + candidateTimeout + "ms.");
            } else {
                long timeout = minOtherTimeout + rand.nextInt((int) (maxOtherTimeout - minOtherTimeout));
                node.setElectionTimeout(timeout);
                node.setLastHeartbeat(System.currentTimeMillis());
                log("Node n" + node.getId() + " has an election timeout of " + timeout + "ms.");
            }
        }
    }

    private void prepareSplitVoteScenario() {
        // Implementation for split vote scenario (not needed for best case)
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Node> getActiveNodes() {
        List<Node> activeNodes = new ArrayList<>();
        for (Node node : nodes) {
            if (!node.isDown()) {
                activeNodes.add(node);
            }
        }
        return activeNodes;
    }

    public void nodeTimeoutExpired(Node node) {
        node.nodeTimeoutExpired(); // Delegate to node's method
    }

    /**
     * Synchronized method to attempt setting the leader.
     * Returns true if the leader was successfully set, false if a leader already exists.
     */
    public synchronized boolean attemptToSetLeader(Node node) {
        if (leaderNode == null) {
            leaderNode = node;
            return true;
        }
        return false;
    }

    /**
     * Synchronized method to get the current leader.
     */
    public synchronized Node getLeaderNode() {
        return leaderNode;
    }

    /**
     * Synchronized method to set the leader node.
     */
    public synchronized void setLeaderNode(Node node) {
        this.leaderNode = node;
    }

    public void sendAppendEntries() {
        // Leader sends AppendEntries messages periodically
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            Node currentLeader;
            synchronized (this) {
                currentLeader = leaderNode;
            }
            if (currentLeader != null) {
                currentLeader.log("Leader n" + currentLeader.getId() + " sends AppendEntries to followers.");
                for (Node node : getActiveNodes()) {
                    if (node != currentLeader) {
                        sendMessage(currentLeader, node, "AppendEntries", Color.PINK);
                    }
                }
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        // Keep track of timelines to pause/resume
        timelines.add(timeline);
    }

    public void start() {
        for (Node node : nodes) {
            Thread t = new Thread(node);
            t.setDaemon(true); // Allow JVM to exit if main thread terminates
            t.start();
        }
    }

    public void pause() {
        for (Node node : nodes) {
            node.pause();
        }
        for (Timeline timeline : timelines) {
            timeline.pause();
        }
    }

    public void resume() {
        for (Node node : nodes) {
            node.resume();
        }
        for (Timeline timeline : timelines) {
            timeline.play();
        }
    }

    public void stop() {
        for (Node node : nodes) {
            node.stop();
        }
        for (Timeline timeline : timelines) {
            timeline.stop();
        }
        timelines.clear();
    }

    public void log(String message) {
        // Append messages to the GUI's messageLabel
        Platform.runLater(() -> {
            messageLabel.setText(message);
        });
    }

    public void sendMessage(Node fromNode, Node toNode, String messageType, Color color) {
        if (fromNode.isDown() || toNode.isDown()) {
            return; // Do not send messages to or from down nodes
        }
        Platform.runLater(() -> {
            // Create message representation
            Circle messageCircle = new Circle(10, color);
            Text messageLabelText = new Text(messageType);
            messageLabelText.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
            messageLabelText.setX(-5);
            messageLabelText.setY(5);

            Group messageGroup = new Group(messageCircle, messageLabelText);

            pane.getChildren().add(messageGroup);

            // Create path for message to move along
            Path path = new Path();
            MoveTo moveTo = new MoveTo(fromNode.getCircle().getCenterX(), fromNode.getCircle().getCenterY());
            LineTo lineTo = new LineTo(toNode.getCircle().getCenterX(), toNode.getCircle().getCenterY());
            path.getElements().addAll(moveTo, lineTo);

            // Animate the message along the path
            PathTransition pt = new PathTransition();
            pt.setDuration(Duration.seconds(1));
            pt.setPath(path);
            pt.setNode(messageGroup);
            pt.setOnFinished(e -> {
                pane.getChildren().remove(messageGroup);
                // After the message arrives, process it
                Message message = new Message(messageType, fromNode.getId(), fromNode, fromNode.getTerm());
                toNode.receiveMessage(message);
            });
            pt.play();
        });
    }

    // Method to update global messages
    public void updateMessage(String message) {
        Platform.runLater(() -> {
            messageLabel.setText(message);
        });
    }
}
