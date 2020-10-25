import java.io.IOException;
import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class Receiver extends Thread {
    private final int WAIT_TIME = 2_000;
    private final int BUF_SIZE;
    private final Node node;
    private final int loss;
    private final DatagramSocket socket;

    Receiver(Node node, int loss) {
        this.node = node;
        this.loss = loss;
        this.socket = node.getSocket();
        BUF_SIZE = node.getBUF_SIZE();
    }

    void confirmMessage(DatagramPacket packet, UUID messageId) throws IOException {
        InetAddress address = packet.getAddress();
        int port = packet.getPort();

        byte[] ack = node.wrapAck(messageId);

        packet = new DatagramPacket(ack, ack.length, address, port);
        socket.send(packet);
    }

    void resendMessages() throws IOException {
        Map<InetSocketAddress, List<UUID>> sentMessages = node.getSentMessages();
        Map<UUID, String> messages = node.getMessages();
        synchronized (node.getSentMessages()) {
            for (Map.Entry<InetSocketAddress, List<UUID>> sentMsgEntry : sentMessages.entrySet()) {
                synchronized (node.getMessageIds(sentMsgEntry.getKey())) {
                    for (UUID msgId : sentMsgEntry.getValue()) {
                        String msg = messages.get(msgId);
                        byte[] buf = node.wrapMessage(msgId, msg);
                        socket.send(new DatagramPacket(buf, buf.length,
                                sentMsgEntry.getKey().getAddress(), sentMsgEntry.getKey().getPort()));
                    }
                }
            }
        }
    }

    @Override
    public void run() {
        Random random = new Random();
        byte[] buf = new byte[BUF_SIZE];
        try {
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
                }

                node.addNeighbor((InetSocketAddress) packet.getSocketAddress());
                node.updateTime((InetSocketAddress) packet.getSocketAddress());
                node.checkTimes();

                // прием обычного сообщения
                if (buf[0] == MessageType.REGULAR.getValue()) {
                    UUID messageId = node.unwrapMessage(packet, buf);
                    confirmMessage(packet, messageId);
                    continue;
                }

                // прием подтверждения
                if (buf[0] == MessageType.ACK.getValue()) {
                    node.ackMessage((InetSocketAddress) packet.getSocketAddress(), packet);
                    continue;
                }

                // прием заместителя
                if (buf[0] == MessageType.SUB.getValue()) {
                    node.updateNeighbors((InetSocketAddress) packet.getSocketAddress(), packet, buf);
                    System.out.println("Neighbor changed");
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}
