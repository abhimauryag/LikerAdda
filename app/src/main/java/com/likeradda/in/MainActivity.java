package com.likeradda.in;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.URLUtil;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RemoteViews;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    String websiteURL = "https://likeradda.in"; // sets web url
    private WebView webview;
    SwipeRefreshLayout mySwipeRefreshLayout;
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check if this activity is started by a deep link
        Intent intent = getIntent();
        Uri data = intent.getData();
        if (data != null) {
            // Handle the deep link (you can extract information from the Uri)
            String path = data.getPath();
            // ... process the path or perform any necessary actions

            // You might want to clear the data to avoid handling it again when the activity is recreated
            intent.setData(null);
        }

        if (!CheckNetwork.isInternetAvailable(this)) //returns true if internet available
        {
            //if there is no internet do this
            setContentView(R.layout.activity_main);
            //Toast.makeText(this,"No Internet Connection, Chris",Toast.LENGTH_LONG).show();

            new AlertDialog.Builder(this) //alert the person knowing they are about to close
                    .setTitle("No internet connection available")
                    .setMessage("Please Check you're Mobile data or Wifi network.")
                    .setPositiveButton("Ok", (dialog, which) -> finish())
                    //.setNegativeButton("No", null)
                    .show();

        } else {
            //Webview stuff
            webview = findViewById(R.id.webView);
            webview.getSettings().setJavaScriptEnabled(true);
            webview.getSettings().setDomStorageEnabled(true);
            webview.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
            webview.loadUrl(websiteURL);
            webview.setWebViewClient(new WebViewClientDemo());


            // Set a custom WebView client
            webview.setWebViewClient(new CustomWebViewClient());

            // Set DownloadListener for handling download events
            webview.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

                // Set notification and visibility preferences
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setTitle("Downloading File");

                // Specify the destination for the downloaded file
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

                // Create a DownloadManager and enqueue the request
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (dm != null) {
                    long downloadId = dm.enqueue(request);

                    // Show a notification that the download has started with a progress bar
                    showDownloadNotification("Downloading " + fileName, downloadId);
                }
            });


        }

        //Swipe to refresh functionality
        mySwipeRefreshLayout = this.findViewById(R.id.swipeContainer);

        mySwipeRefreshLayout.setOnRefreshListener(
                () -> webview.reload()
        );

    }

    private class CustomWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            // Check if the URL is a WhatsApp link
            if (url.startsWith("https://api.whatsapp.com/") || url.startsWith("whatsapp://")) {
                openWhatsApp(url);
                return true; // Prevent the WebView from loading the link
            }

            // Continue loading other URLs in the WebView
            return super.shouldOverrideUrlLoading(view, request);
        }

        private <ActivityNotFoundException extends Throwable> void openWhatsApp(String url) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        }

    }


    private void showDownloadNotification(String message, long downloadId) {
        RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.custom_notification_layout);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "download_channel")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Download started")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCustomContentView(contentView);

        // Create a broadcast receiver to handle the download progress updates
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    // Download completed, update notification
                    contentView.setViewVisibility(R.id.progressBar, View.GONE);
                    builder.setContentText("Download completed");

                    // Unregister the receiver as it's no longer needed
                    unregisterReceiver(this);
                }
            }
        };

        // Register the broadcast receiver to listen for download completion
        registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        notificationManager.notify(1, builder.build());
    }




    private class WebViewClientDemo extends WebViewClient {
        @Override
        //Keep webview in app when clicking links
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            mySwipeRefreshLayout.setRefreshing(false);
        }
    }

    //set back button functionality
    @Override
    public void onBackPressed() { //if user presses the back button do this
        if (webview.isFocused() && webview.canGoBack()) { //check if in webview and the user can go back
            webview.goBack(); //go back in webview
        } else { //do this if the webview cannot go back any further

            new AlertDialog.Builder(this) //alert the person knowing they are about to close
                    .setTitle("EXIT")
                    .setMessage("Are you sure. You want to close this app ?")
                    .setPositiveButton("Yes", (dialog, which) -> finish())
                    .setNegativeButton("No", null)
                    .show();
        }
    }

}

class CheckNetwork {

    private static final String TAG = CheckNetwork.class.getSimpleName();

    public static boolean isInternetAvailable(Context context)
    {
        NetworkInfo info = ((ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();

        if (info == null)
        {
            Log.d(TAG,"no internet connection");
            return false;
        }
        else
        {
            if(info.isConnected())
            {
                Log.d(TAG," internet connection available...");
            }
            else
            {
                Log.d(TAG," internet connection");
            }
            return true;

        }
    }
}