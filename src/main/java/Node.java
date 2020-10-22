import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Node {
    String name;
    int loss;
    DatagramSocket socket;
    final List<InetSocketAddress> neighbors;
    Map<InetSocketAddress, ArrayList<UUID>> sentMessages;
    List<UUID> rcvdMessages;
    byte[] buf;

    public Node(String name, int loss, int port) {
        this.name = name;
        this.loss = loss;
        try {
            this.socket = new DatagramSocket(port);
        } catch (SocketException e) {
            System.out.println(e.getMessage());
        }
        neighbors = new ArrayList<>();
        sentMessages = new HashMap<>();
        rcvdMessages = new ArrayList<>();
    }

    public void connect(String ip, int port) throws IllegalArgumentException {
        neighbors.add(new InetSocketAddress(ip, port));
    }

    public void start() {
        MessageReceiver receiver = new MessageReceiver(this);
        Scanner scanner = new Scanner(System.in);
        receiver.start();
        System.out.println("Connected");
        while (true) {
            String message;

            do {
                message = scanner.nextLine();
            } while (message.isEmpty() | message.isBlank());

            if ("/exit".equals(message)) {
                break;
            }

            System.out.println("You: " + message);

            UUID messageId = UUID.randomUUID();

            buf = ByteBuffer.allocate(messageId.toString().getBytes(StandardCharsets.UTF_8).length + Integer.BYTES +
                    name.getBytes(StandardCharsets.UTF_8).length + message.getBytes(StandardCharsets.UTF_8).length)
                    .put(messageId.toString().getBytes(StandardCharsets.UTF_8))
                    .putInt(name.length())
                    .put(name.getBytes(StandardCharsets.UTF_8))
                    .put(message.getBytes(StandardCharsets.UTF_8)).array();

            for (InetSocketAddress neighbor : neighbors) {
                try {
                    socket.send(new DatagramPacket(buf, buf.length, neighbor.getAddress(), neighbor.getPort()));
                    sentMessages.putIfAbsent(neighbor, new ArrayList<>());
                    if (!sentMessages.get(neighbor).contains(messageId)) {
                        sentMessages.get(neighbor).add(messageId);
                    }
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }
        }

        receiver.interrupt();
        socket.close();
    }


}
