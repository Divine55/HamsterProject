package com;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.ServerCookieEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;

import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

/**
 * Created by dpoltavskyi on 09.03.15.
 */
public class ServerHandler extends SimpleChannelInboundHandler<Object> {
    private static Timer timer = new HashedWheelTimer();
    private static Set<Channel> channels = new HashSet<Channel>();
    private static List<UniqueRequestCounter> uniqueIps = new ArrayList<UniqueRequestCounter>();
    private static List<RedirectCounter> urlCount = new ArrayList<RedirectCounter>();
    private static List<ConnectionDetails> connections = null;
    private static int requestCount = 0;
    private final StringBuilder buf = new StringBuilder();
    private HttpRequest request;
    private boolean isHtml = false;

    public ServerHandler() {
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        activeChannelsCount(ctx);

        if (msg instanceof HttpRequest) {
            requestCount++;
            HttpRequest request = this.request = (HttpRequest) msg;

            if (HttpHeaders.is100ContinueExpected(request)) {
                send100Continue(ctx);
            }

            InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
            countUniqueIp(address.getHostString());

            if ( request.getUri().equals("/hello") ) {
                isHtml = false;
                buf.setLength(0);
                buf.append("Hello World\r\n");
                timer.newTimeout(new HelloTask(this, ctx, request), 10, TimeUnit.SECONDS);
            } else if ( request.getUri().contains("/redirect") ) {
                isHtml = false;
                buf.setLength(0);
                QueryStringDecoder qsd = new QueryStringDecoder(request.getUri());
                List<String> redirectUrl = qsd.parameters().get("url");
                if ( redirectUrl == null || !redirectUrl.get(0).contains("http://") ) {
                    buf.append("Invalid URL or parameter name!! URL name should start with \"http://\"");
                    sendResponse(ctx);
                } else {
                    redirectCount(redirectUrl.get(0));
                    FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, FOUND);
                    response.headers().set(LOCATION, redirectUrl.get(0));
                    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                }
            } else if ( request.getUri().equals("/status") ) {
                isHtml = true;
                buf.setLength(0);
                generateStatusPage();
                sendResponse(ctx);
            } else {
                isHtml = true;
                buf.setLength(0);
                generateWelcomePage();
                sendResponse(ctx);
            }
        }
    }

    public void sendResponse(ChannelHandlerContext ctx) {
        appendDecoderResult(buf, request);
        if (!writeResponse(request, ctx)) {
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static void appendDecoderResult(StringBuilder buf, HttpObject o) {
        DecoderResult result = o.getDecoderResult();
        if (result.isSuccess()) {
            return;
        }

        buf.append("Decoder failure cause: ");
        buf.append(result.cause());
        buf.append("\r\n");
    }

    private boolean writeResponse(HttpObject currentObj, ChannelHandlerContext ctx) {
        boolean keepAlive = HttpHeaders.isKeepAlive(request);

        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, currentObj.getDecoderResult().isSuccess() ? OK : BAD_REQUEST,
                Unpooled.copiedBuffer(buf.toString(), CharsetUtil.UTF_8));

        if ( !isHtml ) {
            response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
        } else {
            response.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
        }

        if (keepAlive) {
            response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
            response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }

        String cookieString = request.headers().get(COOKIE);
        if (cookieString != null) {
            Set<Cookie> cookies = CookieDecoder.decode(cookieString);
            if (!cookies.isEmpty()) {
                for (Cookie cookie: cookies) {
                    response.headers().add(SET_COOKIE, ServerCookieEncoder.encode(cookie));
                }
            }
        } else {
            response.headers().add(SET_COOKIE, ServerCookieEncoder.encode("key1", "value1"));
            response.headers().add(SET_COOKIE, ServerCookieEncoder.encode("key2", "value2"));
        }

        ctx.write(response);

        return keepAlive;
    }

    private void activeChannelsCount(ChannelHandlerContext ctx) {
        synchronized (channels) {
            if (!channels.isEmpty()) {
                for (Iterator<Channel> iterator = channels.iterator(); iterator.hasNext(); ) {
                    if (!iterator.next().isOpen()) {
                        iterator.remove();
                    }
                }
            }

            channels.add(ctx.channel());
        }
    }

    private void generateStatusPage() {
        buf.append("<!doctype html>\n" +
                "<html>\n" +
                "<head>\n" +
                "<meta charset=\"utf-8\">\n" +
                "<title>Server status</title>\n" +
                "<style>\n" +
                "h1 {\n" +
                "text-align: center;\n" +
                "}\n" +
                "h4 {\n" +
                "text-align: center;\n" +
                "}\n" +
                "table {\n" +
                "width: 100%;\n" +
                "border: 2px solid black;\n" +
                "text-align: center;\n" +
                "}\n" +
                "th {\n" +
                "border: 1px solid black;\n" +
                "padding: 3px;\n" +
                "}\n" +
                "td {\n" +
                "border: 1px solid black;\n" +
                "padding: 3px;\n" +
                "}\n" +
                ".table1 {\n" +
                "width: 60%;\n" +
                "margin: auto;\n" +
                "margin-bottom: 30px;\n" +
                "}\n" +
                ".table2 {\n" +
                "width: 50%;\n" +
                "margin: auto;\n" +
                "margin-bottom: 30px;\n" +
                "}\n" +
                ".table3 {\n" +
                "width: 100%;\n" +
                "margin: auto;\n" +
                "}\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<h1>Server statistics page</h1>\n" +
                "<hr/>\n" +
                "<h4>Number of all requests: " + requestCount + "</h4>\n" +
                "<h4>Number of unique requests: " + uniqueIps.size() + "</h4>\n" +
                "<h4>Number of open connections: " + channels.size() + "</h4>\n" +
                "<div class=\"table1\">\n" +
                "<table>\n" +
                "<caption>Server request count (per IP)</caption>\n" +
                "<tbody>\n" +
                "<tr>\n" +
                "<th width=\"25%\">IP</th>\n" +
                "<th width=\"25%\">Count</th>\n" +
                "<th width=\"50%\">Timestamp</th>\n" +
                "</tr>\n");

        for ( UniqueRequestCounter counter: uniqueIps ) {
            buf.append("<tr><td>" + counter.getIp() + "</td><td>" + counter.getCount() +
                    "</td><td>" + counter.getTimestamp() + "</td></tr>");
        }
        buf.append("</tbody>\n" +
                "</table>\n" +
                "</div>\n" +
                "<div class=\"table2\">\n" +
                "<table>\n" +
                "<caption>Redirects count (per URL)</caption>\n" +
                "<tbody>\n" +
                "<tr>\n" +
                "<th width=\"70%\">URL</th>\n" +
                "<th width=\"30%\">Count</th>\n" +
                "</tr>\n");

        for ( RedirectCounter counter: urlCount ) {
            buf.append("<tr><td>" + counter.getUrl() + "</td><td>" + counter.getCount() + "</td></tr>");
        }

        buf.append("</tbody>\n" +
                "</table>\n" +
                "</div>\n" +
                "<div class=\"table3\">\n" +
                "<table>\n" +
                "<caption>Last 16 connections details</caption>\n" +
                "<tbody>\n" +
                "<tr>\n" +
                "<th width=\"23%\">Source IP</th>\n" +
                "<th width=\"23%\">URI</th>\n" +
                "<th width=\"24%\">Timestamp</th>\n" +
                "<th width=\"10%\">Bytes sent</th>\n" +
                "<th width=\"10%\">Bytes received</th>\n" +
                "<th width=\"10%\">Speed(bytes/sec)</th>\n" +
                "</tr>\n");

        connections = TrafficHandler.getConnectionList();
        for ( ConnectionDetails con: connections) {
            buf.append("<tr><td>" + con.getSourceIp() + "</td><td>" + con.getUri() + "</td><td>" +
                    con.getTimestamp() + "</td><td>" + con.getBytesSent() + "</td><td>" +
                    con.getBytesReceived() + "</td><td>" + con.getSpeed() + "</td></tr>");
        }

        buf.append("</tbody>\n" +
                "</table>\n" +
                "</div>\n" +
                "</body>\n" +
                "</html>\n");
    }

    private void generateWelcomePage() {
        buf.append("<!doctype html>\n" +
                "<html>\n" +
                "<head>\n" +
                "<meta charset=\"utf-8\">\n" +
                "<title>Welcome page</title>\n" +
                "<style>\n" +
                "h1 {\n" +
                "text-align: center;\n" +
                "}\n" +
                ".wrap {\n" +
                "width: 60%;\n" +
                "margin: auto;\n" +
                "}\n" +
                "p {\n" +
                "font-size: 18px;\n" +
                "line-height: 25px;\n" +
                "}\n" +
                "a {\n" +
                "font-size: 18px;\n" +
                "line-height: 25px;\n" +
                "}\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<h1>Welcome to the web server</h1>\n" +
                "<hr/>\n" +
                "<div class=\"wrap\">\n" +
                "<p>This server provides service on the three urls listed below <br/> " +
                "Please, click on one of the links below</p>\n" +
                "<a href=\"http://localhost:8080/hello\" target=\"_blank\">Hello page (time out 10 seconds)</a> <br/>\n" +
                "<a href=\"http://localhost:8080/redirect?url=http://www.google.com\" target=\"_blank\">Redirect " +
                "(to google in this example, but you can enter your url)</a> <br/>\n" +
                "<a href=\"http://localhost:8080/status\" target=\"_blank\">Server statistics page</a>\n" +
                "</div>\n" +
                "</body>\n" +
                "</html>\n");
    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE);
        ctx.write(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    private String generateTimestamp() {
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

        return sdf.format(date);
    }

    private void countUniqueIp(String ip) {
        synchronized (uniqueIps) {
            if (!uniqueIps.isEmpty()) {
                for (UniqueRequestCounter counter : uniqueIps) {
                    if (counter.getIp().equals(ip)) {
                        counter.setTimestamp(generateTimestamp());
                        counter.setCount(counter.getCount() + 1);
                        return;
                    }
                }
            }

            uniqueIps.add(new UniqueRequestCounter(ip, generateTimestamp(), 1));
        }
    }

    private void redirectCount(String url) {
        synchronized (urlCount) {
            if (!urlCount.isEmpty()) {
                for (RedirectCounter counter : urlCount) {
                    if (counter.getUrl().equals(url)) {
                        counter.setCount(counter.getCount() + 1);
                        return;
                    }
                }
            }

            urlCount.add(new RedirectCounter(url, 1));
        }
    }
}
