package g_earth.extensions;

import g_earth.protocol.HMessage;
import g_earth.protocol.HPacket;
import g_earth.ui.extensions.Extensions;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Jonas on 23/06/18.
 */
public abstract class Extension {

    public interface MessageListener {
        void act(HMessage message);
    }
    public interface FlagsCheckListener {
        void act(String[] args);
    }

    protected static final boolean CANLEAVE = true;     // can you disconnect the ext
    protected static final boolean CANDELETE = true;    // can you delete the ext (will be false for some built-in extensions)

    private String[] args;
    private boolean isCorrupted = false;
    private static final String[] PORT_FLAG = {"--port", "-p"};
    private static final String[] FILE_FLAG = {"--filename", "-f"};

    private OutputStream out = null;
    private final Map<Integer, List<MessageListener>> incomingMessageListeners = new HashMap<>();
    private final Map<Integer, List<MessageListener>> outgoingMessageListeners = new HashMap<>();
    private FlagsCheckListener flagRequestCallback = null;

    private String getArgument(String[] args, String... arg) {
        for (int i = 0; i < args.length - 1; i++) {
            for (String str : arg) {
                if (args[i].toLowerCase().equals(str.toLowerCase())) {
                    return args[i+1];
                }
            }
        }
        return null;
    }

    /**
     * Makes the connection with G-Earth, pass the arguments given in the Main method "super(args)"
     * @param args arguments
     */
    public Extension(String[] args) {
        //obtain port
        this.args = args;


        if (getInfoAnnotations() == null) {
            System.err.println("Extension info not found\n\n" +
                    "Usage:\n" +
                    "@ExtensionInfo ( \n" +
                    "       Title =  \"...\",\n" +
                    "       Description =  \"...\",\n" +
                    "       Version =  \"...\",\n" +
                    "       Author =  \"...\"" +
                    "\n)");
            isCorrupted = true;
        }

        if (getArgument(args, PORT_FLAG) == null) {
            System.err.println("Don't forget to include G-Earth's port in your program parameters (-p {port})");
            isCorrupted = true;
        }
    }

