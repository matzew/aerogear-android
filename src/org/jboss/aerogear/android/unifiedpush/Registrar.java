/**
 * JBoss, Home of Professional Open Source Copyright Red Hat, Inc., and
 * individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.aerogear.android.unifiedpush;

import java.io.IOException;
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.jboss.aerogear.android.Callback;
import org.jboss.aerogear.android.http.HttpException;
import org.jboss.aerogear.android.impl.http.HttpRestProvider;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.common.collect.ImmutableSet;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Registrar {

    private final URL registryURL;
    private static final String TAG = Registrar.class.getSimpleName();
    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final String PROPERTY_ON_SERVER_EXPIRATION_TIME = "onServerExpirationTimeMs";
    private static List<MessageHandler> mainThreadHandlers = new ArrayList<MessageHandler>();
    private static List<MessageHandler> backgroundThreadHandlers = new ArrayList<MessageHandler>();
    /**
     * Default lifespan (7 days) of a reservation until it is considered
     * expired.
     */
    public static final long REGISTRATION_EXPIRY_TIME_MS = 1000 * 3600 * 24 * 7;
    private GoogleCloudMessaging gcm;

    public Registrar(URL registryURL) {
        this.registryURL = registryURL;
    }

    public void register(final Context context, final PushConfig config, final Callback<Void> callback) {
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {

                try {

                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    String regid = getRegistrationId(context);

                    if (regid.length() == 0) {
                        regid = gcm.register(config.senderIds
                                .toArray(new String[]{}));
                        Registrar.this.setRegistrationId(context, regid);
                    }

                    config.setDeviceToken(regid);

                    HttpRestProvider provider = new HttpRestProvider(registryURL);
                    provider.setPasswordAuthentication(config.getVariantID(), config.getSecret());

                    Gson gson = new GsonBuilder().setExclusionStrategies(
                            new ExclusionStrategy() {
                        private final ImmutableSet<String> fields;

                        {
                            fields = ImmutableSet.<String>builder()
                                    .add("deviceToken")
                                    .add("deviceType").add("alias")
                                    .add("operatingSystem")
                                    .add("osVersion").build();
                        }

                        @Override
                        public boolean shouldSkipField(FieldAttributes f) {
                            return !(f.getDeclaringClass() == PushConfig.class && fields
                                    .contains(f.getName()));
                        }

                        @Override
                        public boolean shouldSkipClass(Class<?> arg0) {
                            return false;
                        }
                    }).create();
                    try {
                        provider.post(gson.toJson(config));
                        return null;
                    } catch (HttpException ex) {
                        return ex;
                    }

                } catch (IOException ex) {
                    return ex;
                }

            }

            @SuppressWarnings("unchecked")
            @Override
            protected void onPostExecute(Exception result) {
                if (result == null) {
                    callback.onSuccess(null);
                } else {
                    callback.onFailure(result);
                }
            }
        ;

        }.execute((Void) null);

    }

    /**
     * Gets the current registration id for application on GCM service.
     * <p>
     * If result is empty, the registration has failed.
     *
     * @return registration id, or empty string if the registration is not
     * complete.
     */
    protected String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.length() == 0) {
            Log.v(TAG, "Registration not found.");
            return "";
        }
        // check if app was updated; if so, it must clear registration id to
        // avoid a race condition if GCM sends a message
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion
                || isRegistrationExpired(context)) {
            Log.v(TAG, "App version changed or registration expired.");
            return "";
        }
        return registrationId;
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGCMPreferences(Context context) {
        return context.getSharedPreferences(Registrar.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * Checks if the registration has expired.
     *
     * <p>
     * To avoid the scenario where the device sends the registration to the
     * server but the server loses it, the app developer may choose to
     * re-register after REGISTRATION_EXPIRY_TIME_MS.
     *
     * @return true if the registration has expired.
     */
    private boolean isRegistrationExpired(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        // checks if the information is not stale
        long expirationTime = prefs.getLong(PROPERTY_ON_SERVER_EXPIRATION_TIME, -1);
        return System.currentTimeMillis() > expirationTime;
    }

    /**
     * Stores the registration id, app versionCode, and expiration time in the
     * application's {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId registration id
     */
    private void setRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGCMPreferences(context);
        int appVersion = getAppVersion(context);
        Log.v(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        long expirationTime = System.currentTimeMillis()
                + REGISTRATION_EXPIRY_TIME_MS;

        Log.v(TAG, "Setting registration expiry time to "
                + new Timestamp(expirationTime));
        editor.putLong(PROPERTY_ON_SERVER_EXPIRATION_TIME, expirationTime);
        editor.commit();
    }

    public static void registerMainThreadHandler(MessageHandler handler) {
        mainThreadHandlers.add(handler);
    }

    public static void registerBackgroundThreadHandler(MessageHandler handler) {
        backgroundThreadHandlers.add(handler);
    }

    public static void unregisterMainThreadHandler(MessageHandler handler) {
        mainThreadHandlers.remove(handler);
    }

    public static void unregisterBackgroundThreadHandler(MessageHandler handler) {
        backgroundThreadHandlers.remove(handler);
    }

    static void notifyHandlers(final Context context, final Intent message, final MessageHandler defaultHandler) {

        if (backgroundThreadHandlers.isEmpty() && mainThreadHandlers.isEmpty()) {
            new Thread(new Runnable() {
                public void run() {

                    GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
                    String messageType = gcm.getMessageType(message);
                    if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
                        defaultHandler.onError();
                    } else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
                        defaultHandler.onDeleteMessage(context, message.getExtras());
                    } else {
                        defaultHandler.onMessage(context, message.getExtras());
                    }

                }
            }).start();
        }

        for (final MessageHandler handler : backgroundThreadHandlers) {
            new Thread(new Runnable() {
                public void run() {

                    GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
                    String messageType = gcm.getMessageType(message);
                    if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
                        handler.onError();
                    } else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
                        handler.onDeleteMessage(context, message.getExtras());
                    } else {
                        handler.onMessage(context, message.getExtras());
                    }

                }
            }).start();
        }

        Looper main = Looper.getMainLooper();

        for (final MessageHandler handler : mainThreadHandlers) {
            new Handler(main).post(new Runnable() {
                @Override
                public void run() {
                    GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
                    String messageType = gcm.getMessageType(message);
                    if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
                        handler.onError();
                    } else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
                        handler.onDeleteMessage(context, message.getExtras());
                    } else {
                        handler.onMessage(context, message.getExtras());
                    }
                }
            });
        }
    }

    protected static void notifyHandlers(final Context context,
            final Intent message) {
        notifyHandlers(context, message, null);
    }
}