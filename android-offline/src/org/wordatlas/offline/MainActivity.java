package org.wordatlas.offline;

import android.app.Activity;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.net.Uri;
import android.content.ClipData;
import android.util.Base64;
import java.io.File;
import java.io.FileOutputStream;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.CookieManager;
import android.view.KeyEvent;
import android.graphics.Color;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final String ORIGIN = "appassets.androidplatform.net";
    private static final String REPO_PATH = "/cnrock/word-atlas";
    private static final int PICK_BACKUP = 701;
    private static final int SAVE_BACKUP = 702;
    private WebView web;
    private ValueCallback<Uri[]> pendingFile;
    private byte[] pendingBackup;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        // Target 35+ draws edge-to-edge; keep the offline UI within system bars.
        web.setFitsSystemWindows(true);
        web.setBackgroundColor(Color.rgb(246, 244, 237));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        // Android's document picker returns content:// URIs for imported backups.
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new BackupBridge(), "WordAtlasAndroid");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFile != null) pendingFile.onReceiveValue(null);
                pendingFile = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                // Some document providers label .json files as octet-stream.
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                try { startActivityForResult(intent, PICK_BACKUP); }
                catch (ActivityNotFoundException e) { pendingFile = null; callback.onReceiveValue(null); return false; }
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"https".equals(uri.getScheme()) || !ORIGIN.equals(uri.getHost())) return denied();
                String path = uri.getEncodedPath();
                if (path == null || !path.startsWith("/assets/") || path.contains("%") || path.contains("\\") || path.contains("..")) return denied();
                String asset = "web/" + path.substring("/assets/".length());
                if (asset.endsWith("/")) asset += "index.html";
                try {
                    InputStream stream = getAssets().open(asset, 1);
                    String mime = mime(asset);
                    Map<String, String> headers = new HashMap<String, String>();
                    headers.put("X-Content-Type-Options", "nosniff");
                    headers.put("Cache-Control", "public, max-age=31536000");
                    return new WebResourceResponse(mime, mime.startsWith("text/") || mime.equals("application/javascript") || mime.equals("application/json") ? "UTF-8" : null, 200, "OK", headers, stream);
                } catch (IOException e) { return denied(); }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return routeUrl(Uri.parse(url));
            }
            private boolean routeUrl(Uri u) {
                if ("https".equals(u.getScheme()) && ORIGIN.equals(u.getHost()) && u.getPath() != null && u.getPath().startsWith("/assets/")) return false;
                if ("https".equals(u.getScheme()) && "github.com".equals(u.getHost()) && u.getPath() != null &&
                        (u.getPath().equals(REPO_PATH) || u.getPath().startsWith(REPO_PATH + "/"))) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE)); }
                    catch (ActivityNotFoundException ignored) { }
                }
                return true;
            }
        });
        CookieManager.getInstance().setAcceptCookie(false);
        setContentView(web);
        web.loadUrl("https://" + ORIGIN + "/assets/index.html");
    }
    private final class BackupBridge {
        @JavascriptInterface public void shareImage(final String dataUrl, final String filename) {
            if (dataUrl == null || !dataUrl.startsWith("data:image/png;base64,") || dataUrl.length() > 16 * 1024 * 1024) return;
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (web == null || web.getUrl() == null || !web.getUrl().startsWith("https://" + ORIGIN + "/assets/")) return;
                    try {
                        byte[] png = Base64.decode(dataUrl.substring("data:image/png;base64,".length()), Base64.DEFAULT);
                        if (png.length < 8 || png.length > 12 * 1024 * 1024 || png[0] != (byte)0x89 || png[1] != 0x50 || png[2] != 0x4e || png[3] != 0x47) throw new IOException("Invalid PNG");
                        File card = new File(getCacheDir(), "shared-card.png");
                        try (FileOutputStream out = new FileOutputStream(card)) { out.write(png); }
                        Uri uri = new Uri.Builder().scheme("content").authority(ShareProvider.AUTHORITY).path("card.png").build();
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType("image/png");
                        send.putExtra(Intent.EXTRA_STREAM, uri);
                        send.setClipData(ClipData.newUri(getContentResolver(), "词光卡片", uri));
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(send, "分享词光卡片"));
                    } catch (Exception e) { android.widget.Toast.makeText(MainActivity.this, "无法分享卡片，请重试", android.widget.Toast.LENGTH_LONG).show(); }
                }
            });
        }

        @JavascriptInterface public void saveBackup(final String json) {
            if (json == null || json.length() > 30 * 1024 * 1024) return;
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (web == null || web.getUrl() == null || !web.getUrl().startsWith("https://" + ORIGIN + "/assets/")) return;
                    if (pendingBackup != null) return;
                    pendingBackup = json.getBytes(StandardCharsets.UTF_8);
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.setType("application/json");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.putExtra(Intent.EXTRA_TITLE, "word-atlas-backup-" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".json");
                    try { startActivityForResult(intent, SAVE_BACKUP); }
                    catch (ActivityNotFoundException e) { pendingBackup = null; android.widget.Toast.makeText(MainActivity.this, "无法打开系统文件保存界面", android.widget.Toast.LENGTH_LONG).show(); }
                }
            });
        }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_BACKUP) {
            if (pendingFile != null) {
                pendingFile.onReceiveValue(resultCode == RESULT_OK && data != null && data.getData() != null ? new Uri[]{data.getData()} : null);
                pendingFile = null;
            }
        } else if (requestCode == SAVE_BACKUP) {
            byte[] backup = pendingBackup;
            pendingBackup = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && backup != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new IOException("No output stream");
                    out.write(backup);
                    android.widget.Toast.makeText(this, "备份已保存", android.widget.Toast.LENGTH_SHORT).show();
                } catch (IOException e) { android.widget.Toast.makeText(this, "备份保存失败，请重试", android.widget.Toast.LENGTH_LONG).show(); }
            }
        }
    }
    private static WebResourceResponse denied() {
        Map<String, String> h = new HashMap<String, String>();
        h.put("X-Content-Type-Options", "nosniff");
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", h, new java.io.ByteArrayInputStream(new byte[0]));
    }
    private static String mime(String s) {
        if (s.endsWith(".html")) return "text/html";
        if (s.endsWith(".js")) return "application/javascript";
        if (s.endsWith(".css")) return "text/css";
        if (s.endsWith(".json") || s.endsWith(".webmanifest")) return "application/json";
        if (s.endsWith(".webp")) return "image/webp";
        if (s.endsWith(".png")) return "image/png";
        if (s.endsWith(".svg")) return "image/svg+xml";
        if (s.endsWith(".jpg") || s.endsWith(".jpeg")) return "image/jpeg";
        if (s.endsWith(".mp3")) return "audio/mpeg";
        if (s.endsWith(".woff2")) return "font/woff2";
        if (s.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web.canGoBack()) { web.goBack(); return true; }
        return super.onKeyDown(keyCode, e);
    }
    @Override public void onDestroy() {
        if (pendingFile != null) { pendingFile.onReceiveValue(null); pendingFile = null; }
        pendingBackup = null;
        web.removeJavascriptInterface("WordAtlasAndroid");
        web.destroy();
        super.onDestroy();
    }
}
