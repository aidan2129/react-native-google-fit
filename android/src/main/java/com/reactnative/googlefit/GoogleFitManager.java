/**
 * Copyright (c) 2017-present, Stanislav Doskalenko - doskalenko.s@gmail.com
 * All rights reserved.
 *
 * This source code is licensed under the MIT-style license found in the
 * LICENSE file in the root directory of this source tree.
 *
 * Based on Asim Malik android source code, copyright (c) 2015
 *
 **/
package com.reactnative.googlefit;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.IntentSender;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import java.util.ArrayList;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.ErrorDialogFragment;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.auth.api.signin.*;

import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Session;
import com.google.android.gms.fitness.request.SessionInsertRequest;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.FitnessActivities;
import com.google.android.gms.common.api.PendingResult;


import java.lang.System;
import java.util.concurrent.TimeUnit;




public class GoogleFitManager implements
        ActivityEventListener {

    private ReactContext mReactContext;
    private GoogleApiClient mApiClient;
    private static final int REQUEST_OAUTH = 1001;
    private static final String AUTH_PENDING = "auth_state_pending";
    private static boolean mAuthInProgress = false;
    private Activity mActivity;

    private DistanceHistory distanceHistory;
    private StepHistory stepHistory;
    private BodyHistory bodyHistory;
    private HeartrateHistory heartrateHistory;
    private CalorieHistory calorieHistory;
    private NutritionHistory nutritionHistory;
    private StepCounter mStepCounter;
    private StepSensor stepSensor;
    private RecordingApi recordingApi;
    private ActivityHistory activityHistory;

    private static final String TAG = "RNGoogleFit";

    public GoogleFitManager(ReactContext reactContext, Activity activity) {

        //Log.i(TAG, "Initializing GoogleFitManager" + mAuthInProgress);
        this.mReactContext = reactContext;
        this.mActivity = activity;

        mReactContext.addActivityEventListener(this);

        this.mStepCounter = new StepCounter(mReactContext, this, activity);
        this.stepHistory = new StepHistory(mReactContext, this);
        this.bodyHistory = new BodyHistory(mReactContext, this);
        this.heartrateHistory = new HeartrateHistory(mReactContext, this);
        this.distanceHistory = new DistanceHistory(mReactContext, this);
        this.calorieHistory = new CalorieHistory(mReactContext, this);
        this.nutritionHistory = new NutritionHistory(mReactContext, this);
        this.recordingApi = new RecordingApi(mReactContext, this);
        this.activityHistory = new ActivityHistory(mReactContext, this);
        //        this.stepSensor = new StepSensor(mReactContext, activity);
    }

    public GoogleApiClient getGoogleApiClient() {
        return mApiClient;
    }

    public RecordingApi getRecordingApi() {
        return recordingApi;
    }

    public StepCounter getStepCounter() {
        return mStepCounter;
    }

    public StepHistory getStepHistory() {
        return stepHistory;
    }

    public BodyHistory getBodyHistory() {
        return bodyHistory;
    }

    public HeartrateHistory getHeartrateHistory() {
        return heartrateHistory;
    }

    public DistanceHistory getDistanceHistory() {
        return distanceHistory;
    }

    public boolean saveMeditation(long startTime, long endTime) {
        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName("com.lvlup.buddhify")
                .setDataType(DataType.TYPE_ACTIVITY_SEGMENT)
                .setName("meditation")
                .setType(DataSource.TYPE_RAW)
                .build();
        
        DataSet dataSet = DataSet.create(dataSource);

        DataPoint dataPoint = dataSet.createDataPoint().setTimeInterval((long) startTime, (long) endTime, TimeUnit.MILLISECONDS);
        dataPoint.getValue(Field.FIELD_ACTIVITY).setActivity(FitnessActivities.MEDITATION);
        dataSet.add(dataPoint);
        
        Session session = new Session.Builder()
          .setName("buddhify meditation")
          .setIdentifier("buddhify " + System.currentTimeMillis())
          .setStartTime(startTime, TimeUnit.MILLISECONDS)
          .setEndTime(endTime, TimeUnit.MILLISECONDS)
          .setActivity(FitnessActivities.MEDITATION)
          .build();
        SessionInsertRequest insertRequest = new SessionInsertRequest.Builder()
            .setSession(session)
            .addDataSet(dataSet)
            .build();

        
        PendingResult<Status> pendingResult = Fitness.SessionsApi.insertSession(this.getGoogleApiClient(), insertRequest);
 
        pendingResult.setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                if( status.isSuccess() ) {
                    Log.i("Tuts+", "successfully inserted meditation session");
                } else {
                    Log.i("Tuts+", "Failed to insert meditation session: " + status.getStatusMessage());
                }
            }
        });

        return true;
    }
    

    public void resetAuthInProgress()
    {
        if (!isAuthorized()) {
            mAuthInProgress = false;
        }
    }

    public CalorieHistory getCalorieHistory() { return calorieHistory; }

    public NutritionHistory getNutritionHistory() { return nutritionHistory; }

    public void authorize(ArrayList<String> userScopes) {
        final ReactContext mReactContext = this.mReactContext;

        GoogleApiClient.Builder apiClientBuilder = new GoogleApiClient.Builder(mReactContext.getApplicationContext())
                .addApi(Fitness.SESSIONS_API);

        for (String scopeName : userScopes) {
            apiClientBuilder.addScope(new Scope(scopeName));
        }

        mApiClient = apiClientBuilder
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {
                            @Override
                            public void onConnected(@Nullable Bundle bundle) {
                                Log.i(TAG, "Authorization - Connected");
                                sendEvent(mReactContext, "GoogleFitAuthorizeSuccess", null);
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                Log.i(TAG, "Authorization - Connection Suspended");
                                if ((mApiClient != null) && (mApiClient.isConnected())) {
                                    mApiClient.disconnect();
                                }
                            }
                        }
                )
                .addOnConnectionFailedListener(
                        new GoogleApiClient.OnConnectionFailedListener() {
                            @Override
                            public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                                Log.i(TAG, "Authorization - Failed Authorization Mgr:" + connectionResult);
                                if (mAuthInProgress) {
                                    Log.i(TAG, "Authorization - Already attempting to resolve an error.");
                                } else if (connectionResult.hasResolution()) {
                                    try {
                                        mAuthInProgress = true;
                                        connectionResult.startResolutionForResult(mActivity, REQUEST_OAUTH);
                                    } catch (IntentSender.SendIntentException e) {
                                        Log.i(TAG, "Authorization - Failed again: " + e);
                                        mApiClient.connect();
                                    }
                                } else {
                                    Log.i(TAG, "Show dialog using GoogleApiAvailability.getErrorDialog()");
                                    showErrorDialog(connectionResult.getErrorCode());
                                    mAuthInProgress = true;
                                    WritableMap map = Arguments.createMap();
                                    map.putString("message", "" + connectionResult);
                                    sendEvent(mReactContext, "GoogleFitAuthorizeFailure", map);
                                }
                            }
                        }
                )
                .build();

        mApiClient.connect();
    }

    public void  disconnect() {
        GoogleSignInAccount gsa = GoogleSignIn.getAccountForScopes(mReactContext, new Scope("https://www.googleapis.com/auth/fitness.activity.read"));
        Fitness.getConfigClient(mReactContext, gsa).disableFit();
        mApiClient.disconnect();
    }

    public boolean isAuthorized() {
        if (mApiClient != null && mApiClient.isConnected()) {
            return true;
        } else {
            return false;
        }
    }

    protected void stop() {
        Fitness.SensorsApi.remove(mApiClient, mStepCounter)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            mApiClient.disconnect();
                        }
                    }
                });
    }


    private void sendEvent(ReactContext reactContext,
                           String eventName,
                           @Nullable WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }


    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OAUTH) {
            mAuthInProgress = false;
            if (resultCode == Activity.RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!mApiClient.isConnecting() && !mApiClient.isConnected()) {
                    mApiClient.connect();
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Log.e(TAG, "Authorization - Cancel");
                WritableMap map = Arguments.createMap();
                map.putString("message", "" + "Authorization cancelled");
                sendEvent(mReactContext, "GoogleFitAuthorizeFailure", map);
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
    }

    public ActivityHistory getActivityHistory() {
        return activityHistory;
    }

    public void setActivityHistory(ActivityHistory activityHistory) {
        this.activityHistory = activityHistory;
    }

    public static class GoogleFitCustomErrorDialig extends ErrorDialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(AUTH_PENDING);
            return GoogleApiAvailability.getInstance().getErrorDialog(
                    this.getActivity(), errorCode, REQUEST_OAUTH);
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            mAuthInProgress = false;
        }
    }

    /* Creates a dialog for an error message */
    private void showErrorDialog(int errorCode) {
        // Create a fragment for the error dialog
        GoogleFitCustomErrorDialig dialogFragment = new GoogleFitCustomErrorDialig();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(AUTH_PENDING, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(mActivity.getFragmentManager(), "errordialog");
    }
}
