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

package me.toolify.backbone.activities;

import android.app.Activity;

import me.toolify.backbone.FileManagerApplication;
import me.toolify.backbone.console.ConsoleBuilder;
import me.toolify.backbone.ui.widgets.BreadcrumbListener;

public abstract class AbstractNavigationActivity extends Activity
    implements BreadcrumbListener {

    // The flag indicating whether or not the application is just starting up.
    // Used in initializing the action bar's breadcrumb once the fragments have
    // finished loading.
    private boolean mFirstRun = true;

    /**
     * Determine whether the "just started up" flag is true or not.  Used to perform
     * actions on/based on the first (and only visible) navigationFragment loaded.
     */
    public boolean isFirstRun() {
        return mFirstRun;
    }

    /**
     * Change the app's "just started up" flag
     */
    public void setFirstRun(boolean firstRun) {
        this.mFirstRun = firstRun;
    }

    /**
     * Method that updates the titlebar of the activity or dialog
     */
    public abstract void updateTitleActionBar();

    /**
     * Method called when a controlled exit is required
     * @hide
     */
    public void exit() {
        try {
            FileManagerApplication.destroyBackgroundConsole();
        } catch (Throwable ex) {
            /**NON BLOCK**/
        }
        try {
            ConsoleBuilder.destroyConsole();
        } catch (Throwable ex) {
            /**NON BLOCK**/
        }
        finish();
    }
}
