/*
 * Copyright (C) 2012 The CyanogenMod Project
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

package me.toolify.backbone.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;

import me.toolify.backbone.ui.ThemeManager.Theme;

import java.util.HashMap;
import java.util.Map;

/**
 * A class that holds icons for a more efficient access.
 */
public class IconHolder {

    private final Map<String, Drawable> mIcons;     // Themes based
    private final Context mContext;

    /**
     * Constructor of <code>IconHolder</code>.
     *
     */
    public IconHolder(Context context) {
        super();
        this.mContext = context;
        this.mIcons = new HashMap<String, Drawable>();
    }

    /**
     * Method that returns a drawable reference of a icon.
     *
     * @param resid The resource identifier
     * @return Drawable The drawable icon reference
     */
    public Drawable getDrawable(final String resid) {
        //Check if the icon exists in the cache
        if (this.mIcons.containsKey(resid)) {
            return this.mIcons.get(resid);
        }

        //Load the drawable, cache and returns reference
        Theme theme = ThemeManager.getCurrentTheme(mContext);
        Drawable dw = theme.getDrawable(mContext, resid);
        this.mIcons.put(resid, dw);
        return dw;
    }

    /**
     * Free any resources used by this instance
     */
    public void cleanup() {
        this.mIcons.clear();
    }
}
