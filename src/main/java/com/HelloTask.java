package com;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObject;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;

/**
 * Created by dpoltavskyi on 09.03.15.
 */
public class HelloTask implements TimerTask {
    private ServerHandler handler;
    private ChannelHandlerContext ctx;
    private HttpObject httpObject;

    public HelloTask(ServerHandler handler, ChannelHandlerContext ctx, HttpObject httpObject) {
        this.handler = handler;
        this.ctx = ctx;
        this.httpObject = httpObject;
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        handler.sendResponse(ctx);
        ctx.flush();
    }

    public ServerHandler getHandler() {
        return handler;
    }

    public void setHandler(ServerHandler handler) {
        this.handler = handler;
    }

    public ChannelHandlerContext getCtx() {
        return ctx;
    }

    public void setCtx(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    public HttpObject getHttpObject() {
        return httpObject;
    }

    public void setHttpObject(HttpObject httpObject) {
        this.httpObject = httpObject;
    }
}
