import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

public class MessageReceiver extends Thread {
    private final Node node;
    private final byte[] buf = new byte[1024];

    MessageReceiver(Node node) {
        this.node = node;
    }

    UUID getMessage(DatagramPacket packet) {
        byte[] UUIDbytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        UUID messageId = UUID.fromString(new String(buf, 0, UUIDbytes.length, StandardCharsets.UTF_8));
        int nameLength = ByteBuffer.wrap(Arrays.copyOfRange(buf, UUIDbytes.length,
                UUIDbytes.length + Integer.BYTES)).getInt();
        String name = new String(buf, UUIDbytes.length + Integer.BYTES, nameLength, StandardCharsets.UTF_8);
        String message = new String(buf, UUIDbytes.length + Integer.BYTES + nameLength,
                packet.getLength() - (UUIDbytes.length + Integer.BYTES + nameLength), StandardCharsets.UTF_8);

        if (!node.rcvdMessages.contains(messageId)) {
            node.rcvdMessages.add(messageId);
            System.out.println("From " + name + ": " + message);
        }

        return messageId;
    }

    void confirmMessage(DatagramPacket packet, UUID messageId) {
        InetAddress address = packet.getAddress();
        int port = packet.getPort();
        byte[] ack = ByteBuffer.allocate(messageId.toString().getBytes(StandardCharsets.UTF_8).length)
                .put(messageId.toString().getBytes(StandardCharsets.UTF_8)).array();

        packet = new DatagramPacket(ack, ack.length, address, port);

        try {
            node.socket.send(packet);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }


    @Override
    public void run() {
        while (!isInterrupted()) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                node.socket.receive(packet);
            } catch (IOException e) {
                System.out.println(e.getMessage());
                return;
            }

            if (!node.neighbors.contains(packet.getSocketAddress())) {
                node.neighbors.add((InetSocketAddress) packet.getSocketAddress());
            }

            byte[] UUIDbytes = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
            if (packet.getLength() > UUIDbytes.length) {
                UUID messageId = getMessage(packet);
                confirmMessage(packet, messageId);
            } else if (packet.getLength() == UUIDbytes.length) {
                node.sentMessages.get(packet.getSocketAddress())
                        .remove(UUID.fromString(new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8)));
            }


        }
    }
}
