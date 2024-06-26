package Project.Server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import Project.Common.Constants;

public class Room implements AutoCloseable {
    // protected static Server server;// used to refer to accessible server
    // functions
    private String name;
    private List<ServerThread> clients = new ArrayList<ServerThread>();

    private boolean isRunning = false;
    // Commands
    private final static String COMMAND_TRIGGER = "/";
    // private final static String CREATE_ROOM = "createroom";
    // private final static String JOIN_ROOM = "joinroom";
    // private final static String DISCONNECT = "disconnect";
    // private final static String LOGOUT = "logout";
    // private final static String LOGOFF = "logoff";
    private static final String FLIP_COMMAND = "flip";
    private static final String ROLL_COMMAND = "roll";

    private Logger logger = Logger.getLogger(Room.class.getName());

    public Room(String name) {
        this.name = name;
        isRunning = true;
    }

    private void info(String message) {
        logger.info(String.format("Room[%s]: %s", name, message));
    }

    public String getName() {
        return name;
    }

    protected synchronized void addClient(ServerThread client) {
        if (!isRunning) {
            return;
        }
        client.setCurrentRoom(this);
        client.sendJoinRoom(getName());// clear first
        if (clients.indexOf(client) > -1) {
            info("Attempting to add a client that already exists");
        } else {
            clients.add(client);
            // connect status second
            sendConnectionStatus(client, true);
            syncClientList(client);
        }


    }

    protected synchronized void removeClient(ServerThread client) {
        if (!isRunning) {
            return;
        }
        clients.remove(client);
        // we don't need to broadcast it to the server
        // only to our own Room
        if (clients.size() > 0) {
            // sendMessage(client, "left the room");
            sendConnectionStatus(client, false);
        }
        checkClients();
    }

    /***
     * Checks the number of clients.
     * If zero, begins the cleanup process to dispose of the room
     */
    private void checkClients() {
        // Cleanup if room is empty and not lobby
        if (!name.equalsIgnoreCase(Constants.LOBBY) && clients.size() == 0) {
            close();
        }
    }

