package com.danikula.videocache;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

/**
 * Stores source's info.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
public class SourceInfo {
    private static final String KEY_KEY = "kkkkkk";
    public final String url;
    public final long length;
    public final String mime;
    public final String key;
    public final boolean m3u8;

    public SourceInfo(String url, long length, String mime) {
        Uri uri = Uri.parse(url);
        key = uri.getQueryParameter(KEY_KEY);
        if (!TextUtils.isEmpty(key)) {
            if (url.contains(KEY_KEY + "=" + key + "&")) {
                url = url.replace(KEY_KEY + "=" + key + "&", "");
            } else {
                url = url.replace(KEY_KEY + "=" + key, "");
            }

            if (url.endsWith("?"))
                url = url.replace("?", "");
        }


        this.url = url;
        this.length = length;
        this.mime = mime;
        this.m3u8 = uri.getPath().contains(".m3u8");
    }

    @Override
    public String toString() {
        return "SourceInfo{" +
                "url='" + url + '\'' +
                ", length=" + length +
                ", mime='" + mime + '\'' +
                '}';
    }
}
