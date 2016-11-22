package com.example.android.sunshine.app.sync;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

public class DataLayerListenerService extends WearableListenerService {
    GoogleApiClient mGoogleApiClient;
    private static final String requestDataPath = "/wear-request-path";
    private static final String LOG_TAG = "Luke";

    public DataLayerListenerService() {
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if(messageEvent.getPath().equals(requestDataPath)) {
            Log.v(LOG_TAG, "Received a message from the wearable");

            Context context = DataLayerListenerService.this.getApplicationContext();
            SunshineSyncAdapter.syncImmediately(context);
        }
    }
}
