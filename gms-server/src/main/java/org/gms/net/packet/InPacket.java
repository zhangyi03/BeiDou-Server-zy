package org.gms.net.packet;

import java.awt.*;

public interface InPacket extends Packet {
    byte readByte();
    short readUnsignedByte();
    short readShort();
    int readInt();
    long readLong();
    Point readPos();
    String readString();
    byte[] readBytes(int numberOfBytes);
    void skip(int numberOfBytes);
    int available();
    void seek(int byteOffset);
    int getPosition();

    /**
     * 复制一份未读报文（readerIndex 归零）。机器人录制回放要用。
     */
    InPacket copy();
}
