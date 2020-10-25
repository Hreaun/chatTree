import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Scanner;
import java.util.UUID;

public class Sender {
    private final Node node;
    private final DatagramSocket socket;
    private final int BUF_SIZE;


    public Sender(Node node) {
        this.node = node;
        this.socket = node.getSocket();
        BUF_SIZE = node.getBUF_SIZE();
    }

    void sendSub() throws IOException {
        if (node.getNeighbors().size() <= 1) {
            return;
        }
        InetSocketAddress sub = node.getNeighbors().get(0);
        byte[] buf = node.wrapSub(sub);

        for (InetSocketAddress neighbor : node.getNeighbors()) {
            if (!sub.equals(neighbor)) {
                socket.send(new DatagramPacket(buf, buf.length, neighbor.getAddress(), neighbor.getPort()));
            }
        }
    }


    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("You connected as " + node.getName() + ". Type '/exit' to exit.");
        try {
            while (true) {
                String message;
                do {
                    message = scanner.nextLine();
                } while (message.isEmpty() | message.isBlank());

                if (message.length() > BUF_SIZE - 100) {
                    System.out.println("Message must not exceed " + (BUF_SIZE - 100) + " bytes.");
                    continue;
                }

                if ("/exit".equals(message)) {
                    sendSub();
                    break;
                }
                System.out.println("You: " + message);

                UUID messageId = UUID.randomUUID();
                node.putMessage(messageId, message);

                byte[] buf = node.wrapMessage(messageId, message);

                for (InetSocketAddress neighbor : node.getNeighbors()) {
                    socket.send(new DatagramPacket(buf, buf.length, neighbor.getAddress(), neighbor.getPort()));
                    node.putSentMessage(neighbor, messageId);
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } finally {
            socket.close();
        }
    }
}
