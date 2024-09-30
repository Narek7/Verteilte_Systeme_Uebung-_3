package org.example.demo3;

public class Message {
    private String type;
    private int fromId;
    private Node fromNode;
    private int term;

    public Message(String type, int fromId, Node fromNode, int term) {
        this.type = type;
        this.fromId = fromId;
        this.fromNode = fromNode;
        this.term = term;
    }

    public String getType() {
        return type;
    }

    public int getFromId() {
        return fromId;
    }

    public Node getFromNode() {
        return fromNode;
    }

    public int getTerm() {
        return term;
    }
}
