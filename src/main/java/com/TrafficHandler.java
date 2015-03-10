package com;

import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;

/**
 * Created by dpoltavskyi on 09.03.15.
 */
public class TrafficHandler extends ChannelTrafficShapingHandler {
    private static Vector<ConnectionDetails> connections = new Vector<ConnectionDetails>();

    public TrafficHandler(long checkInterval) {
        super(checkInterval);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if ( msg instanceof HttpRequest ) {
            ConnectionDetails connection = new ConnectionDetails();
            String uri = ((HttpRequest) msg).getUri();
            connection.setSourceIp(((InetSocketAddress) ctx.channel().remoteAddress()).getHostString());
            connection.setUri(uri);
            connection.setTimestamp(generateTimestamp());
            trafficHandling(connection);
            addNewConnection(connection);
        }

        super.channelRead(ctx, msg);
    }

    private static void addNewConnection(ConnectionDetails connection) {
        connections.add(connection);
        if (connections.size() > 16) {
            connections.remove(0);
        }
    }

    public static List<ConnectionDetails> getConnectionList() {
        List<ConnectionDetails> list = new ArrayList<ConnectionDetails>(connections);

        return list;
    }

    private void trafficHandling(ConnectionDetails connectionDetails) {
        TrafficCounter tc = trafficCounter();

        connectionDetails.setBytesSent(tc.cumulativeWrittenBytes());
        connectionDetails.setBytesReceived(tc.cumulativeReadBytes());
        connectionDetails.setSpeed(tc.getRealWriteThroughput());
        connectionDetails.setTimestamp(generateTimestamp());
    }

    private String generateTimestamp() {
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

        return sdf.format(date);
    }
}
