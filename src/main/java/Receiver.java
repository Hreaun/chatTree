import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class Receiver extends Thread {
    private final int WAIT_TIME = 2_000;
    private final int BUF_SIZE = 1024;
    private final Node node;
    private final int loss;
    private final DatagramSocket socket;

    Receiver(Node node, int loss) {
        this.node = node;
        this.loss = loss;
        this.socket = node.getSocket();
    }

    void confirmMessage(DatagramPacket packet, UUID messageId) {
        InetAddress address = packet.getAddress();
        int port = packet.getPort();
        byte[] ack = ByteBuffer.allocate(messageId.toString().getBytes(StandardCharsets.UTF_8).length)
                .put(messageId.toString().getBytes(StandardCharsets.UTF_8)).array();

        packet = new DatagramPacket(ack, ack.length, address, port);

        try {
            socket.send(packet);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    void resendMessages() { // npe
        Map<InetSocketAddress, List<UUID>> sentMessages = node.getSentMessages();
        Map<UUID, String> messages = node.getMessages();
        synchronized (node.getSentMessages()) {
            for (Map.Entry<InetSocketAddress, List<UUID>> sentMsgEntry : sentMessages.entrySet()) {
                synchronized (node.copyMessageIds(sentMsgEntry.getKey())) {
                    for (UUID msgId : sentMsgEntry.getValue()) {
                        String msg = messages.get(msgId);
                        System.out.println("resent " + msg + " " + msgId);
                        byte[] buf = node.wrapMessage(msgId, msg);
                        try {
                            socket.send(new DatagramPacket(buf, buf.length,
                                    sentMsgEntry.getKey().getAddress(), sentMsgEntry.getKey().getPort()));
                        } catch (IOException e) {
                            System.out.println(e.getMessage());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void run() {
        Random random = new Random();
        byte[] buf = new byte[BUF_SIZE];
        while (!isInterrupted()) {
            resendMessages();
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.setSoTimeout(WAIT_TIME);
                socket.receive(packet);
                if (random.nextInt(100) < loss) {
                    continue;
                }
            } catch (SocketTimeoutException e) {
                continue;
            } catch (IOException e) {
                System.out.println(e.getMessage());
                return;
            }

            node.addNeighbor((InetSocketAddress) packet.getSocketAddress());

            byte[] UUIDbytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
            if (packet.getLength() > UUIDbytes.length) {
                UUID messageId = node.unwrapMessage(packet, buf);
                confirmMessage(packet, messageId);
            } else if (packet.getLength() == UUIDbytes.length) {
                node.ackMessage((InetSocketAddress) packet.getSocketAddress(), packet);
            }
        }
    }
}
