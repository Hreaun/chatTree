import java.net.SocketException;

public class Main {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Enter name, loss percentage and port for the node.");
            return;
        }

        Node node;
        Sender sender;
        Receiver receiver;
        try {
            node = new Node(args[0], Integer.parseInt(args[2]));
            receiver = new Receiver(node, Integer.parseInt(args[1]));
            sender = new Sender(node);
        } catch (NumberFormatException e) {
            System.out.println("Enter name, loss percentage and port for the node.");
            return;

        } catch (SocketException e) {
            return;
        }

        if (args.length == 5) {
            String parentIp = args[3];
            try {
                int parentPort = Integer.parseInt(args[4]);
                node.connect(parentIp, parentPort);
            } catch (IllegalArgumentException e) {
                System.out.println("Enter correct parent's port");
                return;
            }
        }


        receiver.start();
        sender.start();

        receiver.interrupt();
        try {
            receiver.join();
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        }

    }
}
