package g_earth.protocol;

import g_earth.misc.StringifyAble;

public class HMessage implements StringifyAble {

    public enum Side {
        TOSERVER,
        TOCLIENT
    }

    private HPacket hPacket;
    private Side side;
    private int index;

    private boolean isBlocked;

    public HMessage(String fromString) {
        constructFromString(fromString);
    }

    public HMessage(HPacket packet, Side side, int index) {
        this.side = side;
        this.hPacket = packet;
        this.index = index;
        isBlocked = false;
    }

    public int getIndex() {
        return index;
    }

    public void setBlocked(boolean block) {
        isBlocked = block;
    }
    public boolean isBlocked() {
        return isBlocked;
    }

    public HPacket getPacket() {
        return hPacket;
    }
    public Side getDestination() {
        return side;
    }

    public boolean isCorrupted() {
        return hPacket.isCorrupted();
    }


    @Override
    public String stringify() {
        String s = (isBlocked ? "1" : "0") + "\t" + index + "\t" + side.name() + "\t" + hPacket.stringify();
        return s;
    }

    @Override
    public void constructFromString(String str) {
        String[] parts = str.split("\t", 4);
        this.isBlocked = parts[0].equals("1");
        this.index = Integer.parseInt(parts[1]);
        this.side = parts[2].equals("TOCLIENT") ? Side.TOCLIENT : Side.TOSERVER;
        HPacket p = new HPacket(new byte[0]);
        p.constructFromString(parts[3]);
        this.hPacket = p;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof HMessage)) return false;

        HMessage message = (HMessage) obj;

        return message.hPacket.equals(hPacket) && (side == message.side) && (index == message.index);
    }

//    public static void main(String[] args) {
//        HPacket packet3 = new HPacket(81, new byte[]{0,0,0,1,0,0});
//
//        HPacket packet = new HPacket(82, new byte[]{0,0,0,1,0,0});
//        HMessage message = new HMessage(packet, Side.TOSERVER, 5);
//
//        String stringed = message.stringify();
//
//        HMessage message2 = new HMessage(stringed);
//        HPacket packet1 = message2.getPacket();
//
//        System.out.println(message.equals(message2));
//        System.out.println(packet.equals(packet1));
//        System.out.println(packet.equals(packet3));
//    }
}
