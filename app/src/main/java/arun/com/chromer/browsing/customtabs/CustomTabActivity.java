/*
 * Chromer
 * Copyright (C) 2017 Arunkumar
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package arun.com.chromer.browsing.customtabs;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.widget.Toast;

import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import javax.inject.Inject;

import arun.com.chromer.R;
import arun.com.chromer.data.website.DefaultWebsiteRepository;
import arun.com.chromer.data.website.model.Website;
import arun.com.chromer.di.activity.ActivityComponent;
import arun.com.chromer.settings.Preferences;
import arun.com.chromer.shared.base.activity.BaseActivity;
import arun.com.chromer.util.Utils;
import arun.com.chromer.util.glide.GlideApp;
import rx.Subscription;
import rx.subscriptions.CompositeSubscription;
import timber.log.Timber;

import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.widget.Toast.LENGTH_SHORT;
import static arun.com.chromer.shared.Constants.ACTION_MINIMIZE;
import static arun.com.chromer.shared.Constants.EXTRA_KEY_FROM_WEBHEAD;
import static arun.com.chromer.shared.Constants.EXTRA_KEY_ORIGINAL_URL;
import static arun.com.chromer.shared.Constants.EXTRA_KEY_WEBSITE;
import static arun.com.chromer.shared.Constants.NO_COLOR;

public class CustomTabActivity extends BaseActivity {
    private boolean isLoaded = false;
    private String baseUrl = "";
    private BroadcastReceiver minimizeReceiver;
    private final CompositeSubscription subscriptions = new CompositeSubscription();

    @Inject
    DefaultWebsiteRepository websiteRepository;

    @TargetApi(LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() == null || getIntent().getData() == null) {
            Toast.makeText(this, getString(R.string.unsupported_link), LENGTH_SHORT).show();
            finish();
            return;
        }

        /*if (Preferences.get(this).incognitoMode()) {
            // Do an intent copy and let web view handle it.
            final Intent webViewActivity = new Intent(this, WebViewActivity.class);
            webViewActivity.setData(getIntent().getData());
            webViewActivity.putExtras(getIntent().getExtras());
            webViewActivity.setFlags(webViewActivity.getFlags());
            startActivity(webViewActivity);
            finish();
            return;
        }*/

        baseUrl = getIntent().getDataString();
        final boolean isWebHead = getIntent().getBooleanExtra(EXTRA_KEY_FROM_WEBHEAD, false);
        final Website website = getIntent().getParcelableExtra(EXTRA_KEY_WEBSITE);
        final int fallbackWebColor = website != null && !TextUtils.isEmpty(website.themeColor) ? website.themeColor() : NO_COLOR;

        getActivityComponent().customTabs()
                .forUrl(baseUrl)
                .fallbackColor(fallbackWebColor)
                .launch();

        if (Preferences.get(this).aggressiveLoading() && !Preferences.get(this).articleMode()) {
            delayedGoToBack();
        }
        registerMinimizeReceiver();
        beginExtraction(website);
    }

    @Override
    public void inject(@NonNull ActivityComponent activityComponent) {
        activityComponent.inject(this);
    }

    @Override
    protected int getLayoutRes() {
        return 0;
    }

    private void beginExtraction(@Nullable Website website) {
        if (website != null && website.title != null && website.faviconUrl != null) {
            Timber.d("Website info exists, setting description");
            applyDescriptionFromWebsite(website);
        } else {
            Timber.d("No info found, beginning parsing");
            final Subscription s = websiteRepository
                    .getWebsite(baseUrl)
                    .doOnNext(this::applyDescriptionFromWebsite)
                    .doOnError(Timber::e)
                    .subscribe();
            subscriptions.add(s);
        }
    }

    private void registerMinimizeReceiver() {
        minimizeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equalsIgnoreCase(ACTION_MINIMIZE) && intent.hasExtra(EXTRA_KEY_ORIGINAL_URL)) {
                    final String url = intent.getStringExtra(EXTRA_KEY_ORIGINAL_URL);
                    if (baseUrl.equalsIgnoreCase(url)) {
                        try {
                            Timber.d("Minimized %s", url);
                            moveTaskToBack(true);
                        } catch (Exception e) {
                            Timber.e(e);
                        }
                    }
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(minimizeReceiver, new IntentFilter(ACTION_MINIMIZE));
    }

    private void delayedGoToBack() {
        new Handler().postDelayed(() -> moveTaskToBack(true), 650);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        isLoaded = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isLoaded) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(minimizeReceiver);
        subscriptions.clear();
        super.onDestroy();
    }

    @TargetApi(LOLLIPOP)
    private void applyDescriptionFromWebsite(@Nullable final Website website) {
        if (Utils.isLollipopAbove() && website != null) {
            final String title = website.safeLabel();
            final String faviconUrl = website.faviconUrl;
            setTaskDescription(new ActivityManager.TaskDescription(title, null, website.themeColor()));
            GlideApp.with(this)
                    .asBitmap()
                    .load(faviconUrl)
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap icon, Transition<? super Bitmap> transition) {
                            setTaskDescription(new ActivityManager.TaskDescription(title, icon, website.themeColor()));
                        }
                    });
        }
    }
}
