/*
 * LockActivity.java
 *
 * Copyright (c) 2016 Auth0 (http://auth0.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.auth0.android.lock;


import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.auth0.Auth0Exception;
import com.auth0.android.lock.events.SocialConnectionEvent;
import com.auth0.android.lock.provider.AuthorizeResult;
import com.auth0.android.lock.provider.CallbackHelper;
import com.auth0.android.lock.provider.IdentityProviderCallback;
import com.auth0.android.lock.provider.WebIdentityProvider;
import com.auth0.android.lock.utils.Application;
import com.auth0.android.lock.utils.Configuration;
import com.auth0.android.lock.views.HeaderView;
import com.auth0.android.lock.views.LockProgress;
import com.auth0.android.lock.views.SocialView;
import com.auth0.authentication.result.Token;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Response;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;

public class LockActivity extends AppCompatActivity {

    private static final String TAG = LockActivity.class.getSimpleName();
    private static final String JSONP_PREFIX = "Auth0.setClient(";

    private Application application;
    private Options options;
    private Handler handler;
    private Bus lockBus;
    private RelativeLayout rootView;
    private LockProgress progress;

    private WebIdentityProvider lastIdp;

    private IdentityProviderCallback idpCallback = new IdentityProviderCallback() {
        @Override
        public void onFailure(@NonNull Dialog dialog) {
            Log.w(TAG, "OnFailure called");
        }

        @Override
        public void onFailure(int titleResource, int messageResource, Throwable cause) {
            Log.w(TAG, "OnFailure called");
        }

        @Override
        public void onSuccess(@NonNull Token token) {
            Intent intent = new Intent(Lock.AUTHENTICATION_ACTION);
            intent.putExtra(Lock.ID_TOKEN_EXTRA, token.getIdToken());
            intent.putExtra(Lock.ACCESS_TOKEN_EXTRA, token.getAccessToken());
            intent.putExtra(Lock.REFRESH_TOKEN_EXTRA, token.getRefreshToken());
            intent.putExtra(Lock.TOKEN_TYPE_EXTRA, token.getType());

            LocalBroadcastManager.getInstance(LockActivity.this).sendBroadcast(intent);

            Log.d(TAG, "OnSuccess called with token: " + token.getIdToken());
            LockActivity.this.finish();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isLaunchConfigValid()) {
            finish();
            return;
        }

        lockBus = new Bus();
        lockBus.register(this);
        handler = new Handler(getMainLooper());

        setContentView(R.layout.com_auth0_lock_activity_lock);
        progress = (LockProgress) findViewById(R.id.com_auth0_lock_progress);
        rootView = (RelativeLayout) findViewById(R.id.com_auth0_lock_content);

        if (application == null) {
            fetchApplicationInfo();
        }
    }

    private boolean isLaunchConfigValid() {
        options = getIntent().getParcelableExtra(Lock.OPTIONS_EXTRA);
        if (options == null) {
            Log.e(TAG, "You need to specify the com.auth0.android.lock.Options in the Lock.OPTIONS_EXTRA of the Intent for LockActivity to launch. " +
                    "Use com.auth0.android.lock.Lock.Builder to generate one.");
            throw new IllegalArgumentException("Missing com.auth0.android.lock.Options in intent");
        }

        boolean launchedForResult = getCallingActivity() != null;
        if (options.useBrowser() && launchedForResult) {
            Log.e(TAG, "You're not able to useBrowser and startActivityForResult at the same time.");
            return false;
        }
        boolean launchedAsSingleTask = (getIntent().getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            //TODO: Document this case for users on <= KITKAT, as they will not receive this warning.
            if (options.useBrowser() && !launchedAsSingleTask) {
                Log.e(TAG, "Please, check that you have specified launchMode 'singleTask' in the AndroidManifest.");
                return false;
            }
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(Lock.CANCELED_ACTION);
        LocalBroadcastManager.getInstance(LockActivity.this).sendBroadcast(intent);
        super.onBackPressed();
    }

    /**
     * Fetch application information from Auth0
     */
    private void fetchApplicationInfo() {
        OkHttpClient client = new OkHttpClient();
        Uri uri = Uri.parse(options.getAccount().getConfigurationUrl()).buildUpon().appendPath("client")
                .appendPath(options.getAccount().getClientId() + ".js").build();

        com.squareup.okhttp.Request req = new com.squareup.okhttp.Request.Builder()
                .url(uri.toString())
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(com.squareup.okhttp.Request request, final IOException e) {
                Log.e(TAG, "Failed to fetchApplication: " + e.getMessage());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        progress.showResult(e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(Response response) throws IOException {
                application = parseJSONP(response);
                Log.i(TAG, "Application received!: " + application.getId());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        //this will trigger a hide
                        progress.showResult("");
                        initLockUI();
                    }
                });
            }
        });

    }

    /**
     * Show the LockUI with all the panels and custom widgets.
     */
    private void initLockUI() {
        Configuration config = new Configuration(application, null, null);

        //TODO: add custom view for panels layout.
        SocialView sv = new SocialView(this, lockBus, config, SocialView.Mode.List);
        rootView.addView(sv, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }


    /**
     * Parses the Application JSONP received from the API.
     */
    private Application parseJSONP(Response response) {
        try {
            String json = response.body().string();
            final int length = JSONP_PREFIX.length();
            if (json.length() < length) {
                throw new JSONException("Invalid App Info JSONP");
            }
            json = json.substring(length);
            JSONTokener tokenizer = new JSONTokener(json);
            if (!tokenizer.more()) {
                throw tokenizer.syntaxError("Invalid App Info JSONP");
            }
            Object nextValue = tokenizer.nextValue();
            if (!(nextValue instanceof JSONObject)) {
                tokenizer.back();
                throw tokenizer.syntaxError("Invalid JSON value of App Info");
            }
            JSONObject jsonObject = (JSONObject) nextValue;
            return new ObjectMapper().reader(Application.class).readValue(jsonObject.toString());
        } catch (IOException | JSONException e) {
            throw new Auth0Exception("Failed to parse response to request", e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "OnActivityResult called with intent: " + data);
        if (lastIdp != null) {
            //Deliver result to the IDP
            AuthorizeResult result = new AuthorizeResult(requestCode, resultCode, data);
            lastIdp.authorize(LockActivity.this, result);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }


    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(TAG, "OnNewIntent called with intent: " + intent);
        if (lastIdp != null) {
            //Deliver result to the IDP
            AuthorizeResult result = new AuthorizeResult(intent);
            lastIdp.authorize(LockActivity.this, result);
        }

        super.onNewIntent(intent);
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onSocialAuthenticationRequest(SocialConnectionEvent event) {
        //called on social button click
        if (options == null) {
            return;
        }

        String pkgName = getApplicationContext().getPackageName();
        CallbackHelper helper = new CallbackHelper(pkgName);
        lastIdp = new WebIdentityProvider(helper, options.getAccount(), idpCallback);
        lastIdp.setUseBrowser(options.useBrowser());
        lastIdp.start(LockActivity.this, event.getConnectionName());
    }

}