package com.denizenscript.depenizen.bungee.packets.in;

import com.denizenscript.depenizen.bungee.DepenizenBungee;
import com.denizenscript.depenizen.bungee.DepenizenConnection;
import com.denizenscript.depenizen.bungee.PacketIn;
import com.denizenscript.depenizen.bungee.packets.out.YourInfoPacketOut;
import io.netty.buffer.ByteBuf;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;

public class MyInfoPacketIn extends PacketIn {

    public static int PACKET_ID = 11;

    @Override
    public String getName() {
        return "MyInfo";
    }

    @Override
    public void process(DepenizenConnection connection, ByteBuf data) {
        if (data.readableBytes() < 4) {
            connection.fail("Invalid MyInfoPacket (bytes available: " + data.readableBytes() + ")");
            return;
        }
        int port = data.readInt();
        connection.serverPort = port;
//        for (ServerInfo server : ProxyServer.getInstance().getServers().values()) {
//            DepenizenBungee.instance.getLogger().info(server.toString());
//            if (server.getAddress().getAddress().equals(connection.serverAddress) && server.getAddress().getPort() == port) {
//                connection.thisServer = server;
//                break;
//            }
//        }
        Configuration c;
        try {
            c = ConfigurationProvider.getProvider(YamlConfiguration.class)
                    .load(new File(DepenizenBungee.instance.getDataFolder(), "config.yml"));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        Configuration allowedServers = (Configuration) c.get("Allowed Servers");
        for (String name : allowedServers.getKeys()) {
            Configuration allowedServer = (Configuration) allowedServers.get(name);
            // Create a fake server from DepenizenBungee config file.
            ServerInfo fakeServer = ProxyServer.getInstance().constructServerInfo(
                    name,
                    new InetSocketAddress(allowedServer.getString("ip"), allowedServer.getInt("port")),
                    "Fake MOTD",
                    false
            );
            if (fakeServer.getAddress().getAddress().equals(connection.serverAddress) && fakeServer.getAddress().getPort() == port) {
                connection.thisServer = fakeServer;
                break;
            }
        }
        if (connection.thisServer == null) {
            connection.fail("Invalid MyInfoPacket (unknown server, gave port '" + port + "')");
            return;
        }
        connection.sendPacket(new YourInfoPacketOut(connection.thisServer.getName()));
        connection.broadcastIdentity();
    }
}
