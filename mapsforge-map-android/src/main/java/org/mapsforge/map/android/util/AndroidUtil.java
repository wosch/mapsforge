/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mapsforge.map.android.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.os.StatFs;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.map.layer.cache.FileSystemTileCache;
import org.mapsforge.map.layer.cache.InMemoryTileCache;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.cache.TwoLevelTileCache;

import java.io.File;

public class AndroidUtil {

    private AndroidUtil() {
        // noop, for privacy
    }

    public static final boolean honeyCombPlus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;

    /**
     * @return true if the current thread is the UI thread, false otherwise.
     */
    public static boolean currentThreadIsUiThread() {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    /**
     * Utility function to create a two-level tile cache with the right size. When the cache
     * is created we do not actually know the size of the mapview, so the screenRatio is an
     * approximation of the required size
     * @param c              the Android context
     * @param id             name for the storage directory
     * @param screenRatio    part of the screen the view takes up
     * @param overdraw       overdraw allowance
     * @return a new cache created on the external storage
     */

    public static TileCache createTileCache(Context c, String id, float screenRatio, double overdraw) {
        int cacheSize = (int) Math.round(AndroidUtil.getMinimumCacheSize(c,
                overdraw, screenRatio));
        return createExternalStorageTileCache(c, id, cacheSize);
    }

    /**
     * @param c              the Android context
     * @param id             name for the directory
     * @param firstLevelSize size of the first level cache
     * @return a new cache created on the external storage
     */
    public static TileCache createExternalStorageTileCache(Context c, String id, int firstLevelSize) {
	    Log.d("TILECACHE INMEMORY SIZE", Integer.toString(firstLevelSize));
	    TileCache firstLevelTileCache = new InMemoryTileCache(firstLevelSize);
        String cacheDirectoryName = c.getExternalCacheDir().getAbsolutePath() + File.separator + id;
        File cacheDirectory = new File(cacheDirectoryName);
        if (!cacheDirectory.exists()) {
            cacheDirectory.mkdir();
        }
        int tileCacheFiles = estimateSizeOfFileSystemCache(cacheDirectoryName, firstLevelSize);
        if (cacheDirectory.canWrite() && tileCacheFiles > 0) {
            try {
	            Log.d("TILECACHE FILECACHE SIZE", Integer.toString(firstLevelSize));
                TileCache secondLevelTileCache = new FileSystemTileCache(tileCacheFiles, cacheDirectory,
                        org.mapsforge.map.android.graphics.AndroidGraphicFactory.INSTANCE);
                return new TwoLevelTileCache(firstLevelTileCache, secondLevelTileCache);
            } catch (IllegalArgumentException e) {
                Log.w("TILECACHE", e.toString());
            }
        }
        return firstLevelTileCache;
    }

    /**
     * @param cacheDirectoryName where the file system tile cache will be located
     * @param firstLevelSize     size of the first level cache, no point cache being smaller
     * @return recommended number of files in FileSystemTileCache
     */
    public static int estimateSizeOfFileSystemCache(String cacheDirectoryName, int firstLevelSize) {
        // assumption on size of files in cache, on the large side as not to eat
        // up all free space, real average probably 50K compressed
        final int tileCacheFileSize = 4 * GraphicFactory.getTileSize() * GraphicFactory.getTileSize();
        final int maxCacheFiles = 2000; // arbitrary, probably too high

        // result cannot be bigger than maxCacheFiles
        int result = (int) Math.min(maxCacheFiles, getAvailableCacheSlots(cacheDirectoryName, tileCacheFileSize));

        if (firstLevelSize > result) {
            // no point having a file system cache that does not even hold the memory cache
            result = 0;
        }
        return result;
    }

    /**
     * Get the number of tiles that can be stored on the file system
     *
     * @param directory where the cache will reside
     * @param fileSize average size of tile to be cached
     * @return number of tiles that can be stored without running out of space
     */
    @SuppressWarnings("deprecation")
    @TargetApi(18)
    public static long getAvailableCacheSlots(String directory, int fileSize) {
        StatFs statfs = new StatFs(directory);
        if (android.os.Build.VERSION.SDK_INT >= 18){
            return statfs.getAvailableBytes() / fileSize;
        }
        // problem is overflow with devices with large storage, so order is important here
        int result = statfs.getAvailableBlocks() / (fileSize / statfs.getBlockSize());
        return result;
    }
    /**
     * Compute the minimum cache size for a view
     *
     * @param c the context.
     * @param overdrawFactor the overdraw factor applied to the mapview.
     * @param screenRatio the part of the screen the view covers.
     * @return the minimum cache size for the view.
     */
    public static int getMinimumCacheSize(Context c, double overdrawFactor, float screenRatio) {
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        return (int)(screenRatio * Math.ceil(1 + (display.getHeight()  * overdrawFactor / GraphicFactory.getTileSize()))
                * Math.ceil(1 + (display.getWidth()  * overdrawFactor / GraphicFactory.getTileSize())));
    }


}