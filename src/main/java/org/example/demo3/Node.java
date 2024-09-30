package org.example.demo3;

import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Node implements Runnable {
    private int id;
    private String state = "follower";
    private int term = 0;
    private Integer votedFor = null;
    private Cluster cluster;
    private long electionTimeout = 5000 + new Random().nextInt(1500); // Default timeout between 5-6.5s
    private long lastHeartbeat = System.currentTimeMillis();
    private boolean stopFlag = false;

    // Visualization components
    private Circle circle;
    private Text label;
    private Text downText; // 'X' text to indicate node failure

    private Random random = new Random();

    // Message queue for incoming messages
    private BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();

    // For tracking votes
    private Set<Integer> votesReceived = new HashSet<>();

    private boolean isPaused = false;

    private boolean isDown = false; // Indicates if the node is down

    // Election timer control
    private boolean electionTimerRunning = true; // Indicates if the election timer is active

    // Synchronization lock for state transitions
    private final Object stateLock = new Object();

    public Node(int id, Cluster cluster, double x, double y) {
        this.id = id;
        this.cluster = cluster;

        // Initialize visualization components
        circle = new Circle(30, Color.LIGHTBLUE);
        circle.setCenterX(x);
        circle.setCenterY(y);

        label = new Text();
        label.setX(x - 40);
        label.setY(y + 50);

        // Text to indicate node is down
        downText = new Text("X");
        downText.setStyle("-fx-font-size: 40px; -fx-font-weight: bold; -fx-fill: red;");
        downText.setX(x - 10);
        downText.setY(y + 15);
        downText.setVisible(false);

        // Initial label update
        updateLabel(electionTimeout);
    }

    // Getters for visualization
    public Circle getCircle() {
        return circle;
    }

    public Text getLabel() {
        return label;
    }

    public Text getDownText() {
        return downText;
    }

    public int getId() {
        return id;
    }

    public void setDown(boolean down) {
        isDown = down;
        Platform.runLater(() -> {
            downText.setVisible(isDown);
            if (isDown) {
                circle.setFill(Color.GRAY);
                label.setText("ID: n" + id + "\nDOWN");
            }
        });
    }

    public boolean isDown() {
        return isDown;
    }

    public void setElectionTimeout(long timeout) {
        synchronized (stateLock) {
            this.electionTimeout = timeout;
            updateLabel(electionTimeout);
        }
    }

    public long getElectionTimeout() {
        synchronized (stateLock) {
            return electionTimeout;
        }
    }

    public void setLastHeartbeat(long time) {
        synchronized (stateLock) {
            this.lastHeartbeat = time;
        }
    }

    public void setWillNotVoteInTerm(int term) {
        // Not used in best case scenario
    }

    public void run() {
        while (!stopFlag) {
            synchronized (this) {
                if (isPaused || isDown) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            if (isDown) {
                continue; // Skip processing if node is down
            }

            if (electionTimerRunning) {
                long elapsedTime;
                long remainingTime;
                synchronized (stateLock) {
                    elapsedTime = System.currentTimeMillis() - lastHeartbeat;
                    remainingTime = electionTimeout - elapsedTime;
                }

                if (remainingTime <= 0) {
                    cluster.nodeTimeoutExpired(this);
                } else if (state.equals("follower")) {
                    updateLabel(remainingTime);
                }
            }

            // Process incoming messages
            processIncomingMessages();

            try {
                Thread.sleep(100); // Update more frequently for smoother countdown
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void processIncomingMessages() {
        if (isDown) {
            return; // Do not process messages if node is down
        }
        Message message;
        while ((message = messageQueue.poll()) != null) {
            switch (message.getType()) {
                case "RequestVote":
                    handleRequestVote(message);
                    break;
                case "Vote":
                    handleVote(message);
                    break;
                case "AppendEntries":
                    handleAppendEntries(message);
                    break;
                case "Ack":
                    handleAck(message);
                    break;
            }
        }
    }

    private void handleRequestVote(Message message) {
        synchronized (stateLock) {
            if (message.getTerm() > term) {
                term = message.getTerm();
                votedFor = null;
                setState("follower");

                // Restart the election timer
                electionTimerRunning = true;
                resetElectionTimeout();

                updateVisualization();
                cluster.log("Node n" + id + " steps down to follower in term " + term + ".");
            }

            if ((votedFor == null || votedFor == message.getFromId()) && message.getTerm() == term) {
                votedFor = message.getFromId();
                lastHeartbeat = System.currentTimeMillis(); // Reset election timeout
                updateLabel(electionTimeout);
                cluster.sendMessage(this, message.getFromNode(), "Vote", Color.LIGHTGREEN);
                log("Node n" + id + " votes for Node n" + message.getFromId() + " in term " + term + ".");
            } else {
                // Already voted in this term
                log("Node n" + id + " has already voted in term " + term + ".");
            }
        }
    }

    private void handleVote(Message message) {
        synchronized (stateLock) {
            if (state.equals("candidate") && message.getTerm() == term) {
                votesReceived.add(message.getFromId());
                log("Node n" + id + " received vote from Node n" + message.getFromId() + " in term " + term + ".");
                if (votesReceived.size() >= (cluster.getActiveNodes().size() / 2 + 1)) {
                    becomeLeader();
                }
            }
        }
    }

    private void handleAppendEntries(Message message) {
        synchronized (stateLock) {
            if (message.getTerm() > term) {
                term = message.getTerm();
                votedFor = null;
                setState("follower");

                // Restart the election timer
                electionTimerRunning = true;
                resetElectionTimeout();

                updateVisualization();
                cluster.log("Node n" + id + " steps down to follower in term " + term + ".");
            }

            if (message.getTerm() >= term) {
                if (message.getTerm() > term) {
                    term = message.getTerm();
                    votedFor = null;
                    setState("follower");

                    // Restart the election timer
                    electionTimerRunning = true;
                    resetElectionTimeout();

                    updateVisualization();
                    cluster.log("Node n" + id + " steps down to follower in term " + term + ".");
                }
                lastHeartbeat = System.currentTimeMillis();
                cluster.sendMessage(this, message.getFromNode(), "Ack", Color.PINK);
                log("Node n" + id + " acknowledges AppendEntries from Leader n" + message.getFromId() + " in term " + term + ".");
            }
        }
    }

    private void handleAck(Message message) {
        // For simplicity, leader does not need to process acks in this simulation
    }

    public void receiveMessage(Message message) {
        if (isDown) {
            return; // Ignore messages if node is down
        }
        // Ignore messages from past terms
        if (message.getTerm() < term) {
            return;
        }
        messageQueue.offer(message);
    }

    public void pause() {
        isPaused = true;
    }

    public synchronized void resume() {
        isPaused = false;
        synchronized (this) {
            notify();
        }
    }

    public void stop() {
        stopFlag = true;
        resume(); // Ensure thread exits if waiting
    }

    public void setState(String newState) {
        synchronized (stateLock) {
            state = newState;
            updateVisualization();
        }
    }

    public String getState() {
        synchronized (stateLock) {
            return state;
        }
    }

    public void setTerm(int newTerm) {
        synchronized (stateLock) {
            term = newTerm;
            updateLabel(electionTimeout); // Update label with current term
        }
    }

    public int getTerm() {
        synchronized (stateLock) {
            return term;
        }
    }

    public void setVotedFor(Integer votedForId) {
        synchronized (stateLock) {
            votedFor = votedForId;
            updateLabel(electionTimeout); // Update label with current votedFor
        }
    }

    public Integer getVotedFor() {
        synchronized (stateLock) {
            return votedFor;
        }
    }

    public void resetVotesReceived() {
        synchronized (stateLock) {
            votesReceived.clear();
        }
    }

    public void resetElectionTimeout() {
        synchronized (stateLock) {
            electionTimeout = 5000 + random.nextInt(1500); // Randomized timeout between 5-6.5s
            lastHeartbeat = System.currentTimeMillis();
            updateLabel(electionTimeout);
        }
    }

    private void updateVisualization() {
        Platform.runLater(() -> {
            if (isDown) {
                return; // Do not update visualization if node is down
            }
            switch (state) {
                case "follower":
                    circle.setFill(Color.LIGHTBLUE);
                    break;
                case "candidate":
                    circle.setFill(Color.LIGHTGREEN);
                    break;
                case "leader":
                    circle.setFill(Color.RED);
                    break;
            }
            updateLabel(electionTimeout); // Update label when state changes
        });
    }

    private void updateLabel(long remainingTime) {
        if (isDown) {
            return; // Do not update label if node is down
        }
        String votedForText = (votedFor != null) ? "n" + votedFor : "None";
        String timeoutText = "";

        if (state.equals("follower") && electionTimerRunning) {
            double remainingSeconds = remainingTime / 1000.0;
            DecimalFormat df = new DecimalFormat("#0.0");
            timeoutText = "\nTimeout: " + df.format(remainingSeconds) + "s";
        } else if (state.equals("candidate")) {
            timeoutText = "\nWaiting for votes...";
        } else if (state.equals("leader")) {
            timeoutText = "\nHeartbeat sent.";
        }

        final String labelText = "ID: n" + id +
                "\nTerm: " + term +
                "\nVotedFor: " + votedForText +
                "\nState: " + state +
                timeoutText;

        Platform.runLater(() -> label.setText(labelText));
    }

    private void becomeLeader() {
        // Attempt to set self as leader in the Cluster
        boolean success = cluster.attemptToSetLeader(this);
        if (success) {
            setState("leader");
            cluster.log("Node n" + id + " becomes leader in term " + term + ".");
            cluster.updateMessage("Node n" + id + " becomes leader in term " + term + ".");

            // Leader's election timer is not needed; heartbeats prevent timeouts
            electionTimerRunning = false;

            // Start sending AppendEntries
            cluster.sendAppendEntries();
        } else {
            log("Node n" + id + " detected an existing leader. Aborting leadership.");
            setState("follower");
            electionTimerRunning = true;
            resetElectionTimeout();
        }
    }

    public void log(String message) {
        cluster.log(message);
    }

    public void nodeTimeoutExpired() {
        synchronized (stateLock) {
            if (isDown || !electionTimerRunning) {
                return;
            }

            if (state.equals("leader")) {
                return;
            }

            // Before becoming a candidate, check if a leader already exists
            if (cluster.getLeaderNode() != null) {
                log("Node n" + id + " detected existing leader. Remaining follower.");
                setState("follower");
                electionTimerRunning = true;
                resetElectionTimeout();
                return;
            }

            // Increment term before becoming candidate
            term += 1;

            // Stop the election timer
            electionTimerRunning = false;

            // Update the global message
            cluster.updateMessage("Node n" + id + " starts a new election in term " + term + ".");

            setState("candidate");
            votedFor = id;
            resetVotesReceived();
            log("Node n" + id + " becomes candidate for term " + term + " and requests votes.");

            // Send RequestVote messages
            for (Node otherNode : cluster.getActiveNodes()) {
                if (otherNode != this) {
                    cluster.sendMessage(this, otherNode, "RequestVote", Color.YELLOW);
                }
            }

            // Do not reset election timer here, as candidates stop their timers
        }
    }
}
