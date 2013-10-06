/*
 * Copyright (C) 2012 The CyanogenMod Project
 * Copyright (C) 2013 BrandroidTools
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

package me.toolify.backbone.activities.preferences;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.MenuItem;

import java.util.List;

import me.toolify.backbone.R;
import me.toolify.backbone.dashclock.DashExtension;
import me.toolify.backbone.dashclock.DashSettings;
import me.toolify.backbone.preferences.FileManagerSettings;
import me.toolify.backbone.ui.ThemeManager;
import me.toolify.backbone.ui.ThemeManager.Theme;
import me.toolify.backbone.util.AndroidHelper;

/**
 * The {@link SettingsPreferences} preferences
 */
public class SettingsPreferences extends PreferenceActivity {

    private static final String TAG = "SettingsPreferences"; //$NON-NLS-1$

    private static final boolean DEBUG = false;

    private final BroadcastReceiver mNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                if (intent.getAction().compareTo(FileManagerSettings.INTENT_THEME_CHANGED) == 0) {
                    finish();
                }
            }
        }
    };

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DEBUG) {
            Log.d(TAG, "SettingsPreferences.onCreate"); //$NON-NLS-1$
        }

        // Register the broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(FileManagerSettings.INTENT_THEME_CHANGED);
        registerReceiver(this.mNotificationReceiver, filter);

        //Initialize action bars
        initTitleActionBar();

        // Apply the theme
        applyTheme();

        super.onCreate(savedInstanceState);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "SettingsPreferences.onDestroy"); //$NON-NLS-1$
        }

        // Unregister the receiver
        try {
            unregisterReceiver(this.mNotificationReceiver);
        } catch (Throwable ex) {
            /**NON BLOCK**/
        }

        //All destroy. Continue
        super.onDestroy();
    }

    /**
     * Method that initializes the titlebar of the activity.
     */
    private void initTitleActionBar() {
        //Configure the action bar options
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setTitle(R.string.pref);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preferences_headers, target);

        /* Manually load the header icons, since Android chokes on icons referenced via '?attr/xxx'
           which must be done for theming */
        final TypedArray a = getTheme().obtainStyledAttributes(R.styleable.FileManagerPrefs);

        for (Header header : target) {
            switch (header.titleRes) {
                case R.string.pref_general:
                    header.iconRes = a.getResourceId(R.styleable.FileManagerPrefs_preferenceIconGeneral,
                            R.drawable.ic_preference_black_general);
                    break;
                case R.string.pref_search:
                    header.iconRes = a.getResourceId(R.styleable.FileManagerPrefs_preferenceIconSearch,
                            R.drawable.ic_preference_black_search);
                    break;
                case R.string.pref_editor:
                    header.iconRes = a.getResourceId(R.styleable.FileManagerPrefs_preferenceIconEditor,
                            R.drawable.ic_preference_black_editor);
                    break;
                case R.string.pref_about:
                    header.iconRes = a.getResourceId(R.styleable.FileManagerPrefs_preferenceIconAbout,
                            R.drawable.ic_preference_black_about);
                    break;
            }
        }

        // Create Dashclock preference if the user has enabled our Dashclock extension
        if(DashExtension.isEnabled(this)) {
            Header dashHeader = new Header();
            dashHeader.titleRes = R.string.pref_dashclock;
            Intent i =  new Intent(this, DashSettings.class);
            i.putExtra("showLauncherIcon", true);
            dashHeader.intent = i;
            dashHeader.iconRes = a.getResourceId(R.styleable.FileManagerPrefs_preferenceIconDashclock,
                    R.drawable.ic_preference_black_dashclock);
            target.add(dashHeader);
        }

        a.recycle();
    }

    @Override
    protected void onResume() {
        super.onResume();
        invalidateHeaders();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
       switch (item.getItemId()) {
          case android.R.id.home:
              finish();
              return true;
          default:
             return super.onOptionsItemSelected(item);
       }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
        if (!AndroidHelper.isTablet(this) && fragment instanceof TitlePreferenceFragment) {
            getActionBar().setTitle(((TitlePreferenceFragment)fragment).getTitle());
        } else {
            getActionBar().setTitle(R.string.pref);
        }
    }

    /**
     * Method that applies the current theme to the activity
     * @hide
     */
    void applyTheme() {
        Theme theme = ThemeManager.getCurrentTheme(this);
        theme.setBaseTheme(this, false);

        // -View
        theme.setBackgroundDrawable(
                this,
                this.getWindow().getDecorView(),
                "background_drawable"); //$NON-NLS-1$
    }

}
