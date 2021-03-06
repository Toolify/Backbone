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

package me.toolify.backbone.commands.java;

import android.util.Log;

import me.toolify.backbone.commands.ParentDirExecutable;
import me.toolify.backbone.console.ExecutionException;
import me.toolify.backbone.console.InsufficientPermissionsException;
import me.toolify.backbone.console.NoSuchFileOrDirectory;

import java.io.File;


/**
 * A class for returns the parent directory.
 */
public class ParentDirCommand extends Program implements ParentDirExecutable {

    private static final String TAG = "ParentDirCommand"; //$NON-NLS-1$

    private final String mSrc;
    private String mParentDir;

    /**
     * Constructor of <code>ParentDirCommand</code>.
     *
     * @param src The source file
     */
    public ParentDirCommand(String src) {
        super();
        this.mSrc = src;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getResult() {
        return this.mParentDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute()
            throws InsufficientPermissionsException, NoSuchFileOrDirectory, ExecutionException {
        if (isTrace()) {
            Log.v(TAG,
                    String.format("Getting parent directory of %s", //$NON-NLS-1$
                            this.mSrc));
        }

        File f = new File(this.mSrc);
        this.mParentDir = f.getParent();

        if (isTrace()) {
            Log.v(TAG,
                    String.format("Parent directory: %S", //$NON-NLS-1$
                            this.mParentDir));
        }

        if (isTrace()) {
            Log.v(TAG, "Result: OK"); //$NON-NLS-1$
        }
    }

}
