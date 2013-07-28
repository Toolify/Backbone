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
package me.toolify.backbone.util;

import android.content.Context;
import android.os.storage.StorageManager;
import android.util.Log;

import me.toolify.backbone.FileManagerApplication;
import me.toolify.backbone.R;
import me.toolify.backbone.model.FileSystemObject;
import me.toolify.backbone.model.MountPoint;
import me.toolify.backbone.model.StorageVolume;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;


/**
 * A helper class with useful methods for deal with storages.
 */
public final class StorageHelper {
    private final static String TAG = "StorageHelper";
    private final static boolean DEBUG = true;

    private static Object[] sStorageVolumes;

    /**
     * Method that returns the storage volumes defined in the system.  This method uses
     * reflection to retrieve the method because CM10 has a {@link Context}
     * as first parameter, that AOSP hasn't.
     *
     * @param ctx The current context
     * @return StorageVolume[] The storage volumes defined in the system
     */
    @SuppressWarnings("boxing")
    public static synchronized Object[] getStorageVolumes(Context ctx) {
        if (sStorageVolumes == null) {
            //IMP!! Android SDK doesn't have a "getVolumeList" but is supported by CM10.
            //Use reflect to get this value (if possible)
            try {
                StorageManager sm = (StorageManager) ctx.getSystemService(Context.STORAGE_SERVICE);
                Method method = sm.getClass().getMethod("getVolumeList"); //$NON-NLS-1$
                sStorageVolumes = (Object[])method.invoke(sm);

            } catch (Exception ex) {
                //Ignore. Android SDK StorageManager class doesn't have this method
                //Use default android information from environment
                try {
                    HashMap<String, StorageVolume> lst = new HashMap<String, StorageVolume>();
                    //lst.add(convertToStorageVolume(getInternalStorageDirectory()));
                    for(MountPoint mp : CommandHelper.getMountPoints(ctx, null))
                    {
                        if(!MountPointHelper.isReadWrite(mp)) continue;
                        String path = mp.getMountPoint();
                        if(!mp.getDevice().startsWith("/")) continue;
                        if(path.matches("^\\/(dev|persist|cache|proc|efs|data)"))
                           continue;
                        StorageVolume sv = null;
                        try {
                            if(MountPointHelper.getMountPointDiskUsage(mp).getTotal() < 5000000)
                                continue;
                            FileSystemObject fso = CommandHelper.getFileInfo(ctx, path, null);
                            sv = convertToStorageVolume(fso);
                        } catch(Exception e3) { }
                        if(sv == null) continue;
                        if(!lst.containsKey(mp.getDevice())) // Only one volume per device
                            lst.put(mp.getDevice(), sv);
                    }
                    sStorageVolumes = lst.values().toArray(new StorageVolume[lst.size()]);
                } catch (Exception ex2) {
                    if(DEBUG)
                        Log.e(TAG, "Unable to fallback to getStorageVolumes", ex2);
                }
            }
            if (sStorageVolumes == null) {
                sStorageVolumes = new StorageVolume[]{};
            }
        }
        return sStorageVolumes;
    }

    private static StorageVolume convertToStorageVolume(FileSystemObject fso)
    {
        String path = fso.getFullPath();
        int description = R.string.internal_storage;
        if (path.toLowerCase().indexOf("usb") != -1) //$NON-NLS-1$
            description = R.string.usb_storage;
        else if(path.toLowerCase().indexOf("ext") > -1 ||
                path.indexOf("1") > -1)
            description = R.string.external_storage;
        return new StorageVolume(FileHelper.getFile(fso), description, false,
                description == R.string.usb_storage, false, 0, false, 0, null);
    }

    /**
     * Method that returns the storage volume description. This method uses
     * reflection to retrieve the description because CM10 has a {@link Context}
     * as first parameter, that AOSP hasn't.
     *
     * @param ctx The current context
     * @param volume The storage volume
     * @return String The description of the storage volume
     */
    public static String getStorageVolumeDescription(Context ctx, Object volume) {
        try {
            Method m = volume.getClass().getMethod("getDescription", Context.class);
            return (String)m.invoke(volume, ctx);
        } catch (Throwable _throw) {
            // Returns the volume storage path
            return getStoragePath(volume);
        }
    }

    /**
     * Method that returns if the path is in a volume storage
     *
     * @param path The path
     * @return boolean If the path is in a volume storage
     */
    public static boolean isPathInStorageVolume(String path) {
        String fso = FileHelper.getAbsPath(path);
        Object[] volumes =
                getStorageVolumes(FileManagerApplication.getInstance().getApplicationContext());
        int cc = volumes.length;
        for (int i = 0; i < cc; i++) {
            Object vol = volumes[i];
            if (fso.startsWith(getStoragePath(vol))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Method that determines the mount path of a {@link StorageVolume} object.
     *
     * @param volume Object returned from {@link StorageHelper.getStorageVolumes()}
     * @return Mounted path
     */
    public static String getStoragePath(Object volume)
    {
        try {
            Method gpm = volume.getClass().getMethod("getPath");
            Object gpo = gpm.invoke(volume);
            return String.valueOf(gpo);
        } catch (Exception e) {
            if(volume instanceof String) return (String)volume;
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Method that determines whether a path returned from
     * {@link StorageHelper.getStorageVolumes()} is a valid, mounted, path.
     * @param path Path to root of mount
     * @return boolean Whether path is mounted & valid
     */
    public static boolean isValidMount(String path)
    {
        MountPoint mp = MountPointHelper.getMountPointFromDirectory(path);
        if(mp == null) return false;
        if(mp.getOptions() == null) return false;
        String opts = mp.getOptions();
        if(opts != null && opts.indexOf("mode=0")>-1)
            return false; // Skip drives that are not owner read/write
        return true;
    }

    /**
     * Method that returns if the path is a storage volume
     *
     * @param path The path
     * @return boolean If the path is a storage volume
     */
    public static boolean isStorageVolume(String path) {
        Object[] volumes =
                getStorageVolumes(FileManagerApplication.getInstance().getApplicationContext());
        int cc = volumes.length;
        for (int i = 0; i < cc; i++) {
            Object vol = volumes[i];
            String p = new File(path).getAbsolutePath();
            String v = new File(getStoragePath(vol)).getAbsolutePath();
            if (p.compareTo(v) == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Method that return the chrooted path of an absolute path. xe: /storage/sdcard0 --> sdcard0.
     *
     * @param path The path
     * @return String The chrooted path
     */
    public static String getChrootedPath(String path) {
        Object[] volumes =
                getStorageVolumes(FileManagerApplication.getInstance().getApplicationContext());
        int cc = volumes.length;
        for (int i = 0; i < cc; i++) {
            Object vol = volumes[i];
            File p = new File(path);
            File v = new File(getStoragePath(vol));
            if (p.getAbsolutePath().startsWith(v.getAbsolutePath())) {
                return v.getName() + path.substring(v.getAbsolutePath().length());
            }
        }
        return null;
    }

}