    /***
     * Helper function to process messages to trigger different functionality.
     * 
     * @param message The original message being sent
     * @param client  The sender of the message (since they'll be the ones
     *                triggering the actions)
     */
    private boolean processCommands(String message, ServerThread client) {
        boolean wasCommand = false;
        try {
            if (message.startsWith(COMMAND_TRIGGER)) {
                String[] comm = message.split(COMMAND_TRIGGER);
                String part1 = comm[1];
                String[] comm2 = part1.split(" ");
                String command = comm2[0];
                // String roomName;
                wasCommand = true;
                switch (command) {
                    /*
                     * case CREATE_ROOM:
                     * roomName = comm2[1];
                     * Room.createRoom(roomName, client);
                     * break;
                     * case JOIN_ROOM:
                     * roomName = comm2[1];
                     * Room.joinRoom(roomName, client);
                     * break;
                     */
                    /*
                     * case DISCONNECT:
                     * case LOGOUT:
                     * case LOGOFF:
                     * Room.disconnectClient(client, this);
                     * break;
                     */

                //MS75 4-28-24
                /* Addded flip and roll cases here for the sake of getting the command to work since 
                    my previous methods no longer work with the payload */
                    case FLIP_COMMAND:    
                        Random random = new Random();
                        int num = random.nextInt(2);
                        if (num == 0) {
                            sendMessage(client, "<font color='blue'><b>Flipped a coin and got heads</b></font>");
                        } else {
                            sendMessage(client, "<font color='blue'><b>Flipped a coin and got tails</b></font>");
                        }
                        break;
                    case ROLL_COMMAND:
                        String[] parts = message.trim().split("\\s+");
                        String error = "Invalid roll format";
                    
                        if (parts.length == 2 && (parts[1].matches("\\d+d\\d+") || parts[1].matches("\\d+"))) {
                            try {
                                String result;
                                if (parts[1].matches("\\d+d\\d+")) {
                                    // Handling the format "XdY"
                                    String[] diceParts = parts[1].split("d");
                                    int numberOfDice = Integer.parseInt(diceParts[0]);
                                    int numberOfFaces = Integer.parseInt(diceParts[1]);
                    
                                    if (numberOfDice <= 0 || numberOfFaces <= 0) {
                                        sendMessage(client, "<font color='red'>" + error + "</font>");
                                    }
                    
                                    StringBuilder rollResult = new StringBuilder();
                                    int total = 0;
                                    for (int i = 0; i < numberOfDice; i++) {
                                        int diceRoll = (int) (Math.random() * numberOfFaces) + 1;
                                        total += diceRoll;
                                        rollResult.append(diceRoll);
                                        if (i < numberOfDice - 1) {
                                            rollResult.append(", ");
                                        }
                                    }
                                    result = String.format("**%s** rolled %dd%d and got %s with a Total of: %d", client.getClientName(), numberOfDice, numberOfFaces, rollResult.toString(), total);
                                } else {
                                    int max = Integer.parseInt(parts[1]);
                                    int rollResult = (int) (Math.random() * (max - 1 + 1)) + 1;
                                    result = String.format("**%s** rolled: %d", client.getClientName(), rollResult);
                                }
                    
                                sendMessage(client, "<font color='green'><b>" + result + "</b></font>");
                            } catch (NumberFormatException e) {
                                sendMessage(client, "*" + error  + "*");
                            }
                        } else {
                            sendMessage(client, "<font color='red'>" + error + "</font>");
                        }
                        break;
                                        
                    default:
                        wasCommand = false;
                        break;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return wasCommand;
    }

    // Command helper methods
    private synchronized void syncClientList(ServerThread joiner) {
        Iterator<ServerThread> iter = clients.iterator();
        while (iter.hasNext()) {
            ServerThread st = iter.next();
            if (st.getClientId() != joiner.getClientId()) {
                joiner.sendClientMapping(st.getClientId(), st.getClientName());
            }
        }
    }
    protected static void createRoom(String roomName, ServerThread client) {
        if (Server.INSTANCE.createNewRoom(roomName)) {
            // server.joinRoom(roomName, client);
            Room.joinRoom(roomName, client);
        } else {
            client.sendMessage(Constants.DEFAULT_CLIENT_ID, String.format("Room %s already exists", roomName));
        }
    }

    protected static void joinRoom(String roomName, ServerThread client) {
        if (!Server.INSTANCE.joinRoom(roomName, client)) {
            client.sendMessage(Constants.DEFAULT_CLIENT_ID, String.format("Room %s doesn't exist", roomName));
        }
    }

    protected static List<String> listRooms(String searchString, int limit) {
        return Server.INSTANCE.listRooms(searchString, limit);
    }

    protected static void disconnectClient(ServerThread client, Room room) {
        client.setCurrentRoom(null);
        client.disconnect();
        room.removeClient(client);
    }
    // end command helper methods

    /***
     * Takes a sender and a message and broadcasts the message to all clients in
     * this room. Client is mostly passed for command purposes but we can also use
     * it to extract other client info.
     * 
     * @param sender  The client sending the message
     * @param message The message to broadcast inside the room
     */
    protected synchronized void sendMessage(ServerThread sender, String message) {
        if (!isRunning) {
            return;
        }
        info("Sending message to " + clients.size() + " clients");
        if (sender != null && processCommands(message, sender)) {
            // it was a command, don't broadcast
            return;
        }

        /// String from = (sender == null ? "Room" : sender.getClientName());
        long from = (sender == null) ? Constants.DEFAULT_CLIENT_ID : sender.getClientId();
        Iterator<ServerThread> iter = clients.iterator();
        while (iter.hasNext()) {
            ServerThread client = iter.next();
            boolean messageSent = client.sendMessage(from, message);
            if (!messageSent) {
                handleDisconnect(iter, client);
            }
        }
    }

    protected synchronized void sendConnectionStatus(ServerThread sender, boolean isConnected) {
        Iterator<ServerThread> iter = clients.iterator();
        while (iter.hasNext()) {
            ServerThread client = iter.next();
            boolean messageSent = client.sendConnectionStatus(sender.getClientId(), sender.getClientName(),
                    isConnected);
            if (!messageSent) {
                handleDisconnect(iter, client);
            }
        }
    }

    private void handleDisconnect(Iterator<ServerThread> iter, ServerThread client) {
        iter.remove();
        info("Removed client " + client.getClientName());
        checkClients();
        sendMessage(null, client.getClientName() + " disconnected");
    }
    public void close() {
        Server.INSTANCE.removeRoom(this);
        // server = null;
        isRunning = false;
        clients = null;
    }
    
    protected static void Roll(String roll, ServerThread client) {
        String[] parts = roll.trim().split("\\s+");
        String error ="Invalid number";
       
        if (parts.length == 2 && parts[1].matches("\\d+d\\d+")) {
            try {
                String[] diceParts = parts[1].split("d");
                int numberOfDice = Integer.parseInt(diceParts[0]);
                int numberOfFaces = Integer.parseInt(diceParts[1]);
                if (numberOfDice <= 0 || numberOfFaces <= 0) {
                    client.sendRoll(client.getClientId(), error);
                    return;
                }
                StringBuilder result = new StringBuilder();
                result.append(String.format("%s rolled %dd%d: ", client.getClientName(), numberOfDice, numberOfFaces));
                int total = 0;
                for (int i = 0; i < numberOfDice; i++) {
                    int diceRoll = (int) (Math.random() * numberOfFaces) + 1; 
                    total += diceRoll;
                    result.append(diceRoll);
                    if (i < numberOfDice - 1) {
                        result.append(", ");
                    }
                }
                result.append(String.format(" (Total: %d)", total));
                // Send the result to the client using sendRoll
                client.sendRoll(client.getClientId(), result.toString()); 
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                client.sendRoll(client.getClientId(), error);
            }
       
        } else if (parts.length == 1 && parts[0].matches("\\d+")) {
            try {
                int max = Integer.parseInt(parts[0]);
                int start = 0; // Starting value is 0 
                int rollResult = (int) (Math.random() * (max - start + 1)) + start;
                String message = String.format("%s rolled: %d", client.getClientName(), rollResult);
                // Send the result to the client using sendRoll
                client.sendRoll(client.getClientId(), message); 
            } catch (NumberFormatException e) {
                client.sendRoll(client.getClientId(), error);
            }
        } else {
            client.sendRoll(client.getClientId(), error);
        }
    }
// New Code Milestone 3 MS75 4-27-24 
// Mute Feature 
    private List<Long> mutedClients = new ArrayList<>(); 

    protected void mute(long clientIdToMute, ServerThread sender) {
        mutedClients.add(clientIdToMute); 
        sender.sendMute(clientIdToMute);
    
    
    }
}