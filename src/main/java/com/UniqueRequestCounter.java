package com;

/**
 * Created by dpoltavskyi on 09.03.15.
 */
public class UniqueRequestCounter {
    private String ip;
    private String timestamp;
    private int count = 0;

    public UniqueRequestCounter(String ip, String timestamp, int count) {
        this.ip = ip;
        this.timestamp = timestamp;
        this.count = count;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
