package com.danikula.videocache;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.danikula.videocache.file.FileCache;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static com.danikula.videocache.Preconditions.checkNotNull;

/**
 * Client for {@link HttpProxyCacheServer}
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
final class HttpProxyCacheServerClients {

    private final AtomicInteger clientsCount = new AtomicInteger(0);
    private final String url;
    private volatile HttpProxyCache proxyCache;
    private final List<CacheListener> listeners = new CopyOnWriteArrayList<>();
    private final CacheListener uiCacheListener;
    private final Config config;
    private final Context context;

    public HttpProxyCacheServerClients(Context context, String url, Config config) {
        this.context = checkNotNull(context);
        this.url = checkNotNull(url);
        this.config = checkNotNull(config);
        this.uiCacheListener = new UiListenerHandler(url, listeners);
    }

    public void processRequest(GetRequest request, Socket socket) throws ProxyCacheException, IOException {
        startProcessRequest();
        try {
            clientsCount.incrementAndGet();
            proxyCache.processRequest(request, socket);
        } finally {
            finishProcessRequest();
        }
    }

    private synchronized void startProcessRequest() throws ProxyCacheException {
        proxyCache = proxyCache == null ? newHttpProxyCache() : proxyCache;
    }

    private synchronized void finishProcessRequest() {
        if (clientsCount.decrementAndGet() <= 0) {
            proxyCache.shutdown();
            proxyCache = null;
        }
    }

    public void registerCacheListener(CacheListener cacheListener) {
        listeners.add(cacheListener);
    }

    public void unregisterCacheListener(CacheListener cacheListener) {
        listeners.remove(cacheListener);
    }

    public void shutdown() {
        listeners.clear();
        if (proxyCache != null) {
            proxyCache.registerCacheListener(null);
            proxyCache.shutdown();
            proxyCache = null;
        }
        clientsCount.set(0);
    }

    public int getClientsCount() {
        return clientsCount.get();
    }

    public void incrementAndGet() {
        clientsCount.incrementAndGet();
    }

    public void decrementAndGet() {
        clientsCount.decrementAndGet();
    }

    private HttpProxyCache newHttpProxyCache() throws ProxyCacheException {
        HttpUrlSource source = new HttpUrlSource(url, config.sourceInfoStorage, config.headerInjector);
        FileCache cache = new FileCache(config.generateCacheFile(url), config.diskUsage);
        HttpProxyCache httpProxyCache;
        if (source.isM3U8()) {
            httpProxyCache = new M3U8ProxyCache(context, source, cache);
        } else {
            httpProxyCache = new HttpProxyCache(source, cache);
        }

        httpProxyCache.registerCacheListener(uiCacheListener);
        return httpProxyCache;
    }

    private static final class UiListenerHandler extends Handler implements CacheListener {

        private final String url;
        private final List<CacheListener> listeners;
        private static final int MSG_PROGRESS = 0;
        private static final int MSG_M3U8_ITEM = 1;

        public UiListenerHandler(String url, List<CacheListener> listeners) {
            super(Looper.getMainLooper());
            this.url = url;
            this.listeners = listeners;
        }

        @Override
        public void onCacheAvailable(File file, String url, int percentsAvailable) {
            Message message = obtainMessage();
            message.what = MSG_PROGRESS;
            message.arg1 = percentsAvailable;
            message.obj = file;
            sendMessage(message);
        }

        @Override
        public boolean onM3U8ItemDecrypt(M3U8ProxyCache.CacheItem item) {
            Message message = obtainMessage();
            message.what = MSG_M3U8_ITEM;
            message.obj = item;
            sendMessage(message);
            return true;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PROGRESS:
                    for (CacheListener cacheListener : listeners) {
                        cacheListener.onCacheAvailable((File) msg.obj, url, msg.arg1);
                    }
                    break;

                case MSG_M3U8_ITEM:
                    for (CacheListener cacheListener : listeners) {
                        cacheListener.onM3U8ItemDecrypt((M3U8ProxyCache.CacheItem) msg.obj);
                    }
                    break;
            }

        }
    }
}
