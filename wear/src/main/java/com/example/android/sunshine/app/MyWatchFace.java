/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    final String LOG_TAG = "Luke";

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine
            implements GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            DataApi.DataListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        boolean mAmbient;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                mDayofWeekFormat = new SimpleDateFormat("EEE", Locale.getDefault());
                mDayofWeekFormat.setCalendar(mCalendar);
                mMediumDateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
                mMediumDateFormat.setCalendar(mCalendar);
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
                invalidate();
            }
        };


        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        //Paints for drawing on Canvas
        Paint mBackgroundPaint;

        final String requestDataPath = "/wear-request-path";

        DateFormat mMediumDateFormat;
        Calendar mCalendar;
        Date mDate;
        SimpleDateFormat mDayofWeekFormat;
        Bitmap mBitmap;
        float mIconXOffset;
        float mIconYOffset;
        Paint mIconPaint;

        String mTemp = "00/00";
        float mTempXOffset;
        float mTempYOffset;
        Paint mTempPaint;

        String time = "";
        Time mTime;
        float mTimeXOffset;
        float mTimeYOffset;
        Paint mTimePaint;

        String date = "";
        float mDateXOffset;
        float mDateYOffset;
        Paint mDatePaint;

        String mNodeId;
        String capabilityName;

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        //Capability Listener
        CapabilityApi.CapabilityListener requestCapabilityListener = new CapabilityApi.CapabilityListener() {
            @Override
            public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
                Log.v(LOG_TAG, "Capability Changed!");
                updateCapability(capabilityInfo);
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            Log.v(LOG_TAG," MyWatchFace.onCreate !!!!!! ");
            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = MyWatchFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mCalendar = Calendar.getInstance();

            mTime = new Time();
            mTimeYOffset = resources.getDimension(R.dimen.digital_time_y_offset);
            mTimePaint = new Paint();
            mTimePaint.setColor(resources.getColor(R.color.digital_text));
            mTimePaint.setTypeface(NORMAL_TYPEFACE);
            mTimePaint.setAntiAlias(true);

            mDateYOffset = resources.getDimension(R.dimen.date_y_offset);
            mDatePaint = new Paint();
            mDatePaint.setColor(resources.getColor(R.color.digital_text));
            mDatePaint.setTypeface(NORMAL_TYPEFACE);
            mDatePaint.setAntiAlias(true);

            mIconYOffset = resources.getDimension(R.dimen.icon_y_offset);
            mIconPaint = new Paint();

            float scaleToUse = 0.35f;
            mBitmap = BitmapFactory.decodeResource(resources, R.drawable.art_clear);
            float sizeY = (float) mBitmap.getHeight() * scaleToUse;
            float sizeX = (float) mBitmap.getWidth() * scaleToUse;
            mBitmap = Bitmap.createScaledBitmap(mBitmap, (int) sizeX, (int) sizeY, false);

            mTempYOffset = resources.getDimension(R.dimen.temperature_y_offset);
            mTempPaint = new Paint();
            mTempPaint.setColor(resources.getColor(R.color.digital_text));
            mTempPaint.setTypeface(NORMAL_TYPEFACE);
            mTempPaint.setAntiAlias(true);

            mDate = new Date();
            capabilityName = "mobile_request";
            mNodeId = null;
            mDayofWeekFormat = new SimpleDateFormat("EEE", Locale.getDefault());
            mDayofWeekFormat.setCalendar(mCalendar);
            mMediumDateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
            mMediumDateFormat.setCalendar(mCalendar);

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                mGoogleApiClient.connect();

                mCalendar.setTimeZone(TimeZone.getDefault());
                mDayofWeekFormat = new SimpleDateFormat("EEE", Locale.getDefault());
                mDayofWeekFormat.setCalendar(mCalendar);
                mMediumDateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
                mMediumDateFormat.setCalendar(mCalendar);

                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();

                if(mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();

            mTimeXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_time_x_offset_round : R.dimen.digital_time_x_offset);
            float timeTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_time_text_size_round : R.dimen.digital_time_text_size);
            mTimePaint.setTextSize(timeTextSize);

            mDateXOffset = resources.getDimension(isRound
                    ? R.dimen.date_x_offset_round : R.dimen.date_x_offset);
            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.date_text_size_round : R.dimen.date_text_size);
            mDatePaint.setTextSize(dateTextSize);

            mIconXOffset = resources.getDimension(isRound
                    ? R.dimen.icon_x_offset_round : R.dimen.icon_x_offset);

            mTempXOffset = resources.getDimension(isRound
                    ? R.dimen.temperature_x_offset_round : R.dimen.temperature_x_offset);
            float temperatureTextSize = resources.getDimension(isRound
                    ? R.dimen.temperature_text_size_round : R.dimen.temperature_text_size);
            mTempPaint.setTextSize(temperatureTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @SuppressLint("DefaultLocale")
        @Override
        public void onTimeTick() {
            super.onTimeTick();
            mTime.setToNow();
            time = String.format("%d:%02d", mTime.hour, mTime.minute);

            @SuppressLint("SimpleDateFormat")
            DateFormat formatter = new SimpleDateFormat("EEE, MMM d, ''yy");
            Date today = new Date();
            date = formatter.format(today);

            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }
            canvas.drawText(date, mDateXOffset, mDateYOffset, mDatePaint);
            canvas.drawText(time, mTimeXOffset, mTimeYOffset, mTimePaint);
            canvas.drawBitmap(mBitmap, mIconXOffset, mIconYOffset, mIconPaint);
            canvas.drawText(mTemp, mTempXOffset, mTempYOffset, mTempPaint);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(Bundle connectionHint) {
            Log.v(LOG_TAG + " GoogleApiClient", "onConnected");
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            setupNodes();
            Wearable.CapabilityApi.addCapabilityListener(mGoogleApiClient, requestCapabilityListener, capabilityName);
        }

        @Override
        public void onConnectionSuspended(int cause) {
            Log.v(LOG_TAG + " GoogleApiClient", "onConnectionSuspended: " + cause );
        }

        @Override
        public void onConnectionFailed(ConnectionResult result) {
            Log.v(LOG_TAG + " GoogleApiClient", "onConnectionFailed : " + result.toString());
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.v(LOG_TAG, "onDataChanged called.");
            for (DataEvent data : dataEvents) {
                String path = data.getDataItem().getUri().getPath();
                String weather_path = MyWatchFace.this.getString(R.string.data_path);
                if(weather_path.equals(path)) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(data.getDataItem());

                    mTemp = dataMapItem.getDataMap().getString(MyWatchFace.this.getString(R.string.high_key))+"/"+dataMapItem.getDataMap().getString(MyWatchFace.this.getString(R.string.low_key));
                    int asset_num = dataMapItem.getDataMap().getInt(MyWatchFace.this.getString(R.string.asset_key));
                    Log.v(LOG_TAG, "Data Changed called asset number "+asset_num);
                    Resources resources = MyWatchFace.this.getResources();
                    float scaleToUse = 0.85f;
                    mBitmap = BitmapFactory.decodeResource(resources, asset_num);
                    float sizeY = (float) mBitmap.getHeight() * scaleToUse;
                    float sizeX = (float) mBitmap.getWidth() * scaleToUse;
                    mBitmap = Bitmap.createScaledBitmap(mBitmap, (int) sizeX, (int) sizeY, false);

                }
            }
        }


        public void setupNodes() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Wearable.CapabilityApi.getCapability(
                            mGoogleApiClient, capabilityName,
                            CapabilityApi.FILTER_REACHABLE)
                            .setResultCallback(new ResultCallback<CapabilityApi.GetCapabilityResult>() {
                                @Override
                                public void onResult(@NonNull CapabilityApi.GetCapabilityResult getCapabilityResult) {
                                    if(getCapabilityResult.getStatus().isSuccess()) {
                                        Log.v(LOG_TAG, "Capability detected");
                                        updateCapability(getCapabilityResult.getCapability());
                                    } else {
                                        Log.v(LOG_TAG, "Capability undetected\"");
                                    }
                                }
                            });
                }
            }).start();
        }

        public void updateCapability(CapabilityInfo capabilityInfo) {
            Set<Node> capableNodes = capabilityInfo.getNodes();
            mNodeId = findBestNodeId(capableNodes);
            requestWeatherData();
        }

        public String findBestNodeId(Set<Node> nodes) {
            String bestNodeId = null;
            for(Node node : nodes) {
                if(node.isNearby()) {
                    return node.getId();
                }
                bestNodeId = node.getId();
            }

            return bestNodeId;
        }

        public void requestWeatherData() {
            if(mNodeId != null) {
                Wearable.MessageApi.sendMessage(mGoogleApiClient, mNodeId, requestDataPath, null)
                        .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                            @Override
                            public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                                if (sendMessageResult.getStatus().isSuccess()) {
                                    Log.v(LOG_TAG, "requestWeatherData success! " + sendMessageResult.getStatus().getStatusCode());
                                } else {
                                    Log.v(LOG_TAG, "requestWeatherData unsuccessful. "
                                            + sendMessageResult.getStatus().getStatusCode());
                                }
                            }
                        });
            }
        }
    }
}
