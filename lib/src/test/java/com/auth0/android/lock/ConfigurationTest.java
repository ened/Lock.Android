/*
 * ConfigurationTest.java
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

import com.auth0.android.lock.CustomField.FieldType;
import com.auth0.android.lock.enums.InitialScreen;
import com.auth0.android.lock.enums.PasswordStrength;
import com.auth0.android.lock.enums.PasswordlessMode;
import com.auth0.android.lock.enums.SocialButtonStyle;
import com.auth0.android.lock.enums.Strategies;
import com.auth0.android.lock.enums.UsernameStyle;
import com.auth0.android.lock.utils.json.Connection;
import com.auth0.android.lock.utils.json.GsonBaseTest;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.auth0.android.lock.utils.json.ConnectionMatcher.hasConnection;
import static com.auth0.android.lock.utils.json.ConnectionMatcher.hasName;
import static com.auth0.android.lock.utils.json.ConnectionMatcher.hasStrategy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.auth0.android.lock.BuildConfig.class, sdk = 21, manifest = Config.NONE)
public class ConfigurationTest extends GsonBaseTest {

    private static final String RESTRICTIVE_DATABASE = "RestrictiveDatabase";
    private static final String CUSTOM_DATABASE = "CustomDatabase";
    private static final String USERNAME_PASSWORD_AUTHENTICATION = "Username-Password-Authentication";
    private static final String TWITTER = "twitter";
    private static final String EMAIL = "email";
    private static final String MY_AD = "MyAD";
    private static final String UNKNOWN_CONNECTION = "UnknownConnection";
    private static final String CUSTOM_PASSWORDLESS_CONNECTION = "my-sms-connection";

    private Configuration configuration;
    private List<Connection> connections;
    private Options options;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        final FileReader fr = new FileReader("src/test/resources/appinfo.json");
        Type applicationType = new TypeToken<List<Connection>>() {
        }.getType();
        connections = createGson().fromJson(new JsonReader(fr), applicationType);
        options = new Options();
    }

    @Test
    public void shouldKeepApplicationDefaultsIfOptionsAreNotModified() throws Exception {
        configuration = new Configuration(connections, options);
        assertThat(configuration.isUsernameRequired(), is(false));
        assertThat(configuration.allowLogIn(), is(true));
        assertThat(configuration.allowSignUp(), is(true));
        assertThat(configuration.allowForgotPassword(), is(true));
        assertThat(configuration.loginAfterSignUp(), is(true));
        assertThat(configuration.getUsernameStyle(), is(equalTo(UsernameStyle.DEFAULT)));
        assertThat(configuration.getInitialScreen(), is(equalTo(InitialScreen.LOG_IN)));
        assertThat(configuration.getSocialButtonStyle(), is(equalTo(SocialButtonStyle.UNSPECIFIED)));
        assertThat(configuration.hasExtraFields(), is(false));
        assertThat(configuration.getPasswordPolicy(), is(PasswordStrength.NONE));
        assertThat(configuration.mustAcceptTerms(), is(false));
    }

    @Test
    public void shouldGetValidStyleForNotOverriddenStrategy() throws Exception {
        configuration = new Configuration(connections, options);
        assertThat(configuration.authStyleForConnection("facebook", "facebook-prod"), is(R.style.Lock_Theme_AuthStyle_Facebook));
    }

    @Test
    public void shouldGetStyleForOverriddenStrategy() throws Exception {
        //noinspection ResourceType
        options.withAuthStyle("facebook-prod", 123456);
        configuration = new Configuration(connections, options);
        assertThat(configuration.authStyleForConnection("facebook", "facebook-prod"), is(123456));
    }

    @Test
    public void shouldMergeApplicationWithOptionsIfDefaultDatabaseExists() throws Exception {
        options.setConnections(Collections.singletonList(USERNAME_PASSWORD_AUTHENTICATION));
        options.setAllowLogIn(false);
        options.setAllowSignUp(false);
        options.setAllowForgotPassword(false);
        options.setLoginAfterSignUp(false);
        options.setUsernameStyle(UsernameStyle.USERNAME);
        options.setSocialButtonStyle(SocialButtonStyle.BIG);
        configuration = new Configuration(connections, options);
        assertThat(configuration.isUsernameRequired(), is(false));
        assertThat(configuration.allowLogIn(), is(false));
        assertThat(configuration.allowSignUp(), is(false));
        assertThat(configuration.allowForgotPassword(), is(false));
        assertThat(configuration.loginAfterSignUp(), is(false));
        assertThat(configuration.getUsernameStyle(), is(equalTo(UsernameStyle.USERNAME)));
        assertThat(configuration.getSocialButtonStyle(), is(equalTo(SocialButtonStyle.BIG)));
        assertThat(configuration.hasExtraFields(), is(false));
    }

    @Test
    public void shouldNotMergeApplicationWithOptionsIfApplicationIsRestrictive() throws Exception {
        options.setConnections(Collections.singletonList(RESTRICTIVE_DATABASE));
        options.setAllowSignUp(true);
        options.setAllowForgotPassword(true);
        configuration = new Configuration(connections, options);
        assertThat(configuration.allowSignUp(), is(false));
        assertThat(configuration.allowForgotPassword(), is(false));
    }

    @Test
    public void shouldNotUseClassicLockIfNoConnectionsAreAvailable() throws Exception {
        configuration = filteredConfigBy("");
        assertThat(configuration.hasClassicConnections(), is(false));
    }

    @Test
    public void shouldUseClassicLockWithEnterpriseConnections() throws Exception {
        configuration = filteredConfigBy(MY_AD);
        assertThat(configuration.hasClassicConnections(), is(true));
    }

    @Test
    public void shouldUseClassicLockWithSocialConnections() throws Exception {
        configuration = filteredConfigBy(TWITTER);
        assertThat(configuration.hasClassicConnections(), is(true));
    }

    @Test
    public void shouldUseClassicLockWithDatabaseConnections() throws Exception {
        configuration = filteredConfigBy(RESTRICTIVE_DATABASE);
        assertThat(configuration.hasClassicConnections(), is(true));
    }

    @Test
    public void shouldNotUsePasswordlessIfNoConnectionsAreAvailable() throws Exception {
        configuration = filteredConfigBy("");
        assertThat(configuration.hasPasswordlessConnections(), is(false));
    }

    @Test
    public void shouldIgnoreAllowedScreenSettingsIfDatabaseConnectionsAreAvailable() throws Exception {
        options.setAllowLogIn(false);
        options.setAllowSignUp(false);
        options.setAllowForgotPassword(false);
        configuration = filteredConfigBy(USERNAME_PASSWORD_AUTHENTICATION);
        assertThat(configuration.hasClassicConnections(), is(true));
    }

    @Test
    public void shouldUsePasswordlessLockWithSocialConnections() throws Exception {
        configuration = filteredConfigBy(TWITTER);
        assertThat(configuration.hasPasswordlessConnections(), is(true));
    }

    @Test
    public void shouldUsePasswordlessLockWithPasswordlessConnections() throws Exception {
        configuration = filteredConfigBy(EMAIL);
        assertThat(configuration.hasPasswordlessConnections(), is(true));
    }

    @Test
    public void shouldPasswordlessLockNotBeAffectedByClassicLockScreenFlags() throws Exception {
        options.setAllowLogIn(false);
        options.setAllowSignUp(false);
        options.setAllowForgotPassword(false);
        configuration = filteredConfigBy(EMAIL, TWITTER);
        assertThat(configuration.hasPasswordlessConnections(), is(true));
    }

    @Test
    public void shouldSetExtraSignUpFields() throws Exception {
        options.setCustomFields(createCustomFields());
        configuration = new Configuration(connections, options);

        assertThat(configuration.hasExtraFields(), is(true));
        assertThat(configuration.getExtraSignUpFields(), contains(options.getCustomFields().toArray()));
    }

    @Test
    public void shouldSetInitialScreenWhenDatabaseConnectionAvailable() throws Exception {
        options.setConnections(Collections.singletonList(USERNAME_PASSWORD_AUTHENTICATION));

        options.setInitialScreen(InitialScreen.SIGN_UP);
        configuration = new Configuration(connections, options);
        assertThat(configuration.getInitialScreen(), is(InitialScreen.SIGN_UP));

        options.setInitialScreen(InitialScreen.LOG_IN);
        configuration = new Configuration(connections, options);
        assertThat(configuration.getInitialScreen(), is(InitialScreen.LOG_IN));

        options.setInitialScreen(InitialScreen.FORGOT_PASSWORD);
        configuration = new Configuration(connections, options);
        assertThat(configuration.getInitialScreen(), is(InitialScreen.FORGOT_PASSWORD));
    }

    @Test
    public void shouldNotChangeInitialScreenWhenNoDatabaseConnectionAvailable() throws Exception {
        options.setConnections(Collections.singletonList(""));

        //InitialScreen.LOG_IN is the default InitialScreen.
        options.setInitialScreen(InitialScreen.SIGN_UP);
        configuration = new Configuration(connections, options);
        assertThat(configuration.getInitialScreen(), is(InitialScreen.LOG_IN));

        options.setInitialScreen(InitialScreen.FORGOT_PASSWORD);
        configuration = new Configuration(connections, options);
        assertThat(configuration.getInitialScreen(), is(InitialScreen.LOG_IN));
    }

    @Test
    public void shouldPreferPasswordlessEmailOverSMSWhenBothAvailable() throws Exception {
        options.setUseCodePasswordless(true);
        options.setConnections(Arrays.asList(Strategies.SMS, Strategies.Email));
        configuration = new Configuration(connections, options);

        assertThat(configuration.getPasswordlessMode(), is(PasswordlessMode.EMAIL_CODE));

        options.setUseCodePasswordless(false);
        options.setConnections(Arrays.asList(Strategies.SMS, Strategies.Email));
        configuration = new Configuration(connections, options);

        assertThat(configuration.getPasswordlessMode(), is(PasswordlessMode.EMAIL_LINK));
    }

    @Test
    public void shouldSetCorrectPasswordlessTypeWhenUsingEmail() throws Exception {
        options.setUseCodePasswordless(true);
        options.setConnections(Arrays.asList(Strategies.Email));
        configuration = new Configuration(connections, options);

        assertThat(configuration.getPasswordlessMode(), is(PasswordlessMode.EMAIL_CODE));

        options.setUseCodePasswordless(false);
        options.setConnections(Arrays.asList(Strategies.Email));
        configuration = new Configuration(connections, options);

        assertThat(configuration.getPasswordlessMode(), is(PasswordlessMode.EMAIL_LINK));
    }

    @Test
    public void shouldSetCorrectPasswordlessTypeWhenUsingSMS() throws Exception {
        options.setUseCodePasswordless(true);
        options.setConnections(Arrays.asList(Strategies.SMS));
        configuration = new Configuration(connections, options);

        assertThat(configuration.getPasswordlessMode(), is(PasswordlessMode.SMS_CODE));

        options.setUseCodePasswordless(false);
        options.setConnections(Arrays.asList(Strategies.SMS));
        configuration = new Configuration(connections, options);

        assertThat(configuration.getPasswordlessMode(), is(PasswordlessMode.SMS_LINK));
    }

    @Test
    public void shouldNotHavePasswordlessModeWithoutConnections() throws Exception {
        options.setUseCodePasswordless(true);
        options.setConnections(Collections.singletonList(Strategies.Facebook));
        configuration = new Configuration(connections, options);

        assertThat(configuration.getPasswordlessMode(), is(PasswordlessMode.DISABLED));
    }

    @Test
    public void shouldDefaultToCodePasswordlessWhenTypeMissingFromOptions() throws Exception {
        options.setConnections(Collections.singletonList(Strategies.SMS));
        configuration = new Configuration(connections, options);

        assertThat(configuration.getPasswordlessMode(), is(PasswordlessMode.SMS_CODE));
    }

    @Test
    public void shouldNotFilterDefaultDBConnection() throws Exception {
        configuration = unfilteredConfig();
        assertThat(configuration.getDatabaseConnection(), hasName(USERNAME_PASSWORD_AUTHENTICATION));
    }

    @Test
    public void shouldHandleNoDBConnections() throws Exception {
        options.useDatabaseConnection(null);
        configuration = new Configuration(new ArrayList<Connection>(), options);
        final Connection connection = configuration.getDatabaseConnection();
        assertThat(connection, nullValue());
    }

    @Test
    public void shouldFilterDBConnection() throws Exception {
        configuration = filteredConfigBy(CUSTOM_DATABASE);
        assertThat(configuration.getDatabaseConnection(), hasName(CUSTOM_DATABASE));
    }

    @Test
    public void shouldReturnNullDBConnectionWhenNoneMatch() throws Exception {
        configuration = filteredConfigBy(UNKNOWN_CONNECTION);
        assertThat(configuration.getDatabaseConnection(), nullValue());
    }

    @Test
    public void shouldReturnSpecifiedDBConnectionWhenMoreThanOneDBConnectionIsAvailable() throws Exception {
        options.setConnections(Arrays.asList(CUSTOM_DATABASE, USERNAME_PASSWORD_AUTHENTICATION, RESTRICTIVE_DATABASE, UNKNOWN_CONNECTION));
        options.useDatabaseConnection(RESTRICTIVE_DATABASE);
        configuration = new Configuration(connections, options);
        assertThat(configuration.getDatabaseConnection(), hasName(RESTRICTIVE_DATABASE));
    }

    @Test
    public void shouldReturnSpecifiedDBConnectionIfAvailable() throws Exception {
        options.setConnections(null);

        options.useDatabaseConnection(CUSTOM_DATABASE);
        configuration = new Configuration(connections, options);
        assertThat(configuration.getDatabaseConnection(), hasName(CUSTOM_DATABASE));
    }

    @Test
    public void shouldIgnoreSpecifiedDBConnectionIfNotAvailable() throws Exception {
        options.setConnections(null);

        options.useDatabaseConnection("non-existing-db-connection");
        configuration = new Configuration(connections, options);
        assertThat(configuration.getDatabaseConnection(), hasName(USERNAME_PASSWORD_AUTHENTICATION));
    }

    @Test
    public void shouldIgnoreSpecifiedDBConnectionIfFiltered() throws Exception {
        options.setConnections(Collections.singletonList(USERNAME_PASSWORD_AUTHENTICATION));

        options.useDatabaseConnection("non-existing-db-connection");
        configuration = new Configuration(connections, options);
        assertThat(configuration.getDatabaseConnection(), hasName(USERNAME_PASSWORD_AUTHENTICATION));
    }

    @Test
    public void shouldReturnUnfilteredPasswordlessConnections() throws Exception {
        configuration = unfilteredConfig();
        List<Connection> connections = configuration.getPasswordlessConnections();
        assertThat(connections, is(notNullValue()));
        assertThat(connections, containsInAnyOrder(hasConnection(Strategies.Email, "email"),
                hasConnection(Strategies.SMS, "sms"), hasConnection(Strategies.SMS, CUSTOM_PASSWORDLESS_CONNECTION)));
    }

    @Test
    public void shouldReturnFilteredPasswordlessConnections() throws Exception {
        configuration = filteredConfigBy(CUSTOM_PASSWORDLESS_CONNECTION);
        Connection connection = configuration.getPasswordlessConnection();
        assertThat(connection, is(notNullValue()));
        assertThat(connection, hasConnection(Strategies.SMS, CUSTOM_PASSWORDLESS_CONNECTION));
    }

    @Test
    public void shouldPreferEmailPasswordlessConnection() throws Exception {
        configuration = unfilteredConfig();
        Connection defaultConnection = configuration.getPasswordlessConnection();
        assertThat(defaultConnection, is(notNullValue()));
        assertThat(defaultConnection.getName(), equalTo(Strategies.Email));
    }

    @Test
    public void shouldReturnEmptyPasswordlessConnectionIfNoneMatch() throws Exception {
        configuration = filteredConfigBy(Strategies.Facebook);
        Connection connection = configuration.getPasswordlessConnection();
        assertThat(connection, is(nullValue()));
    }

    @Test
    public void shouldIgnoreStrategyNameAndReturnFilteredConnections() throws Exception {
        configuration = filteredConfigBy(Strategies.Twitter, "twitter-dev");
        final List<Connection> strategies = configuration.getSocialConnections();
        assertThat(strategies, containsInAnyOrder(hasConnection(Strategies.Twitter, "twitter"), hasConnection(Strategies.Twitter, "twitter-dev") ));
        assertThat(strategies, hasSize(2));
    }

    @Test
    public void shouldNotReturnFilteredSocialStrategiesWithoutConnections() throws Exception {
        configuration = filteredConfigBy(Strategies.Facebook, Strategies.Linkedin);
        final List<Connection> connections = configuration.getSocialConnections();
        assertThat(connections, hasItem(hasStrategy(Strategies.Facebook)));
        assertThat(connections, not(hasItem(hasStrategy(Strategies.Linkedin))));
    }

    @Test
    public void shouldReturnUnfilteredSocialConnections() throws Exception {
        configuration = unfilteredConfig();
        final List<Connection> connections = configuration.getSocialConnections();
        assertThat(connections, containsInAnyOrder(hasConnection(Strategies.Facebook, "facebook"),
                hasConnection(Strategies.Twitter, "twitter"), hasConnection(Strategies.Twitter, "twitter-dev"), hasConnection(Strategies.Instagram, "instagram"),
                hasConnection(Strategies.GooglePlus, "google-oauth2")));
    }

    @Test
    public void shouldReturnFilteredSocialConnections() throws Exception {
        configuration = filteredConfigBy(Strategies.Facebook, Strategies.Instagram);
        assertThat(configuration.getSocialConnections(), containsInAnyOrder(hasConnection(Strategies.Facebook, "facebook"),
                hasConnection(Strategies.Instagram, "instagram")));
    }

    @Test
    public void shouldReturnEmptySocialConnectionsIfNoneMatch() throws Exception {
        configuration = filteredConfigBy(Strategies.Yammer, Strategies.Yahoo);
        assertThat(configuration.getSocialConnections(), emptyIterable());
    }

    @Test
    public void shouldReturnUnfilteredEnterpriseConnections() throws Exception {
        configuration = unfilteredConfig();
        assertThat(configuration.getEnterpriseConnections(), containsInAnyOrder(hasConnection(Strategies.ActiveDirectory, "MyAD"),
                hasConnection(Strategies.ActiveDirectory, "mySecondAD"), hasConnection(Strategies.GoogleApps, "auth0.com")));
    }

    @Test
    public void shouldReturnFilteredEnterpriseConnections() throws Exception {
        configuration = filteredConfigBy("auth0.com");
        assertThat(configuration.getEnterpriseConnections(), contains(hasConnection(Strategies.GoogleApps, "auth0.com")));
    }

    @Test
    public void shouldReturnEmptyEnterpriseConnectionsIfNoneMatch() throws Exception {
        configuration = filteredConfigBy(Strategies.Yandex);
        assertThat(configuration.getEnterpriseConnections(), emptyIterable());
    }

    @Test
    public void shouldHaveDefaultPrivacyPolicyURL() throws Exception {
        configuration = unfilteredConfig();
        assertThat(configuration.getPrivacyURL(), is(notNullValue()));
        assertThat(configuration.getPrivacyURL(), is(equalTo("https://auth0.com/privacy")));
    }

    @Test
    public void shouldHaveCustomPrivacyPolicyURL() throws Exception {
        options.setPrivacyURL("https://google.com/privacy");
        configuration = new Configuration(connections, options);
        assertThat(configuration.getPrivacyURL(), is(notNullValue()));
        assertThat(configuration.getPrivacyURL(), is(equalTo("https://google.com/privacy")));
    }

    @Test
    public void shouldHaveDefaultTermsOfServiceURL() throws Exception {
        configuration = unfilteredConfig();
        assertThat(configuration.getTermsURL(), is(notNullValue()));
        assertThat(configuration.getTermsURL(), is(equalTo("https://auth0.com/terms")));
    }

    @Test
    public void shouldHaveCustomTermsOfServiceURL() throws Exception {
        options.setTermsURL("https://google.com/terms");
        configuration = new Configuration(connections, options);
        assertThat(configuration.getTermsURL(), is(notNullValue()));
        assertThat(configuration.getTermsURL(), is(equalTo("https://google.com/terms")));
    }

    @Test
    public void shouldHaveMustAcceptTermsEnabled() throws Exception {
        options.setMustAcceptTerms(true);
        configuration = new Configuration(connections, options);
        assertThat(configuration.mustAcceptTerms(), is(true));
    }

    private Configuration unfilteredConfig() {
        return new Configuration(connections, options);
    }

    private Configuration filteredConfigBy(String... names) {
        options.setConnections(Arrays.asList(names));
        return new Configuration(connections, options);
    }

    private List<CustomField> createCustomFields() {
        CustomField fieldNumber = new CustomField(R.drawable.com_auth0_lock_ic_phone, FieldType.TYPE_PHONE_NUMBER, "number", R.string.com_auth0_lock_hint_phone_number);
        CustomField fieldSurname = new CustomField(R.drawable.com_auth0_lock_ic_username, FieldType.TYPE_NAME, "surname", R.string.com_auth0_lock_hint_username);

        List<CustomField> customFields = new ArrayList<>();
        customFields.add(fieldNumber);
        customFields.add(fieldSurname);
        return customFields;
    }
}