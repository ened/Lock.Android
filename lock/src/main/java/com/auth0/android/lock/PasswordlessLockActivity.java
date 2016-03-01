/*
 * LockPasswordlessActivity.java
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
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.auth0.Auth0Exception;
import com.auth0.android.lock.enums.PasswordlessMode;
import com.auth0.android.lock.events.PasswordlessLoginEvent;
import com.auth0.android.lock.events.SocialConnectionEvent;
import com.auth0.android.lock.provider.AuthorizeResult;
import com.auth0.android.lock.provider.CallbackHelper;
import com.auth0.android.lock.provider.IdentityProviderCallback;
import com.auth0.android.lock.provider.WebIdentityProvider;
import com.auth0.android.lock.utils.Application;
import com.auth0.android.lock.views.LockProgress;
import com.auth0.android.lock.views.PasswordlessPanelHolder;
import com.auth0.authentication.AuthenticationAPIClient;
import com.auth0.authentication.AuthenticationRequest;
import com.auth0.authentication.result.Authentication;
import com.auth0.authentication.result.Token;
import com.auth0.authentication.result.UserProfile;
import com.auth0.callback.BaseCallback;
import com.auth0.request.ParameterizableRequest;
import com.auth0.request.Request;
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

public class PasswordlessLockActivity extends AppCompatActivity {

    private static final String TAG = PasswordlessLockActivity.class.getSimpleName();
    private static final String JSONP_PREFIX = "Auth0.setClient(";

    private Application application;
    private Configuration configuration;
    private Options options;
    private Handler handler;
    private Bus lockBus;
    private LinearLayout rootView;
    private LockProgress progress;
    private String lastPasswordlessEmailOrNumber;
    private WebIdentityProvider lastIdp;
    private PasswordlessPanelHolder panelHolder;

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
        rootView = (LinearLayout) findViewById(R.id.com_auth0_lock_content);
        fetchApplicationInfo();
    }

    private boolean isLaunchConfigValid() {
        options = getIntent().getParcelableExtra(Lock.OPTIONS_EXTRA);
        if (options == null) {
            Log.e(TAG, "Lock Options are missing in the received Intent and PasswordlessLockActivity will not launch. " +
                    "Use the PasswordlessLock.Builder to generate a valid Intent.");
            finish();
            return false;
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
        if (panelHolder != null && panelHolder.onBackPressed()) {
            return;
        }
        if (options != null && options.isClosable()) {
            Intent intent = new Intent(Lock.CANCELED_ACTION);
            LocalBroadcastManager.getInstance(PasswordlessLockActivity.this).sendBroadcast(intent);
            return;
        }
        super.onBackPressed();
    }

    /**
     * Show the LockUI with all the panels and custom widgets.
     */
    private void initLockUI() {
        configuration = new Configuration(application, options);
        if (configuration.getDefaultPasswordlessStrategy() != null || !configuration.getSocialStrategies().isEmpty()) {
            panelHolder = new PasswordlessPanelHolder(this, lockBus, configuration);
            rootView.addView(panelHolder, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void deliverResult(Authentication result) {
        Intent intent = new Intent(Lock.AUTHENTICATION_ACTION);
        intent.putExtra(Lock.ID_TOKEN_EXTRA, result.getToken().getIdToken());
        intent.putExtra(Lock.ACCESS_TOKEN_EXTRA, result.getToken().getAccessToken());
        intent.putExtra(Lock.REFRESH_TOKEN_EXTRA, result.getToken().getRefreshToken());
        intent.putExtra(Lock.TOKEN_TYPE_EXTRA, result.getToken().getType());
        intent.putExtra(Lock.PROFILE_EXTRA, result.getProfile());

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "OnActivityResult called with intent: " + data);
        processIncomingIntent(data);

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(TAG, "OnNewIntent called with intent: " + intent);
        processIncomingIntent(intent);

        super.onNewIntent(intent);
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

    private void processIncomingIntent(Intent intent) {
        if (options != null && lastPasswordlessEmailOrNumber != null && configuration.getPasswordlessMode() == PasswordlessMode.EMAIL_LINK) {
            String code = intent.getData().getQueryParameter("code");
            if (code == null || code.isEmpty()) {
                Log.w(TAG, "Passwordless Code is missing or could not be parsed");
                Snackbar.make(rootView, R.string.com_auth0_lock_error_parsing_passwordless_code, Snackbar.LENGTH_LONG).show();
            } else {
                PasswordlessLoginEvent event = new PasswordlessLoginEvent(configuration.getPasswordlessMode(), lastPasswordlessEmailOrNumber, code);
                onPasswordlessAuthenticationRequest(event);
            }
        } else if (options != null && lastIdp != null) {
            AuthorizeResult result = new AuthorizeResult(intent);
            lastIdp.authorize(PasswordlessLockActivity.this, result);
        } else {
            progress.showResult(getString(R.string.com_auth0_lock_error_unexpected_passwordless_intent));
        }
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onPasswordlessAuthenticationRequest(PasswordlessLoginEvent event) {
        if (options == null || configuration.getDefaultPasswordlessStrategy() == null) {
            return;
        } else if (event.getEmailOrNumber().isEmpty()) {
            return;
        }

        progress.showProgress();
        AuthenticationAPIClient apiClient = new AuthenticationAPIClient(options.getAccount());
        String connectionName = configuration.getFirstConnectionOfStrategy(configuration.getDefaultPasswordlessStrategy());
        if (event.getCode() != null) {
            AuthenticationRequest loginRequest = event.getLoginRequest(apiClient);
            loginRequest.addParameters(options.getAuthenticationParameters());
            loginRequest.setConnection(connectionName);
            loginRequest.start(authCallback);
            return;
        }

        lastPasswordlessEmailOrNumber = event.getEmailOrNumber();
        ParameterizableRequest<Void> codeRequest = event.getCodeRequest(apiClient);
        codeRequest.getParameterBuilder().setConnection(connectionName);
        codeRequest.start(passwordlessCodeCallback);
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onSocialAuthenticationRequest(SocialConnectionEvent event) {
        //called on social button click
        if (options == null) {
            return;
        }

        progress.showProgress();
        String pkgName = getApplicationContext().getPackageName();
        CallbackHelper helper = new CallbackHelper(pkgName);
        lastIdp = new WebIdentityProvider(helper, options.getAccount(), idpCallback);
        lastIdp.setUseBrowser(options.useBrowser());
        lastIdp.setParameters(options.getAuthenticationParameters());
        lastIdp.start(PasswordlessLockActivity.this, event.getConnectionName());
    }

    //Callbacks

    private BaseCallback<Void> passwordlessCodeCallback = new BaseCallback<Void>() {
        @Override
        public void onSuccess(Void payload) {
            Log.d(TAG, "Passwordless authentication succeeded");
            handler.post(new Runnable() {
                @Override
                public void run() {
                    progress.showResult("Passwordless authentication succeeded");
                }
            });
        }

        @Override
        public void onFailure(final Auth0Exception error) {
            Log.d(TAG, "Passwordless authentication failed");
            handler.post(new Runnable() {
                @Override
                public void run() {
                    progress.showResult(error.getMessage());
                }
            });
        }
    };

    private BaseCallback<Authentication> authCallback = new BaseCallback<Authentication>() {
        @Override
        public void onSuccess(Authentication authentication) {
            Log.d(TAG, "Login success: " + authentication.getProfile());
            deliverResult(authentication);
        }

        @Override
        public void onFailure(final Auth0Exception error) {
            Log.e(TAG, "Login failed");
            handler.post(new Runnable() {
                @Override
                public void run() {
                    progress.showResult(error.getMessage());
                }
            });
        }
    };

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
        public void onSuccess(@NonNull final Token token) {
            Log.d(TAG, "Fetching user profile..");
            Request<UserProfile> request = options.getAuthenticationAPIClient().tokenInfo(token.getIdToken());
            request.start(new BaseCallback<UserProfile>() {
                @Override
                public void onSuccess(UserProfile profile) {
                    Log.d(TAG, "OnSuccess called for user " + profile.getName());
                    Authentication authentication = new Authentication(profile, token);
                    deliverResult(authentication);
                }

                @Override
                public void onFailure(final Auth0Exception error) {
                    Log.w(TAG, "OnFailure called");
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            progress.showResult(error.getMessage());
                        }
                    });
                }
            });
        }
    };
}
