package com;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.traffic.AbstractTrafficShapingHandler;

/**
 * Created by dpoltavskyi on 09.03.15.
 */
public class ServerInitializer extends ChannelInitializer<SocketChannel> {

    public ServerInitializer() {
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        p.addLast(new HttpRequestDecoder());
        p.addLast(new HttpResponseEncoder());
        p.addLast(new TrafficHandler(AbstractTrafficShapingHandler.DEFAULT_CHECK_INTERVAL));
        p.addLast(new ServerHandler());
    }
}
