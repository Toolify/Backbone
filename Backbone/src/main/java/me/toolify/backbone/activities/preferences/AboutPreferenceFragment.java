/*
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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;

import me.toolify.backbone.R;
import me.toolify.backbone.activities.ChangeLogActivity;
import me.toolify.backbone.preferences.Preferences;

/**
 * A class that manages the 'about' screen
 */
public class AboutPreferenceFragment extends TitlePreferenceFragment {

    private static final String TAG = "AboutPreferenceFragment"; //$NON-NLS-1$

    private static final boolean DEBUG = false;

    private Preference mFaq;
    private Preference mChangelog;
    private Preference mPrivacyPolicy;
    private Preference mCreditsAndLicenses;
    private Preference mVersion;

    /**
     * @hide
     */
    boolean mLoaded = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Change the preference manager
        getPreferenceManager().setSharedPreferencesName(Preferences.SETTINGS_FILENAME);
        getPreferenceManager().setSharedPreferencesMode(Context.MODE_PRIVATE);
        this.mLoaded = false;

        // Add the preferences
        addPreferencesFromResource(R.xml.preferences_about);

        // Frequently Asked Questions
        this.mFaq = findPreference("bb_filemanager_faq");
        this.mFaq.setEnabled(false);

        // Changelog
        this.mChangelog = findPreference("bb_filemanager_changelog");
        mChangelog.setIntent(new Intent(getActivity(), ChangeLogActivity.class));
        this.mChangelog.setEnabled(false);

        // Privacy Policy
        this.mPrivacyPolicy = findPreference("bb_filemanager_privacy_policy");
        this.mPrivacyPolicy.setEnabled(false);

        // Credits and Open Source Licenses
        this.mCreditsAndLicenses = findPreference("bb_filemanager_credits_licenses");
        mCreditsAndLicenses.setIntent(new Intent(getActivity(), LicenseActivity.class));;

        // Build Version
        this.mVersion = findPreference("bb_filemanager_version");
        // Retrieve the about header
        try {
            String appver =
                    getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
            mVersion.setSummary(getString(R.string.pref_about_version_num, appver));
        } catch (Exception e) {
            mVersion.setSummary(getString(R.string.pref_about_version_num, "")); //$NON-NLS-1$
        }

        // Loaded
        this.mLoaded = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CharSequence getTitle() {
        return getString(R.string.pref_about);
    }
}
