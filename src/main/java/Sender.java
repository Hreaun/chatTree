import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Scanner;
import java.util.UUID;

public class Sender {
    private final Node node;
    private final DatagramSocket socket;


    public Sender(Node node) {
        this.node = node;
        this.socket = node.getSocket();
    }


    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Connected. Type '/exit' to exit");
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
            node.putMessage(messageId, message);
            byte[] buf = node.wrapMessage(messageId, message);

            for (InetSocketAddress neighbor : node.getNeighbors()) {
                try {
                    socket.send(new DatagramPacket(buf, buf.length, neighbor.getAddress(), neighbor.getPort()));
                    node.putSentMessage(neighbor, messageId);
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }
        }

        socket.close();
    }
}
