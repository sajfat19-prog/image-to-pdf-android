package com.sajfat19.image2pdf;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Base64;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER = 1001;
    private ValueCallback<Uri[]> fileCallback;
    private WebView webView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUserAgentString(s.getUserAgentString() + " ImageToPdfAndroid/1.0");

        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u != null && "https".equalsIgnoreCase(u.getScheme()) &&
                        "sajfat19-prog.github.io".equalsIgnoreCase(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, FILE_CHOOSER);
                    return true;
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "لا يوجد مدير ملفات مناسب", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
            @Override public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress == 100) injectDownloadBridge();
            }
        });

        webView.loadUrl("https://sajfat19-prog.github.io/image-to-pdf/");
    }

    private void injectDownloadBridge() {
        String js = "javascript:(function(){if(window.__androidDownloadPatched)return;window.__androidDownloadPatched=true;" +
                "var oldClick=HTMLAnchorElement.prototype.click;HTMLAnchorElement.prototype.click=function(){" +
                "var a=this,h=a.href||'';if(h.indexOf('blob:')===0&&window.Android){" +
                "fetch(h).then(function(r){return r.blob()}).then(function(b){var rd=new FileReader();" +
                "rd.onloadend=function(){Android.saveBase64(rd.result.split(',')[1],a.download||'document.pdf',b.type||'application/pdf')};" +
                "rd.readAsDataURL(b)}).catch(function(){oldClick.call(a)});return;}return oldClick.call(this);};" +
                "})()";
        webView.evaluateJavascript(js, null);
    }

    public class AndroidBridge {
        @JavascriptInterface public void saveBase64(String base64, String fileName, String mimeType) {
            try {
                byte[] bytes;
                if (Build.VERSION.SDK_INT >= 26) bytes = Base64.getDecoder().decode(base64);
                else bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                String safeName = fileName.replaceAll("[^a-zA-Z0-9._-\\u0600-\\u06FF]", "_");
                if (safeName.isEmpty()) safeName = "document.pdf";
                final String outName = safeName;
                Uri uri;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, outName);
                    values.put(MediaStore.Downloads.MIME_TYPE, mimeType == null ? "application/pdf" : mimeType);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    values.put(MediaStore.Downloads.IS_PENDING, 1);
                    uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("MediaStore insert failed");
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(bytes); }
                    values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists() && !dir.mkdirs()) throw new Exception("Cannot create Downloads");
                    File f = new File(dir, outName);
                    try (FileOutputStream os = new FileOutputStream(f)) { os.write(bytes); }
                    uri = Uri.fromFile(f);
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "تم حفظ الملف في مجلد التنزيلات", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "تعذر حفظ الملف", Toast.LENGTH_LONG).show());
            }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER || fileCallback == null) return;
        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                results = new Uri[n];
                for (int i=0;i<n;i++) results[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) results = new Uri[]{data.getData()};
        }
        fileCallback.onReceiveValue(results);
        fileCallback = null;
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
