package com.denizenscript.depenizen.bungee;

import com.denizenscript.depenizen.bungee.packets.in.*;
import com.denizenscript.depenizen.bungee.packets.out.*;
import io.netty.channel.Channel;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import net.md_5.bungee.netty.ChannelWrapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DepenizenBungee extends Plugin implements Listener {

    public static DepenizenBungee instance;

    public HashMap<Integer, PacketIn> packets = new HashMap<>();

    public final List<DepenizenConnection> connections = new ArrayList<>();

    public void addConnection(DepenizenConnection connection) {
        synchronized (connections) {
            connections.add(connection);
        }
    }

    public void removeConnection(DepenizenConnection connection) {
        synchronized (connections) {
            connections.remove(connection);
        }
    }

    public List<DepenizenConnection> getConnections() {
        synchronized (connections) {
            return new ArrayList<>(connections);
        }
    }

    public DepenizenConnection getConnectionByName(String name) {
        name = name.toLowerCase(Locale.ENGLISH);
        for (DepenizenConnection connection : getConnections()) {
            if (connection.thisServer != null && name.equals(connection.thisServer.getName().toLowerCase(Locale.ENGLISH))) {
                return connection;
            }
        }
        return null;
    }

    public void registerPackets() {
        packets.put(1, new KeepAlivePacketIn());
        packets.put(MyInfoPacketIn.PACKET_ID, new MyInfoPacketIn());
        packets.put(12, new ControlProxyPingPacketIn());
        packets.put(13, new ProxyPingResultPacketIn());
        packets.put(14, new RedirectPacketIn());
        packets.put(15, new ExecuteCommandPacketIn());
        packets.put(16, new ControlProxyCommandPacketIn());
        packets.put(17, new ProxyCommandResultPacketIn());
        packets.put(18, new ExecutePlayerCommandPacketIn());
    }

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("DepenizenBungee loading...");
        getProxy().getPluginManager().registerListener(this, this);
        registerPackets();
        ProxyServer.getInstance().getScheduler().schedule(this, new Runnable() {
            @Override
            public void run() {
                long curTime = System.currentTimeMillis();
                KeepAlivePacketOut packet = new KeepAlivePacketOut();
                for (DepenizenConnection connection : getConnections()) {
                    if (connection.thisServer == null) {
                        continue;
                    }
                    if (curTime > connection.lastPacketReceived + 20 * 1000) {
                        // 20 seconds without a packet = connection lost!
                        connection.fail("Connection time out.");
                    }
                    else {
                        connection.sendPacket(packet);
                    }
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
        if (!getDataFolder().exists())
            getDataFolder().mkdirs();
        File file = new File(getDataFolder(), "config.yml");

        if (!file.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                getLogger().severe("Failed to create config.");
                e.printStackTrace();
            }
        }
    }

    public void broadcastPacket(PacketOut packet) {
        for (DepenizenConnection connection : getConnections()) {
            connection.sendPacket(packet);
        }
    }

    @EventHandler
    public void onProxyPing(ProxyPingEvent event) {
        for (DepenizenConnection connection : getConnections()) {
            if (connection.controlsProxyPing && connection.isValid) {
                event.registerIntent(this);
                long id = connection.proxyPingId++;
                connection.proxyEventMap.put(id, event);
                ProxyPingPacketOut packet = new ProxyPingPacketOut();
                packet.id = id;
                packet.address = event.getConnection().getAddress().toString();
                packet.currentPlayers = event.getResponse().getPlayers().getOnline();
                packet.maxPlayers = event.getResponse().getPlayers().getMax();
                packet.motd = event.getResponse().getDescriptionComponent().toLegacyText();
                packet.protocol = event.getResponse().getVersion().getProtocol();
                packet.version = event.getResponse().getVersion().getName();
                connection.sendPacket(packet);
            }
        }
    }

    @EventHandler
    public void onServerSwitch(ServerConnectEvent event) {
        PlayerSwitchServerPacketOut packet = new PlayerSwitchServerPacketOut();
        packet.name = event.getPlayer().getName();
        packet.uuid = event.getPlayer().getUniqueId();
        packet.newServer = event.getTarget().getName();
        broadcastPacket(packet);
    }

    @EventHandler
    public void onPlayerJoin(PostLoginEvent event) {
        PlayerJoinPacketOut packet = new PlayerJoinPacketOut();
        packet.name = event.getPlayer().getName();
        packet.uuid = event.getPlayer().getUniqueId();
        packet.ip = event.getPlayer().getAddress().toString();
        packet.host = event.getPlayer().getPendingConnection().getVirtualHost().getHostString();
        broadcastPacket(packet);
    }

    @EventHandler
    public void onPlayerQuit(PlayerDisconnectEvent event) {
        PlayerQuitPacketOut packet = new PlayerQuitPacketOut();
        packet.name = event.getPlayer().getName();
        packet.uuid = event.getPlayer().getUniqueId();
        packet.ip = event.getPlayer().getAddress().toString();
        broadcastPacket(packet);
    }

    public static boolean proxyCommandNoDup = false;

    public static long proxyCommandId = 1;

    public HashMap<Long, CompletableFuture<String>> proxyCommandWaiters = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProxyCommand(ChatEvent event) {
        if (!event.isProxyCommand()) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        if (proxyCommandNoDup) {
            return;
        }
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (DepenizenConnection connection : getConnections()) {
            if (connection.controlsProxyCommand && connection.isValid) {
                long newId = proxyCommandId++;
                CompletableFuture<String> future = new CompletableFuture<>();
                futures.add(future);
                proxyCommandWaiters.put(newId, future);
                connection.sendPacket(new ProxyCommandPacketOut(newId, ((CommandSender) event.getSender()).getName(),
                        event.getSender() instanceof ProxiedPlayer ? ((ProxiedPlayer) event.getSender()).getUniqueId() : null, event.getMessage()));
            }
        }
        if (futures.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        String[] command = new String[1];
        command[0] = event.getMessage();
        Runnable finished = new Runnable() {
            @Override
            public void run() {
                proxyCommandNoDup = true;
                try {
                    ProxyServer.getInstance().getPluginManager().dispatchCommand((CommandSender) event.getSender(), command[0].substring(1));
                }
                finally {
                    proxyCommandNoDup = false;
                }
            }
        };
        ProxyServer.getInstance().getScheduler().runAsync(this, new Runnable() {
            @Override
            public void run() {
                for (CompletableFuture<String> future : futures) {
                    try {
                        String result = future.get(5, TimeUnit.SECONDS);
                        if (result != null) {
                            if (result.equalsIgnoreCase("cancelled")) {
                                return;
                            }
                            else if (result.startsWith("/")) {
                                command[0] = result;
                            }
                        }
                    }
                    catch (TimeoutException ex) {
                        DepenizenBungee.instance.getLogger().info("Proxy ChatEvent TimeoutException");
                        continue;
                    }
                    catch (ExecutionException ex) {
                        DepenizenBungee.instance.getLogger().info("Proxy ChatEvent ExecutionException");
                        ex.printStackTrace();
                        return;
                    }
                    catch (InterruptedException ex) {
                        DepenizenBungee.instance.getLogger().info("Proxy ChatEvent InterruptedException");
                        ex.printStackTrace();
                        return;
                    }
                }
                ProxyServer.getInstance().getScheduler().schedule(DepenizenBungee.this, finished, 0, TimeUnit.SECONDS);
            }
        });
    }

    @EventHandler
    public void onPlayerHandshake(PlayerHandshakeEvent event) {
        InitialHandler handler = (InitialHandler) event.getConnection();
        // Only operate on connections started by Depenizen
        if (!handler.getExtraDataInHandshake().equals("\0depen")) {
            return;
        }
        getLogger().info("Depenizen handshake seen from: " + handler.getAddress());
        final InetAddress address = handler.getAddress().getAddress();
        if (!address.isLoopbackAddress()) { // Localhost is always allowed
            boolean isValid = false;
//            for (ServerInfo info : getProxy ().getServers().values()) {
//                if (info.getAddress().getAddress().equals(address)) {
//                    isValid = true;
//                    break;
//                }
//            }
            Configuration c;
            try {
                c = ConfigurationProvider.getProvider(YamlConfiguration.class)
                        .load(new File(getDataFolder(), "config.yml"));
            } catch (IOException e) {
                getLogger().severe("Could not load config.");
                e.printStackTrace();
                return;
            }
            try {
                Configuration allowedServers = (Configuration) c.get("Allowed Servers");

                for (String name : allowedServers.getKeys()) {
                    Configuration server = (Configuration) allowedServers.get(name);
                    InetAddress ip = InetAddress.getByName(server.getString("ip"));
                    if (ip.equals(address)) {
                        isValid = true;
                        break;
                    }
                }
            } catch (Exception e) {
                getLogger().severe("Couldn't construct address.");
                e.printStackTrace();
            }
            if (!isValid) {
                getLogger().warning("Denied invalid or fake Depenizen connection from: " + handler.getAddress() + ".");
                getLogger().info("If this was meant to be a real connection, ensure the IP and the port are correctly set in the config.");
                return;
            }
        }
        final Channel channel;
        try {
            // Set 'closed' to true, so Bungee ignores the connection
            ChannelWrapper wrapper = (ChannelWrapper) INITIALHANDLER_GET_CH.invoke(handler);
            CHANNELWRAPPER_SET_CLOSED.invoke(wrapper, true);
            // Get the underlying netty channel to do with as we please
            channel = (Channel) CHANNELWRAPPER_GET_CH.invoke(wrapper);
            // Add it to the list, for the network thread to handle
            final DepenizenConnection connection = new DepenizenConnection();
            getProxy().getScheduler().schedule(this, new Runnable() {
                @Override
                public void run() {
                    try {
                        connection.build(channel, address);
                    }
                    catch (Throwable ex) {
                        getLogger().severe("Exception while handling Depenizen connection build...");
                        ex.printStackTrace();
                    }
                }
            }, 1, TimeUnit.MILLISECONDS);
        }
        catch (Throwable ex) {
            getLogger().severe("Exception while handling Depenizen handshake...");
            ex.printStackTrace();
            return;
        }
    }

    public static MethodHandle INITIALHANDLER_GET_CH = ReflectionHelper.getGetter(InitialHandler.class, "ch");
    public static MethodHandle CHANNELWRAPPER_SET_CLOSED = ReflectionHelper.getSetter(ChannelWrapper.class, "closed");
    public static MethodHandle CHANNELWRAPPER_GET_CH = ReflectionHelper.getGetter(ChannelWrapper.class, "ch");

}
