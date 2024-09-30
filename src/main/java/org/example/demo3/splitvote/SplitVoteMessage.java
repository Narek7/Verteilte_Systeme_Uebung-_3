package org.example.demo3.splitvote;

/**
 * Represents messages in the Split Vote scenario.
 */
public class SplitVoteMessage {
    private String type; // Type of the message, e.g., "RequestVote", "Vote", "AppendEntries"
    private int fromId; // ID of the sending node
    private SplitVoteNode fromNode; // Reference to the sending node
    private int term; // Current term of the message

    /**
     * Constructor for SplitVoteMessage.
     *
     * @param type     Type of the message.
     * @param fromId   ID of the sending node.
     * @param fromNode Reference to the sending node.
     * @param term     Current term.
     */
    public SplitVoteMessage(String type, int fromId, SplitVoteNode fromNode, int term) {
        this.type = type;
        this.fromId = fromId;
        this.fromNode = fromNode;
        this.term = term;
    }

    // Getter methods
    public String getType() {
        return type;
    }

    public int getFromId() {
        return fromId;
    }

    public SplitVoteNode getFromNode() {
        return fromNode;
    }

    public int getTerm() {
        return term;
    }
}
