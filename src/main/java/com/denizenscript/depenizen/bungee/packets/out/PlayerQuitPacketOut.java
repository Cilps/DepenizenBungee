package com.denizenscript.depenizen.bungee.packets.out;

import com.denizenscript.depenizen.bungee.PacketOut;
import io.netty.buffer.ByteBuf;

import java.util.UUID;

public class PlayerQuitPacketOut extends PacketOut {

    public String name;

    public UUID uuid;

    public String ip;

    @Override
    public int getPacketId() {
        return 54;
    }

    @Override
    public void writeTo(ByteBuf buf) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
        writeString(buf, name);
        writeString(buf, ip);
    }
}
