public enum MessageType {
    REGULAR(0), ACK(1), SUB(2);

    private final int value;
    MessageType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
