package org.example.demo3.splitvote;

import javafx.animation.KeyFrame;
import javafx.animation.PathTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SplitVoteCluster {
    private List<SplitVoteNode> nodes = new ArrayList<>();
    private Pane pane;

    private SplitVoteNode leaderNode;

    private List<Timeline> timelines = new ArrayList<>();

    private Label messageLabel; // For global messages

    public SplitVoteCluster(int numNodes, Pane pane, Label messageLabel) {
        this.pane = pane;
        this.messageLabel = messageLabel;

        // Arrange nodes in a circle
        double centerX = pane.getPrefWidth() / 2;
        double centerY = pane.getPrefHeight() / 2;
        double radius = Math.min(centerX, centerY) - 50;

        for (int i = 0; i < numNodes; i++) {
            double angle = 2 * Math.PI * i / numNodes;
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
            SplitVoteNode node = new SplitVoteNode(i + 1, this, x, y);
            nodes.add(node);

            // Add node to the pane
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
                pane.getChildren().add(0, line); // Add behind the nodes
            }
        }

        // Prepare the split vote scenario
        prepareSplitVoteScenario();
    }

    private void prepareSplitVoteScenario() {
        // If there are 5 nodes, set one node randomly to "down"
        if (nodes.size() == 5) {
            Random rand = new Random();
            SplitVoteNode downNode = nodes.get(rand.nextInt(nodes.size()));
            downNode.setDown(true);
            log("Node n" + downNode.getId() + " has been randomly set to 'down'.");
        }

        // All active nodes start in Term 1 as followers
        for (SplitVoteNode node : getActiveNodes()) {
            node.setTerm(1);
            node.setState("follower");
            node.resetElectionTimeout();
        }

        // Ensure only 2 followers remain active
        List<SplitVoteNode> activeNodes = getActiveNodes();
        if (activeNodes.size() > 2) {
            // Randomly select 2 followers to remain active
            List<SplitVoteNode> followers = new ArrayList<>(activeNodes);
            List<SplitVoteNode> selectedFollowers = selectRandomFollowers(followers, 2);

            // The rest become candidates
            List<SplitVoteNode> candidateNodes = new ArrayList<>(activeNodes);
            candidateNodes.removeAll(selectedFollowers);

            // Set candidates to "candidate" state and have them vote for themselves
            for (SplitVoteNode candidate : candidateNodes) {
                candidate.becomeCandidate();
                log("Node n" + candidate.getId() + " becomes a candidate in Term " + candidate.getTerm() + ".");
            }

            // Distribute votes equally among candidates (each candidate votes for itself)
            distributeVotesEqually(candidateNodes);

            // Initiate leader election by setting one follower's timeout faster
            initiateLeaderElection(selectedFollowers);
        }
    }

    private List<SplitVoteNode> selectRandomFollowers(List<SplitVoteNode> followers, int count) {
        List<SplitVoteNode> selected = new ArrayList<>();
        Random rand = new Random();
        while (selected.size() < count && !followers.isEmpty()) {
            int index = rand.nextInt(followers.size());
            selected.add(followers.get(index));
            followers.remove(index);
        }
        return selected;
    }

    private void distributeVotesEqually(List<SplitVoteNode> candidates) {
        // Ensure all candidates have the same number of votes
        // For simplicity, each candidate receives one vote from itself
        for (SplitVoteNode candidate : candidates) {
            candidate.receiveVote(candidate.getId()); // Each candidate votes for itself
            log("Node n" + candidate.getId() + " receives a vote from itself.");
        }
    }

    private void initiateLeaderElection(List<SplitVoteNode> followers) {
        // Randomly select one follower to have a faster election timeout
        Random rand = new Random();
        SplitVoteNode selectedFollower = followers.get(rand.nextInt(followers.size()));
        selectedFollower.setElectionTimeout(1000); // 1 second for quick timeout
        log("Node n" + selectedFollower.getId() + " has a faster election timeout.");

        // Start a timeline to check for election timeouts
        Timeline electionTimeline = new Timeline(new KeyFrame(Duration.millis(100), e -> {
            for (SplitVoteNode follower : followers) {
                follower.checkElectionTimeout();
            }
        }));
        electionTimeline.setCycleCount(Timeline.INDEFINITE);
        electionTimeline.play();
        timelines.add(electionTimeline);
    }

    public List<SplitVoteNode> getNodes() {
        return nodes;
    }

    public List<SplitVoteNode> getActiveNodes() {
        List<SplitVoteNode> activeNodes = new ArrayList<>();
        for (SplitVoteNode node : nodes) {
            if (!node.isDown()) {
                activeNodes.add(node);
            }
        }
        return activeNodes;
    }

    public void nodeTimeoutExpired(SplitVoteNode node) {
        node.nodeTimeoutExpired(); // Delegate to node method
    }

    /**
     * Synchronized method to set the Leader.
     * Returns true if the Leader was successfully set, false if a Leader already exists.
     */
    public synchronized boolean attemptToSetLeader(SplitVoteNode node) {
        if (leaderNode == null) {
            leaderNode = node;
            return true;
        }
        return false;
    }

    /**
     * Synchronized method to get the current Leader.
     */
    public synchronized SplitVoteNode getLeaderNode() {
        return leaderNode;
    }

    /**
     * Synchronized method to set the Leader node.
     */
    public synchronized void setLeaderNode(SplitVoteNode node) {
        this.leaderNode = node;
    }

    /**
     * Sends AppendEntries (heartbeats) from the Leader to Followers.
     */
    public void sendHeartbeats() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            SplitVoteNode currentLeader;
            synchronized (this) {
                currentLeader = leaderNode;
            }
            if (currentLeader != null) {
                currentLeader.log("Leader n" + currentLeader.getId() + " sends AppendEntries (heartbeats) to Followers.");
                for (SplitVoteNode node : getActiveNodes()) {
                    if (node != currentLeader) {
                        sendMessage(currentLeader, node, "AppendEntries", Color.PINK);
                    }
                }
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        // Track timelines to allow pausing/resuming
        timelines.add(timeline);
    }

    public void start() {
        for (SplitVoteNode node : nodes) {
            Thread t = new Thread(node);
            t.setDaemon(true); // JVM can exit if the main thread ends
            t.start();
        }
    }

    public void pause() {
        for (SplitVoteNode node : nodes) {
            node.pause();
        }
        for (Timeline timeline : timelines) {
            timeline.pause();
        }
    }

    public void resume() {
        for (SplitVoteNode node : nodes) {
            node.resume();
        }
        for (Timeline timeline : timelines) {
            timeline.play();
        }
    }

    public void stop() {
        for (SplitVoteNode node : nodes) {
            node.stop();
        }
        for (Timeline timeline : timelines) {
            timeline.stop();
        }
        timelines.clear();
    }

    public void log(String message) {
        // Append global messages to the GUI label
        Platform.runLater(() -> {
            messageLabel.setText(message);
        });
    }

    public void sendMessage(SplitVoteNode fromNode, SplitVoteNode toNode, String messageType, Color color) {
        if (fromNode.isDown() || toNode.isDown()) {
            return; // Do not send messages to or from failed nodes
        }
        Platform.runLater(() -> {
            // Create message representation
            Circle messageCircle = new Circle(10, color);
            Label messageLabelText = new Label(messageType);
            messageLabelText.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
            messageLabelText.setTranslateX(-5);
            messageLabelText.setTranslateY(5);

            Group messageGroup = new Group(messageCircle, messageLabelText);

            pane.getChildren().add(messageGroup);

            // Create path for the message to move along
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
                // Process the message upon arrival
                SplitVoteMessage message = new SplitVoteMessage(messageType, fromNode.getId(), fromNode, fromNode.getTerm());
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

    // Helper method for logging
    private void clusterLog(String message) {
        log(message);
    }

    // Method to finalize leader election
    public void finalizeLeader(SplitVoteNode newLeader) {
        synchronized (this) {
            if (leaderNode == null) {
                leaderNode = newLeader;
                leaderNode.setState("leader");
                log("Node n" + leaderNode.getId() + " has been elected as the Leader in Term " + leaderNode.getTerm() + ".");
                messageLabel.setText("Node n" + leaderNode.getId() + " is elected as Leader in Term " + leaderNode.getTerm() + ".");

                // Start sending heartbeats
                sendHeartbeats();
            }
        }
    }
}