    public void run() {
        if (isCorrupted) {
            return;
        }

        int port = Integer.parseInt(getArgument(args, PORT_FLAG));
        String file = getArgument(args, FILE_FLAG);

        Socket gEarthExtensionServer = null;
        try {
            gEarthExtensionServer = new Socket("127.0.0.1", port);
            InputStream in = gEarthExtensionServer.getInputStream();
            DataInputStream dIn = new DataInputStream(in);
            out = gEarthExtensionServer.getOutputStream();

            while (!gEarthExtensionServer.isClosed()) {

                int length;
                try {
                    length = dIn.readInt();
                }
                catch(EOFException exception) {
                    //g-earth closed the extension
                    break;
                }

                byte[] headerandbody = new byte[length + 4];

                int amountRead = 0;

                while (amountRead < length) {
                    amountRead += dIn.read(headerandbody, 4 + amountRead, Math.min(dIn.available(), length - amountRead));
                }

                HPacket packet = new HPacket(headerandbody);
                packet.fixLength();


                if (packet.headerId() == Extensions.OUTGOING_MESSAGES_IDS.INFOREQUEST) {
                    ExtensionInfo info = getInfoAnnotations();

                    HPacket response = new HPacket(Extensions.INCOMING_MESSAGES_IDS.EXTENSIONINFO);
                    response.appendString(info.Title())
                            .appendString(info.Author())
                            .appendString(info.Version())
                            .appendString(info.Description())
                            .appendBoolean(isOnClickMethodUsed())
                            .appendBoolean(file != null)
                            .appendString(file == null ? "": file)
                            .appendBoolean(CANLEAVE)
                            .appendBoolean(CANDELETE);
                    writeToStream(response.toBytes());
                }
                else if (packet.headerId() == Extensions.OUTGOING_MESSAGES_IDS.CONNECTIONSTART) {
                    onStartConnection();
                }
                else if (packet.headerId() == Extensions.OUTGOING_MESSAGES_IDS.CONNECTIONEND) {
                    onEndConnection();
                }
                else if (packet.headerId() == Extensions.OUTGOING_MESSAGES_IDS.FLAGSCHECK) {
                    // body = an array of G-Earths main flags
                    if (flagRequestCallback != null) {
                        int arraysize = packet.readInteger();
                        String[] gEarthArgs = new String[arraysize];
                        for (int i = 0; i < gEarthArgs.length; i++) {
                            gEarthArgs[i] = packet.readString();
                        }
                        flagRequestCallback.act(gEarthArgs);
                    }
                    flagRequestCallback = null;
                }
                else if (packet.headerId() == Extensions.OUTGOING_MESSAGES_IDS.INIT) {
                    init();
                }
                else if (packet.headerId() == Extensions.OUTGOING_MESSAGES_IDS.FREEFLOW) {
                    // nothing to be done yet
                }
                else if (packet.headerId() == Extensions.OUTGOING_MESSAGES_IDS.ONDOUBLECLICK) {
                    onClick();
                }
                else if (packet.headerId() == Extensions.OUTGOING_MESSAGES_IDS.PACKETINTERCEPT) {
                    String stringifiedMessage = packet.readLongString();
                    HMessage habboMessage = new HMessage(stringifiedMessage);
                    HPacket habboPacket = habboMessage.getPacket();

                    Map<Integer, List<MessageListener>> listeners =
                            habboMessage.getDestination() == HMessage.Side.TOCLIENT ?
                                    incomingMessageListeners :
                                    outgoingMessageListeners;

                    List<MessageListener> correctListeners = new ArrayList<>();

                    synchronized (incomingMessageListeners) {
                        synchronized (outgoingMessageListeners) {
                            if (listeners.containsKey(-1)) { // registered on all packets
                                for (int i = listeners.get(-1).size() - 1; i >= 0; i--) {
                                    correctListeners.add(listeners.get(-1).get(i));
                                }
                            }

                            if (listeners.containsKey(habboPacket.headerId())) {
                                for (int i = listeners.get(habboPacket.headerId()).size() - 1; i >= 0; i--) {
                                    correctListeners.add(listeners.get(habboPacket.headerId()).get(i));
                                }
                            }
                        }
                    }

                    for(MessageListener listener : correctListeners) {
                        habboMessage.getPacket().setReadIndex(6);
                        listener.act(habboMessage);
                    }
                    habboMessage.getPacket().setReadIndex(6);

                    HPacket response = new HPacket(Extensions.INCOMING_MESSAGES_IDS.MANIPULATEDPACKET);
                    response.appendLongString(habboMessage.stringify());

                    writeToStream(response.toBytes());

                }
            }

        } catch (IOException | ArrayIndexOutOfBoundsException e) {
            System.err.println("Connection failed; is G-Earth active?");
//            e.printStackTrace();
        }
        finally {
            if (gEarthExtensionServer != null && !gEarthExtensionServer.isClosed()) {
                try {
                    gEarthExtensionServer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void writeToStream(byte[] bytes) throws IOException {
        synchronized (this) {
            out.write(bytes);
        }
    }

    /**
     * Send a message to the client
     * @param packet packet to be sent
     * @return success or failure
     */
    protected boolean sendToClient(HPacket packet) {
        return send(packet, HMessage.Side.TOCLIENT);
    }

    /**
     * Send a message to the server
     * @param packet packet to be sent
     * @return success or failure
     */
    protected boolean sendToServer(HPacket packet) {
        return send(packet, HMessage.Side.TOSERVER);
    }
    private boolean send(HPacket packet, HMessage.Side side) {
        HPacket packet1 = new HPacket(Extensions.INCOMING_MESSAGES_IDS.SENDMESSAGE);
        packet1.appendByte(side == HMessage.Side.TOCLIENT ? (byte)0 : (byte)1);
        packet1.appendInt(packet.getBytesLength());
        packet1.appendBytes(packet.toBytes());
        try {
            writeToStream(packet1.toBytes());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Register a listener on a specific packet Type
     * @param side ToClient or ToServer
     * @param headerId the packet header ID
     * @param messageListener the callback
     */
    protected void intercept(HMessage.Side side, int headerId, MessageListener messageListener) {
        Map<Integer, List<MessageListener>> listeners =
                side == HMessage.Side.TOCLIENT ?
                        incomingMessageListeners :
                        outgoingMessageListeners;

        synchronized (listeners) {
            if (!listeners.containsKey(headerId)) {
                listeners.put(headerId, new ArrayList<>());
            }
        }


        listeners.get(headerId).add(messageListener);
    }

    /**
     * Register a listener on all packets
     * @param side ToClient or ToServer
     * @param messageListener the callback
     */
    protected void intercept(HMessage.Side side, MessageListener messageListener) {
        intercept(side, -1, messageListener);
    }

    /**
     * Requests the flags which have been given to G-Earth when it got executed
     * For example, you might want this extension to do a specific thing if the flag "-e" was given
     * @param flagRequestCallback callback
     * @return if the request was successful, will return false if another flagrequest is busy
     */
    protected boolean requestFlags(FlagsCheckListener flagRequestCallback) {
        if (this.flagRequestCallback != null) return false;
        this.flagRequestCallback = flagRequestCallback;
        return true;
    }

    /**
     * Write to the console in G-Earth
     * @param s the text to be written
     */
    protected void writeToConsole(String s) {
        HPacket packet = new HPacket(Extensions.INCOMING_MESSAGES_IDS.EXTENSIONCONSOLELOG);
        packet.appendString(s);
        try {
            writeToStream(packet.toBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private boolean isOnClickMethodUsed() {

        Class<? extends Extension> c = getClass();

        while (c != Extension.class) {
            try {
                c.getDeclaredMethod("onClick");
                // if it didnt error, onClick exists
                return true;
            } catch (NoSuchMethodException e) {
//                e.printStackTrace();
            }

            c = (Class<? extends Extension>) c.getSuperclass();
        }

        return false;
    }

    /**
     * Gets called when a connection has been established with G-Earth.
     * This does not imply a connection with Habbo is setup.
     */
    protected void init(){}

    /**
     * The application got doubleclicked from the G-Earth interface. Doing something here is optional
     */
    protected void onClick(){}

    /**
     * A connection with Habbo has been started
     */
    protected void onStartConnection(){}

    /**
     * A connection with Habbo has ended
     */
    protected void onEndConnection(){}


    ExtensionInfo getInfoAnnotations() {
        return getClass().getAnnotation(ExtensionInfo.class);
    }
}
