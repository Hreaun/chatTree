import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Node {
    private final long TIMEOUT = 100_000;
    private final int BUF_SIZE = 1024;
    private final String name;
    private final DatagramSocket socket;
    private final List<InetSocketAddress> neighbors;
    private final Map<InetSocketAddress, List<UUID>> sentMessages;      // хранит айди неподтвержденных сообщений соседа
    private final ConcurrentMap<InetSocketAddress, Long> lastMessageTime; // хранит время последнего сообщения от соседа
    private final ConcurrentMap<UUID, String> messages;
    private final List<UUID> rcvdMessages;
    private int ackCounter = 0;

    public Node(String name, int port) throws SocketException {
        this.name = name.substring(0, Math.min(name.length(), 30));
        this.socket = new DatagramSocket(port);

        neighbors = Collections.synchronizedList(new ArrayList<>());
        sentMessages = Collections.synchronizedMap(new HashMap<>());
        lastMessageTime = new ConcurrentHashMap<>();
        rcvdMessages = new ArrayList<>();
        messages = new ConcurrentHashMap<>();
    }

    public int getBUF_SIZE() {
        return BUF_SIZE;
    }

    public String getName() {
        return name;
    }

    public Map<InetSocketAddress, List<UUID>> getSentMessages() {
        Map<InetSocketAddress, List<UUID>> sentMessages;
        synchronized (this.sentMessages) {
            sentMessages = this.sentMessages;
        }
        return sentMessages;
    }

    public List<InetSocketAddress> getNeighbors() {
        List<InetSocketAddress> neighbors;
        synchronized (this.neighbors) {
            neighbors = this.neighbors;
        }
        return neighbors;
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    public Map<UUID, String> getMessages() {
        return messages;
    }

    public List<UUID> getMessageIds(InetSocketAddress neighbor) {
        List<UUID> uuids;
        synchronized (sentMessages) {
            uuids = sentMessages.get(neighbor);
        }
        return uuids;
    }

    public void connect(String ip, int port) throws IllegalArgumentException {
        neighbors.add(new InetSocketAddress(ip, port));
    }

    public byte[] wrapSub(InetSocketAddress sub) {
        byte[] buf = ByteBuffer.allocate(Byte.BYTES + sub.getAddress().toString().getBytes(StandardCharsets.UTF_8).length
                + Integer.BYTES)
                .put((byte) MessageType.SUB.getValue())
                .putInt(sub.getPort())
                .put(sub.getHostString().getBytes(StandardCharsets.UTF_8))
                .array();
        return buf;
    }

    public InetSocketAddress unwrapSub(DatagramPacket packet, byte[] buf) {
        int port = ByteBuffer.wrap(Arrays.copyOfRange(buf, Byte.BYTES, Integer.BYTES + Byte.BYTES)).getInt();
        String ip = new String(buf, Byte.BYTES + Integer.BYTES,
                packet.getLength() - (Byte.BYTES + Integer.BYTES), StandardCharsets.UTF_8);

        return new InetSocketAddress(ip, port);
    }

    public byte[] wrapAck(UUID messageId) {
        byte[] buf = ByteBuffer.allocate(Byte.BYTES + messageId.toString().getBytes(StandardCharsets.UTF_8).length)
                .put((byte) MessageType.ACK.getValue())
                .put(messageId.toString().getBytes(StandardCharsets.UTF_8)).array();

        return buf;
    }

    public byte[] wrapMessage(UUID messageId, String message) {
        byte[] buf = ByteBuffer.allocate(Byte.BYTES + messageId.toString().getBytes(StandardCharsets.UTF_8).length
                + Integer.BYTES + name.getBytes(StandardCharsets.UTF_8).length
                + message.getBytes(StandardCharsets.UTF_8).length)
                .put((byte) MessageType.REGULAR.getValue())
                .put(messageId.toString().getBytes(StandardCharsets.UTF_8))
                .putInt(name.length())
                .put(name.getBytes(StandardCharsets.UTF_8))
                .put(message.getBytes(StandardCharsets.UTF_8)).array();

        return buf;
    }

    public UUID unwrapMessage(DatagramPacket packet, byte[] buf) {
        byte[] UUIDbytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        UUID messageId = UUID.fromString(new String(buf, Byte.BYTES, UUIDbytes.length, StandardCharsets.UTF_8));
        int nameLength = ByteBuffer.wrap(Arrays.copyOfRange(buf, Byte.BYTES + UUIDbytes.length,
                Byte.BYTES + UUIDbytes.length + Integer.BYTES)).getInt();
        String name = new String(buf, Byte.BYTES + UUIDbytes.length + Integer.BYTES, nameLength, StandardCharsets.UTF_8);
        String message = new String(buf, Byte.BYTES + UUIDbytes.length + Integer.BYTES + nameLength,
                packet.getLength() - (Byte.BYTES + UUIDbytes.length + Integer.BYTES + nameLength), StandardCharsets.UTF_8);

        // проверка повторного принятия сообщения в случае потери пакета с подтверждением у отправителя
        if (!rcvdMessages.contains(messageId)) {
            rcvdMessages.add(messageId);
            System.out.println("From " + name + ": " + message);
        }

        return messageId;
    }

    public void putSentMessage(InetSocketAddress neighbor, UUID messageId) {
        synchronized (sentMessages) {
            sentMessages.putIfAbsent(neighbor, Collections.synchronizedList(new ArrayList<>()));
            synchronized (sentMessages.get(neighbor)) {
                if (!sentMessages.get(neighbor).contains(messageId)) {
                    sentMessages.get(neighbor).add(messageId);
                }
            }
        }
    }

    public void putMessage(UUID messageId, String message) {
        messages.put(messageId, message);
    }

    public void addNeighbor(InetSocketAddress neighbor) {
        synchronized (neighbors) {
            if (!neighbors.contains(neighbor)) {
                neighbors.add(neighbor);
            }
        }
    }

    public void updateNeighbors(InetSocketAddress oldParent, DatagramPacket packet, byte[] buf) {
        InetSocketAddress newParent = unwrapSub(packet, buf);
        addNeighbor(newParent);

        synchronized (neighbors) {
            neighbors.remove(oldParent);
        }
        synchronized (sentMessages) {
            sentMessages.remove(oldParent);
        }
        lastMessageTime.remove(oldParent);
    }

    public void updateTime(InetSocketAddress neighbor) {
        lastMessageTime.put(neighbor, System.currentTimeMillis());
    }

    void checkTimes() {
        Iterator<Map.Entry<InetSocketAddress, Long>> timesIter = lastMessageTime.entrySet().iterator();
        while (timesIter.hasNext()) {
            Map.Entry<InetSocketAddress, Long> timeEntry = timesIter.next();
            if (System.currentTimeMillis() - timeEntry.getValue() > TIMEOUT) {
                synchronized (neighbors) {
                    neighbors.remove(timeEntry.getKey());
                }
                synchronized (sentMessages) {
                    sentMessages.remove(timeEntry.getKey());
                }
                timesIter.remove();
            }
        }
    }

    void checkMessages() {
        Iterator<Map.Entry<UUID, String>> messagesIter = messages.entrySet().iterator();
        while (messagesIter.hasNext()) {
            int counter = 0;
            Map.Entry<UUID, String> msgEntry = messagesIter.next();
            synchronized (sentMessages) {
                for (Map.Entry<InetSocketAddress, List<UUID>> sentMsgEntry : sentMessages.entrySet()) {
                    synchronized (this.getMessageIds(sentMsgEntry.getKey())) {
                        if (sentMsgEntry.getValue().contains(msgEntry.getKey())) {
                            counter++;
                        }
                    }
                }
                // удаление подтвержденного всеми соседями сообщения
                if (counter == 0) {
                    messagesIter.remove();
                }
            }
        }
    }

    public void ackMessage(InetSocketAddress neighbor, DatagramPacket packet) {
        synchronized (neighbors) {
            synchronized (sentMessages) {
                if (sentMessages.get(neighbor)
                        .remove(UUID.fromString(new String(packet.getData(), Byte.BYTES,
                                packet.getLength() - Byte.BYTES, StandardCharsets.UTF_8)))) {
                    ackCounter++;
                }
                if (ackCounter >= neighbors.size()) {
                    checkMessages();
                    ackCounter = 0;
                }
            }
        }
    }
}
