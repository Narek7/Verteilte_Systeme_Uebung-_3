package org.example.demo3.splitvote;

import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SplitVoteNode implements Runnable {
    private int id;
    private String state = "follower";
    private int term = 1; // Start with Term 1
    private Integer votedFor = null;
    private SplitVoteCluster cluster;
    private long electionTimeout = 5000 + new Random().nextInt(1500); // Default timeout between 5-6.5s
    private long lastHeartbeat = System.currentTimeMillis();
    private boolean stopFlag = false;

    // Visualization components
    private Circle circle;
    private Text label;
    private Text downText; // 'X' text to indicate node failure

    private Random random = new Random();

    // Message queue for incoming messages
    private BlockingQueue<SplitVoteMessage> messageQueue = new LinkedBlockingQueue<>();

    // To track received votes
    private Set<Integer> votesReceived = new HashSet<>();

    private boolean isPaused = false;

    private boolean isDown = false; // Indicates if the node has failed

    // Control for the election timer
    private boolean electionTimerRunning = true; // Indicates if the election timer is active

    // Synchronization lock for state transitions
    private final Object stateLock = new Object();

    public SplitVoteNode(int id, SplitVoteCluster cluster, double x, double y) {
        this.id = id;
        this.cluster = cluster;

        // Initialize visualization components
        circle = new Circle(30, Color.LIGHTBLUE);
        circle.setCenterX(x);
        circle.setCenterY(y);

        label = new Text();
        label.setX(x - 40);
        label.setY(y + 50);

        // Text to display that the node has failed
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
        // Not needed for the split vote scenario
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
                Thread.sleep(100); // Frequent updates for smoother countdown display
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void processIncomingMessages() {
        if (isDown) {
            return; // Do not process messages if node is down
        }
        SplitVoteMessage message;
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

    private void handleRequestVote(SplitVoteMessage message) {
        synchronized (stateLock) {
            if (message.getTerm() > term) {
                term = message.getTerm();
                votedFor = null;
                setState("follower");

                // Reset election timer
                electionTimerRunning = true;
                resetElectionTimeout();

                updateVisualization();
                cluster.log("Node n" + id + " reverts to Follower in Term " + term + ".");
            }

            if ((votedFor == null || votedFor.equals(message.getFromId())) && message.getTerm() == term) {
                votedFor = message.getFromId();
                lastHeartbeat = System.currentTimeMillis(); // Reset election timeout
                updateLabel(electionTimeout);
                cluster.sendMessage(this, message.getFromNode(), "Vote", Color.LIGHTGREEN);
                log("Node n" + id + " votes for Node n" + message.getFromId() + " in Term " + term + ".");
            } else {
                // Already voted in this term
                log("Node n" + id + " has already voted in Term " + term + ".");
            }
        }
    }

    private void handleVote(SplitVoteMessage message) {
        synchronized (stateLock) {
            if (state.equals("candidate") && message.getTerm() == term) {
                votesReceived.add(message.getFromId());
                log("Node n" + id + " receives a vote from Node n" + message.getFromId() + " in Term " + term + ".");
                if (votesReceived.size() >= (cluster.getActiveNodes().size() / 2 + 1)) {
                    becomeLeader();
                }
            }
        }
    }

    private void handleAppendEntries(SplitVoteMessage message) {
        synchronized (stateLock) {
            if (message.getTerm() > term) {
                term = message.getTerm();
                votedFor = null;
                setState("follower");

                // Reset election timer
                electionTimerRunning = true;
                resetElectionTimeout();

                updateVisualization();
                cluster.log("Node n" + id + " reverts to Follower in Term " + term + ".");
            }

            if (message.getTerm() >= term) {
                if (message.getTerm() > term) {
                    term = message.getTerm();
                    votedFor = null;
                    setState("follower");

                    // Reset election timer
                    electionTimerRunning = true;
                    resetElectionTimeout();

                    updateVisualization();
                    cluster.log("Node n" + id + " reverts to Follower in Term " + term + ".");
                }
                lastHeartbeat = System.currentTimeMillis();
                cluster.sendMessage(this, message.getFromNode(), "Ack", Color.PINK);
                log("Node n" + id + " acknowledges AppendEntries from Leader n" + message.getFromId() + " in Term " + term + ".");
            }
        }
    }

    private void handleAck(SplitVoteMessage message) {
        // For this simulation, the Leader does not require Acks
    }

    public void receiveMessage(SplitVoteMessage message) {
        if (isDown) {
            return; // Ignore messages if node is down
        }
        // Ignore messages from previous terms
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
        resume(); // Ensure the thread ends if it's waiting
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
            updateLabel(electionTimeout); // Update label with current vote
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
            updateLabel(electionTimeout); // Update label on state change
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
            timeoutText = "\nHeartbeats sent.";
        }

        final String labelText = "ID: n" + id +
                "\nTerm: " + term +
                "\nVotedFor: " + votedForText +
                "\nState: " + state +
                timeoutText;

        Platform.runLater(() -> label.setText(labelText));
    }

    public void becomeCandidate() {
        synchronized (stateLock) {
            setState("candidate");
            votedFor = id;
            resetVotesReceived();
            log("Node n" + id + " becomes a candidate for Term " + term + " and requests votes.");

            // Send RequestVote messages
            for (SplitVoteNode otherNode : cluster.getActiveNodes()) {
                if (otherNode != this) {
                    cluster.sendMessage(this, otherNode, "RequestVote", Color.YELLOW);
                }
            }

            // Stop the election timer as the candidate does not need it
            electionTimerRunning = false;
        }
    }

    private void becomeLeader() {
        // Attempt to set self as Leader in the cluster
        boolean success = cluster.attemptToSetLeader(this);
        if (success) {
            setState("leader");
            cluster.log("Node n" + id + " has been elected as Leader in Term " + term + ".");
            cluster.updateMessage("Node n" + id + " is elected as Leader in Term " + term + ".");

            // Stop election timer as Leader does not need it
            electionTimerRunning = false;

            // Start sending heartbeats
            cluster.sendHeartbeats();
        } else {
            log("Node n" + id + " recognizes an existing Leader. Does not attempt to take leadership.");
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

            // Before becoming a candidate, check if a Leader exists
            if (cluster.getLeaderNode() != null) {
                log("Node n" + id + " recognizes an existing Leader. Remains as Follower.");
                setState("follower");
                electionTimerRunning = true;
                resetElectionTimeout();
                return;
            }

            // Increment term before becoming a candidate
            term += 1;

            // Stop the election timer
            electionTimerRunning = false;

            // Update global message
            cluster.updateMessage("Node n" + id + " starts a new election for Term " + term + ".");

            setState("candidate");
            votedFor = id;
            resetVotesReceived();
            log("Node n" + id + " becomes a candidate for Term " + term + " and requests votes.");

            // Send RequestVote messages
            for (SplitVoteNode otherNode : cluster.getActiveNodes()) {
                if (otherNode != this) {
                    cluster.sendMessage(this, otherNode, "RequestVote", Color.YELLOW);
                }
            }

            // Election timer not reset here as the candidate does not need it
        }
    }

    // Method to receive a vote (used during initial split vote setup)
    public void receiveVote(int voterId) {
        synchronized (stateLock) {
            votesReceived.add(voterId);
            log("Node n" + id + " receives a vote from Node n" + voterId + " in Term " + term + ".");
            if (votesReceived.size() >= (cluster.getActiveNodes().size() / 2 + 1)) {
                becomeLeader();
            }
        }
    }

    // Method to vote for self (used during initial split vote setup)
    public void votedForSelf() {
        synchronized (stateLock) {
            votedFor = id;
            votesReceived.add(id);
            updateLabel(electionTimeout);
            log("Node n" + id + " votes for itself in Term " + term + ".");
        }
    }

    // Method to check and handle election timeout
    public void checkElectionTimeout() {
        synchronized (stateLock) {
            if (System.currentTimeMillis() - lastHeartbeat >= electionTimeout) {
                cluster.nodeTimeoutExpired(this);
            }
        }
    }
}
