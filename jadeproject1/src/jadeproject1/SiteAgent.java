package jadeproject1;


import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.core.AID;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class SiteAgent extends Agent {
    private int clock = 0;
    private int id;
    private String state = "IDLE";
    private PriorityQueue<Request> queue = new PriorityQueue<>();
    private Set<String> acksReceived = new HashSet<>();
    private JFrame frame;
    private JTextArea logArea;

    @Override
    protected void setup() {
        id = Integer.parseInt(getLocalName().replace("Site", ""));
        System.out.println(getLocalName() + " started.");
        setupGUI();

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    handleMessage(msg);
                } else {
                    block();
                }
            }
        });
    }

    private void setupGUI() {
        frame = new JFrame(getLocalName());
        frame.setSize(400, 300);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JButton requestButton = new JButton("Request CS");
        requestButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                requestCriticalSection();
            }
        });

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);

        frame.getContentPane().add(requestButton, BorderLayout.NORTH);
        frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private void handleMessage(ACLMessage msg) {
        String[] content = msg.getContent().split(",");
        int msgClock = Integer.parseInt(content[0]);
        String type = content[1];
        String sender = msg.getSender().getLocalName();

        clock = Math.max(clock, msgClock) + 1;

        System.out.println(getLocalName() + " received " + type + " from " + sender + " at clock " + clock);

        switch (type) {
            case "REQ":
                queue.add(new Request(msgClock, sender));
                sendMessage("ACK", sender);
                break;
            case "ACK":
                acksReceived.add(sender);
                checkCriticalSectionEntry();
                break;
            case "REL":
                queue.removeIf(r -> r.sender.equals(sender));
                checkCriticalSectionEntry();
                break;
        }

        updateStatus();
    }

    private void requestCriticalSection() {
        clock++;
        state = "REQUESTING";
        acksReceived.clear();
        queue.add(new Request(clock, getLocalName()));
        broadcastMessage("REQ");
        updateStatus();
    }

    private void checkCriticalSectionEntry() {
        if (state.equals("REQUESTING") && acksReceived.size() == 2 && queue.peek().sender.equals(getLocalName())) {
            enterCriticalSection();
        }
    }

    private void enterCriticalSection() {
        state = "IN_CS";
        log(getLocalName() + " ENTERED CS at time " + clock);
        new java.util.Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                releaseCriticalSection();
            }
        }, 2000);
    }

    private void releaseCriticalSection() {
        clock++;
        state = "IDLE";
        queue.poll();
        broadcastMessage("REL");
        updateStatus();
    }

    private void broadcastMessage(String type) {
        for (int i = 1; i <= 3; i++) {
            if (i != id) {
                sendMessage(type, "Site" + i);
            }
        }
    }

    private void sendMessage(String type, String receiverName) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID(receiverName, AID.ISLOCALNAME));
        msg.setContent(clock + "," + type);
        send(msg);
        System.out.println(getLocalName() + " sending " + type + " to " + receiverName + " at clock " + clock);
    }

    private void updateStatus() {
        log(getLocalName() + " [Clock: " + clock + ", State: " + state + "] Queue: " + queue);
    }

    private void log(String message) {
        System.out.println(message);
        logArea.append(message + "\n");
    }

    class Request implements Comparable<Request> {
        int clock;
        String sender;

        Request(int clock, String sender) {
            this.clock = clock;
            this.sender = sender;
        }

        public int compareTo(Request other) {
            if (this.clock == other.clock) return this.sender.compareTo(other.sender);
            return Integer.compare(this.clock, other.clock);
        }

        @Override
        public String toString() {
            return sender + "(" + clock + ")";
        }
    }
}
