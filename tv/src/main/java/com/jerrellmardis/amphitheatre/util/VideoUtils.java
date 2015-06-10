/*
 * Copyright (C) 2014 Jerrell Mardis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jerrellmardis.amphitheatre.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.github.mjdev.libaums.fs.UsbFile;
import com.jerrellmardis.amphitheatre.model.FileSource;
import com.jerrellmardis.amphitheatre.model.Video;
import com.jerrellmardis.amphitheatre.server.Streamer;
import com.orm.query.Condition;
import com.orm.query.Select;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;

/**
 * Created by Jerrell Mardis on 8/4/14.
 */
public class VideoUtils {

    private static final int NOT_FOUND = -1;
    private static final char EXTENSION_SEPARATOR = '.';
    private static final char UNIX_SEPARATOR = '/';
    private static final char WINDOWS_SEPARATOR = '\\';
    private static final String TAG = "amp:VideoUtils";

    public static void playVideo(WeakReference<Activity> ref, final Video video) {
        final Activity activity = ref.get();

        if (activity != null) {
            final Streamer streamer = Streamer.getInstance();
            //Make sure we get the correct video
            final String[] vurl = {video.getVideoUrl()};
            if(video.getVideoUrls().length > 1) {
                AlertDialog ad = new AlertDialog.Builder(activity)
                        .setItems(video.getVideoUrls(), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                vurl[0] = video.getVideoUrls()[which];
                                Log.d(TAG, "Selected video "+which+" "+ vurl[0]);
                                //Let's hack a new video object and player
                                Video mVideo = new Video();
                                mVideo.setVideoUrl(vurl[0]);
                                mVideo.setName(video.getName());
                                playVideo(new WeakReference<Activity>(activity), mVideo);
                            }
                        })
                        .setTitle("Play Which File?")
                        .show();
                return;
            }

            final String finalVurl = vurl[0];
            streamer.setOnStreamListener(new Streamer.OnStreamListener() {
                @Override
                public void onStream(int percentStreamed) {
                    // FIXME Ideally, the watch status should only get set once the server has streamed a certain % of the video.
                    //(a 120min video w/ 5 min credits is 115/120 ~96%
                    // Unfortunately a partial stream is only set when a user has requested to play a partially watched video.
                }

                @Override
                public void onPlay() {
                    video.setWatched(true);

                    List<Video> videos = Select
                            .from(Video.class)
                            .where(Condition.prop("video_url").eq(video.getVideoUrl()))
                            .list();

                    if (!videos.isEmpty()) {
                        Video vid = videos.get(0);
                        if (!vid.isWatched()) {
                            vid.setWatched(true);
                            vid.save();
                        }
                    }
                }
            });

