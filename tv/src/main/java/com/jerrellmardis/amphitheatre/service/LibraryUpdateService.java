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

package com.jerrellmardis.amphitheatre.service;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.jerrellmardis.amphitheatre.api.ApiClient;
import com.jerrellmardis.amphitheatre.model.Source;
import com.jerrellmardis.amphitheatre.model.SuperFile;
import com.jerrellmardis.amphitheatre.model.Video;
import com.jerrellmardis.amphitheatre.model.tmdb.Config;
import com.jerrellmardis.amphitheatre.task.DownloadTaskHelper;
import com.jerrellmardis.amphitheatre.util.Constants;
import com.jerrellmardis.amphitheatre.util.SecurePreferences;
import com.orm.query.Condition;
import com.orm.query.Select;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jcifs.smb.SmbFile;

import static com.jerrellmardis.amphitheatre.model.Source.Type;

/**
 * Created by Jerrell Mardis on 8/16/14.
 */
public class LibraryUpdateService extends IntentService {

    private static final String TAG = "amp:LibraryUpdate";

    public LibraryUpdateService() {
        super("LibraryUpdateService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "Update library");
        if(intent.getBooleanExtra("ALARM", false))
            return; //Stop the auto-checking for the moment as it's annoying
        try {
            List<Source> sources = Source.listAll(Source.class);
            Log.d(TAG, "Found "+sources.size()+" sources");
            Log.d(TAG, sources.toString());

            if (sources != null && !sources.isEmpty()) {
                //Dupe check
                String srcA = sources.get(0).getSource();
                for(int i = 1; i<sources.size();i++) {
//                    Log.d(TAG, "Comparing "+srcA+" with "+sources.get(i));
                    if(sources.get(i).getSource().contains(srcA) || srcA.contains(sources.get(i).getSource())) {
                        //Want to remove redundancies
                        sources.get(i).delete();
                        sources.remove(i);
                        i--;
                    } else {
                        srcA = sources.get(i).getSource();
                    }
                }
//                Log.d(TAG, "Stop");
                Log.d(TAG, "Found "+sources.size()+" sources");
                Log.d(TAG, sources.toString());
                SecurePreferences prefs = new SecurePreferences(getApplicationContext());
                String user = prefs.getString(Constants.PREFS_USER_KEY, "");
                String pass = prefs.getString(Constants.PREFS_PASSWORD_KEY, "");

                //Because we are using a new traversal method, we will not be using the methods below
                for (Source source : sources) {
                    DownloadTaskHelper.getFiles(user, pass, getPath(source));
//                    sendBroadcast(new Intent(Constants.LIBRARY_UPDATED_ACTION));
                }
                if(true)
                    return;

                try {
                    Config config = ApiClient.getInstance().createTMDbClient().getConfig();

                    for (Source source : sources) {
                        // get a list of files on the device
                        List<SmbFile> systemFiles = DownloadTaskHelper.getFiles(user, pass, getPath(source));

                        if (systemFiles != null && !systemFiles.isEmpty()) {
                            // convert the list of SmbFiles to a Map of file paths to SmbFiles
                            Map<String, SmbFile> systemFileMap = new HashMap<String, SmbFile>(systemFiles.size());
                            for (SmbFile file : systemFiles) {
                                systemFileMap.put(file.getPath(), file);
                            }

                            reconcileVideoFiles(source, config, systemFileMap);
                        }
                    }

                    sendBroadcast(new Intent(Constants.LIBRARY_UPDATED_ACTION));
                } catch(Exception e) {

                }


            }
        } catch (Exception e) {
            Log.e(TAG, "An error occurred while updating the library.", e);
        }
    }

    private void reconcileVideoFiles(Source source, Config config, Map<String, SmbFile> systemFileMap) {
        boolean isMovie = Type.MOVIE == Type.valueOf(source.getType());

        List<Video> videos = Select
                .from(Video.class)
                .where(Condition.prop("is_movie").eq(isMovie ? 1 : 0),
                        Condition.prop("video_url").like("%" + source.getSource() + "%"))
                .list();

        if (videos != null && !videos.isEmpty()) {
            // convert the list of videos saved in the db to a Map of file paths to Videos
            Map<String, Video> dbFileMap = new HashMap<>(videos.size());
            for (Video video : videos) {
                dbFileMap.put(video.getVideoUrl(), video);
            }

            Set<String> clonedSystemFileNames = new HashSet<String>(systemFileMap.keySet());

            // systemFileMap now represents a Map of files to add
            systemFileMap.keySet().removeAll(dbFileMap.keySet());

            // dbFileMap now represents a Map of files to remove
            dbFileMap.keySet().removeAll(clonedSystemFileNames);

            // delete the video and associations
            // ignore failures, continue on
            for (Map.Entry<String, Video> entry : dbFileMap.entrySet()) {
                if (isMovie) {
                    try { entry.getValue().getMovie().delete(); } catch (Exception e) { /* do nothing */ }
                    try { entry.getValue().delete(); } catch (Exception e) { /* do nothing */ }
                } else {
                    try { entry.getValue().getTvShow().getEpisode().delete(); } catch (Exception e) { /* do nothing */ }
                    try { entry.getValue().getTvShow().delete(); } catch (Exception e) { /* do nothing */ }
                    try { entry.getValue().delete(); } catch (Exception e) { /* do nothing */ }
                }
            }

            // download data for the new files
            // ignore failures, continue on
            if (!systemFileMap.values().isEmpty()) {
                for (SmbFile file : systemFileMap.values()) {
                    //Don't manage per movie or per tv show. Let's figure that out if we can
                    try {
                        DownloadTaskHelper.downloadFileData(config, new SuperFile(file));
                    } catch(Exception e) {
                        //Do nothing
                    }
                    /*if (isMovie) {
                        try { DownloadTaskHelper.downloadMovieData(config, file); } catch (Exception e) { *//* do nothing *//* }
                    } else {
                        try { DownloadTaskHelper.downloadTvShowData(config, file); } catch (Exception e) { *//* do nothing *//* }
                    }*/
                }
            }
        }
    }

    private String getPath(Source source) {
        String path = source.getSource();

        if (!path.startsWith("smb://")) {
            path = "smb://" + path;
        }

        if (!path.endsWith("/")) {
            path += "/";
        }

        return path;
    }
}