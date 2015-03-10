package com;

/**
 * Created by dpoltavskyi on 09.03.15.
 */
public class RedirectCounter {
    private String url;
    private int count;

    public RedirectCounter(String url, int count) {
        this.url = url;
        this.count = count;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