            if(video.getSource() == FileSource.SMB) {
                Log.d("amp:VideoUtils", "Looking to play non-local video "+video.getName()+", "+finalVurl);
                new Thread() {
                    public void run() {
                        try {
                            SecurePreferences preferences = new SecurePreferences(activity.getApplicationContext());

                            String user = preferences.getString(Constants.PREFS_USER_KEY, "");
                            String pass = preferences.getString(Constants.PREFS_PASSWORD_KEY, "");
                            NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication("", user, pass);
                            SmbFile file = new SmbFile(finalVurl, auth);
                            streamer.setStreamSrc(file, null);

                            activity.runOnUiThread(new Runnable() {
                                public void run() {
                                    try {
                                        Uri uri = Uri.parse(Streamer.URL + Uri.fromFile(new File(Uri.parse(finalVurl).getPath())).getEncodedPath());
                                        Intent i = new Intent(Intent.ACTION_VIEW);
                                        i.setDataAndType(uri, VideoUtils.getMimeType(finalVurl, true));
                                        activity.startActivity(i);
                                    } catch (ActivityNotFoundException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
            } else if(video.getSource() == FileSource.INTERNAL_STORAGE) {
                Log.d(TAG, "Playing local video " +video.getName()+" @ "+video.getVideoUrl());
//                File myVideo = new File(video.getVideoUrl());
//                Log.d(TAG, "File "+Uri.fromFile(myVideo));
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(Uri.parse(video.getVideoUrl()), VideoUtils.getMimeType(video.getVideoUrl(), true));
                activity.startActivity(i);
            } else if(video.getSource() == FileSource.USB) {
                CopyTaskParam param = new CopyTaskParam();
                Log.d(TAG, "Start copy task for "+video.getName()+" @ " +video.getVideoUrl());
                try {
                    param.from = (UsbFile) video.getFile(activity).getFile();
                    File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                            + "/usbfileman/cache");
                    f.mkdirs();
                    int index = param.from.getName().lastIndexOf(".");
                    String prefix = param.from.getName().substring(0, index);
                    String ext = param.from.getName().substring(index);
                    // prefix must be at least 3 characters
                    if(prefix.length() < 3) {
                        prefix += "pad";
                    }
                    try {
                        param.to = File.createTempFile(prefix, ext, f);
                        new CopyTask(activity).execute(param);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public static boolean isVideoFile(String s) {
        String[] fileTypes = new String[]{".3gp", ".aaf.", "mp4", ".ts", ".webm", ".m4v", ".mkv", ".divx", ".xvid", ".rec", ".avi", ".flv", ".f4v", ".moi", ".mpeg", ".mpg", /*".mts", ".m2ts",*/ ".ogv", ".rm", ".rmvb", ".mov", ".wmv", /*".iso",*/ ".vob", ".ifo", ".wtv", ".pyv", ".ogm", /*".img"*/};
        int count = fileTypes.length;
        for (int i = 0; i < count; i++)
            if (s.toLowerCase().endsWith(fileTypes[i]))
                return true;
        return false;
    }

    public static String getMimeType(String filepath, boolean useWildcard) {
        if (useWildcard)
            return "video/*";

        HashMap<String, String> mimeTypes = new HashMap<String, String>();
        mimeTypes.put("3gp", "video/3gpp");
        mimeTypes.put("aaf", "application/octet-stream");
        mimeTypes.put("mp4", "video/mp4");
        mimeTypes.put("ts", "video/mp2t");
        mimeTypes.put("webm", "video/webm");
        mimeTypes.put("m4v", "video/x-m4v");
        mimeTypes.put("mkv", "video/x-matroska");
        mimeTypes.put("divx", "video/x-divx");
        mimeTypes.put("xvid", "video/x-xvid");
        mimeTypes.put("rec", "application/octet-stream");
        mimeTypes.put("avi", "video/avi");
        mimeTypes.put("flv", "video/x-flv");
        mimeTypes.put("f4v", "video/x-f4v");
        mimeTypes.put("moi", "application/octet-stream");
        mimeTypes.put("mpeg", "video/mpeg");
        mimeTypes.put("mpg", "video/mpeg");
        mimeTypes.put("mts", "video/mts");
        mimeTypes.put("m2ts", "video/mp2t");
        mimeTypes.put("ogv", "video/ogg");
        mimeTypes.put("rm", "application/vnd.rn-realmedia");
        mimeTypes.put("rmvb", "application/vnd.rn-realmedia-vbr");
        mimeTypes.put("mov", "video/quicktime");
        mimeTypes.put("wmv", "video/x-ms-wmv");
        mimeTypes.put("iso", "application/octet-stream");
        mimeTypes.put("vob", "video/dvd");
        mimeTypes.put("ifo", "application/octet-stream");
        mimeTypes.put("wtv", "video/wtv");
        mimeTypes.put("pyv", "video/vnd.ms-playready.media.pyv");
        mimeTypes.put("ogm", "video/ogg");
        mimeTypes.put("img", "application/octet-stream");

        String mime = mimeTypes.get(getExtension(filepath));
        if (mime == null)
            return "video/*";
        return mime;
    }

    public static String getExtension(final String filename) {
        if (filename == null) {
            return null;
        }
        final int index = indexOfExtension(filename);
        if (index == NOT_FOUND) {
            return "";
        } else {
            return filename.substring(index + 1);
        }
    }

    public static int indexOfExtension(final String filename) {
        if (filename == null) {
            return NOT_FOUND;
        }
        final int extensionPos = filename.lastIndexOf(EXTENSION_SEPARATOR);
        final int lastSeparator = indexOfLastSeparator(filename);
        return lastSeparator > extensionPos ? NOT_FOUND : extensionPos;
    }

    public static int indexOfLastSeparator(final String filename) {
        if (filename == null) {
            return NOT_FOUND;
        }
        final int lastUnixPos = filename.lastIndexOf(UNIX_SEPARATOR);
        final int lastWindowsPos = filename.lastIndexOf(WINDOWS_SEPARATOR);
        return Math.max(lastUnixPos, lastWindowsPos);
    }

    public static Intent getVideoIntent(Video video) {
        return getVideoIntent(video.getVideoUrl().replace("smb", "http"), "video/*", video);
    }

    public static Intent getVideoIntent(String fileUrl, String mimeType, Video video) {
        if (fileUrl.startsWith("http")) {
            return getVideoIntent(Uri.parse(fileUrl), mimeType, video);
        }

        Intent videoIntent = new Intent(Intent.ACTION_VIEW);
        videoIntent.setDataAndType(Uri.fromFile(new File(fileUrl)), mimeType);
        videoIntent.putExtras(getVideoIntentBundle(video));

        return videoIntent;
    }

    public static Intent getVideoIntent(Uri file, String mimeType, Video video) {
        Intent videoIntent = new Intent(Intent.ACTION_VIEW);
        videoIntent.setDataAndType(file, mimeType);
        videoIntent.putExtras(getVideoIntentBundle(video));

        return videoIntent;
    }

    private static Bundle getVideoIntentBundle(Video video) {
        Bundle b = new Bundle();

        String title = video.getName();

        if (video.getMovie() != null) {
            b.putString("plot", video.getMovie().getOverview());
            b.putString("date", video.getMovie().getReleaseDate());
            b.putString("cover", video.getCardImageUrl());
        } else if (video.getTvShow() != null) {
            b.putString("plot", video.getTvShow().getOverview());
            b.putString("date", video.getTvShow().getFirstAirDate());
            b.putString("cover", video.getCardImageUrl());
        }

        b.putString("title", title);
        b.putString("forcename", title);
        b.putBoolean("forcedirect", true);

        return b;
    }

    public static List<SmbFile> getFilesFromDir(String path, NtlmPasswordAuthentication auth) throws Exception {
        List<SmbFile> results = new ArrayList<SmbFile>();
        Set<SmbFile> seen = new LinkedHashSet<SmbFile>();
        Deque<SmbFile> queue = new ArrayDeque<SmbFile>();

        Log.d(TAG, "getting files from dir "+path+" with auth "+auth.getDomain()+" "+auth.getName()+" "+auth.getUsername()+" "+auth.getPassword());
        SmbFile baseDir = new SmbFile(path, auth);
        //See if this worked
        Log.d(TAG, baseDir.getName()+", "+baseDir.getPath());
        queue.add(baseDir);

        while (!queue.isEmpty()) {
            SmbFile file = queue.removeFirst();
            seen.add(file);
            Log.d(TAG, file.getName()+", "+file.getPath());

            if (file.isDirectory()) {
                Log.d(TAG, "Found directory " +file.getName()+", "+file.getPath());
                Set<SmbFile> smbFiles = new LinkedHashSet<SmbFile>();
                try {
                    Collections.addAll(smbFiles, file.listFiles());
                    Log.d(TAG, "Got past "+file.getName());
                    //TODO Let's create a dialog to show all this stuff
                } catch(Exception e) {
                    Log.d(TAG, "List files failed "+e.getMessage());
                }

                for (SmbFile child : smbFiles) {
                    if (!seen.contains(child)) {
                        queue.add(child);
                    }
                }
            } else if (VideoUtils.isVideoFile(file.getName())) {
                results.add(file);
            }
        }

        return results;
    }

    /**
     * Class to hold the files for a copy task. Holds the source and the
     * destination file.
     *
     * @author mjahnen
     *
     */
    private static class CopyTaskParam {
        /* package */UsbFile from;
        /* package */File to;
    }
    /**
     * Asynchronous task to copy a file from the mass storage device connected
     * via USB to the internal storage.
     *
     * @author mjahnen
     *
     */
    private static class CopyTask extends AsyncTask<CopyTaskParam, Integer, Void> {

        private ProgressDialog dialog;
        private CopyTaskParam param;
        private Activity mActivity;

        public CopyTask(Activity activity) {
            mActivity = activity;
            dialog = new ProgressDialog(mActivity);
            dialog.setTitle("Copying File");
            dialog.setMessage("Copying a file to the internal storage, this can take some time! The file can only be played on internal storage!");
            dialog.setIndeterminate(false);
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        }

        @Override
        protected void onPreExecute() {
            dialog.show();
        }

        @Override
        protected Void doInBackground(CopyTaskParam... params) {
            long time = System.currentTimeMillis();
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            param = params[0];
            long length = params[0].from.getLength();
            try {
                FileOutputStream out = new FileOutputStream(params[0].to);
                for (long i = 0; i < length; i += buffer.limit()) {
                    buffer.limit((int) Math.min(buffer.capacity(), length - i));
                    params[0].from.read(i, buffer);
                    out.write(buffer.array(), 0, buffer.limit());
                    publishProgress((int) i);
                    buffer.clear();
                }
                out.close();
            } catch (IOException e) {
                Log.e(TAG, "error copying!", e);
            }
            Log.d(TAG, "copy time: " + (System.currentTimeMillis() - time));
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            dialog.dismiss();

            Intent myIntent = new Intent(android.content.Intent.ACTION_VIEW);
            File file = new File(param.to.getAbsolutePath());
            String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(Uri
                    .fromFile(file).toString());
            String mimetype = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    extension);
            myIntent.setDataAndType(Uri.fromFile(file), mimetype);
            try {
                mActivity.startActivity(myIntent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(mActivity, "Could no find an app for that file!",
                        Toast.LENGTH_LONG).show();
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            dialog.setMax((int) param.from.getLength());
            dialog.setProgress(values[0]);
        }

    }
}
