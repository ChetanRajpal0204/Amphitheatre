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

package com.jerrellmardis.amphitheatre.task;

import com.jerrellmardis.amphitheatre.api.ApiClient;
import com.jerrellmardis.amphitheatre.api.GuessItClient;
import com.jerrellmardis.amphitheatre.model.FileSource;
import com.jerrellmardis.amphitheatre.model.SuperFile;
import com.jerrellmardis.amphitheatre.model.Video;
import com.jerrellmardis.amphitheatre.model.guessit.Guess;
import com.jerrellmardis.amphitheatre.model.tmdb.Config;
import com.jerrellmardis.amphitheatre.model.tmdb.Episode;
import com.jerrellmardis.amphitheatre.model.tmdb.Movie;
import com.jerrellmardis.amphitheatre.model.tmdb.SearchResult;
import com.jerrellmardis.amphitheatre.model.tmdb.TvShow;
import com.jerrellmardis.amphitheatre.util.Constants;
import com.jerrellmardis.amphitheatre.util.VideoUtils;
import com.orm.query.Condition;
import com.orm.query.Select;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * Created by Jerrell Mardis on 8/15/14.
 */
public final class DownloadTaskHelper {
    private static final String TAG = "amp:DownloadTaskHlpr";

    public static List<SmbFile> getFiles(String user, String password, String path) {
        Log.d(TAG, path+" "+user+"//"+password);
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication("", user, password);

        List<SmbFile> files = Collections.emptyList();
        /*try {
//            files = VideoUtils.getFilesFromDir(path, auth);
        } catch (Exception e) {
            Log.d(TAG, e.getMessage()+"");
            e.printStackTrace();
            return new ArrayList<>();
        }*/
        //Let's do this in a way that's not going to blow out the memory of the device
        Log.d(TAG, "getting files from dir "+path+" with auth "+auth.getDomain()+" "+auth.getName()+" "+auth.getUsername()+" "+auth.getPassword());
        SmbFile baseDir = null;
        try {
            baseDir = new SmbFile(path, auth);
            Log.d(TAG, "Base Directory: " + baseDir.getName() + ", " + baseDir.getPath());
            traverseSmbFiles(baseDir, auth);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            Log.e(TAG, "MalformedURLException: "+e.getMessage());
        } catch (SmbException e) {
            e.printStackTrace();
            Log.e(TAG, "SmbException: "+e.getMessage());
        }


        /*while (!queue.isEmpty()) {
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
        }*/

        return files;
    }

    public static void traverseSmbFiles(SmbFile root, NtlmPasswordAuthentication auth) throws SmbException, MalformedURLException {
        int fileCount = 0;
        boolean video_folder = false;
        final Video v = new Video();
        Handler h = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
//                            super.handleMessage(msg);
                Video vid = (Video) msg.getData().getSerializable("VIDEO");
                Log.d(TAG, "Handle " + msg.getData().getInt("WHEN") + " "+ vid.getName());
                updateSingleVideo(vid, null);
            }

        };


        for(SmbFile f: root.listFiles()) {
            if(f.getParent().contains("Entertainment Media")) {
//                Log.d(TAG, "Discovered "+f.getPath()+" "+f.isDirectory());
            }
            if(f.isDirectory()) {
                try {
                    //TODO Port VOB folder support to USB and internal storage
                    SmbFile[] directoryContents = f.listFiles();
//                    Log.d(TAG, "Going into directory "+f.getPath());
                    //If this works, then we can explore
                    //Let's do some quick name checking to for time savings
                    if(f.getName().contains("Entertainment Media")) {
//                        Log.d(TAG, Arrays.asList(f.listFiles()).toString());
                    }
                    if(!f.getName().contains("iTunes")
                            && !f.getName().contains("Digi Pix")
                            && !f.getName().contains("AppData")
                            && !f.getName().startsWith(".")
                            && !f.getName().contains("Avid")
                            && !f.getName().contains("Spotify")
                            && !f.getName().contains("audacity_temp")
                            && !f.getName().contains("Media_previews")
                            && !f.getName().contains("GFX_previews")
                            && !f.getName().contains("Samsung Install Files")
                            && !f.getName().contains("AE Renders")
                            && !f.getName().contains("LocalData")
                            && !f.getName().contains("Documents") //TEMP
                            /*&& !f.getName().contains("Thrive Music Video") //TEMP*/
                            /*&& f.getPath().contains("Entertainment") //TEMP*/
                            && !f.getName().contains("Preview Files")) {
//                        Log.d(TAG, "Check "+f.getPath());
                        traverseSmbFiles(f, auth);
                    } else {
//                        Log.d(TAG, "Don't check " + f.getPath());
                    }
                } catch(Exception e) {
                    //This folder isn't accessible
                    Log.d(TAG, "Inaccessible: "+f.getName()+" "+e.getMessage());
                    //This will save us time in the traversal
                }
            } else /*if(f.getPath().contains("Holly"))*/{ //TEMP
                //Is this something we want to add?
//                Log.d(TAG, "Non-directory "+f.getPath());
                if(VideoUtils.isVideoFile(f.getPath())) {
//                    Log.d(TAG, "Is video");
                    //Perhaps. Let's do some checking.
                    /* VOB check
                        If the files are in a structure like:
                        { Movie Name } -> VIDEO_TS -> VTS_nn_n.vob

                        Then use the movie name as the source, and each vob url will
                        be added in a comma-separated list to the video url string
                    */

                    if(f.getPath().contains("VIDEO_TS")) {
                        Log.d(TAG, "Special case for "+f.getPath());
                        //We have a special case!
                        String grandparentPath = f.getPath().substring(0, f.getPath().indexOf("VIDEO_TS"));
                        SmbFile grandparent = new SmbFile(grandparentPath, auth);

                        //Let's delete this video and all like it from our video database
                        //TODO Makes more sense to not delete and replace a video, just to update in place
//                        Log.d(TAG, "Purge where video_url like "+"%" + grandparent.getPath().replace("'", "\'") + "%");
                        List<Video> videos = Select
                                .from(Video.class)
                                .where(Condition.prop("video_url").like("%" + grandparent.getPath().replace("'", "\'") + "%"))
                                .list();
//                        Log.d(TAG, "Purging "+videos.size()+" item(s)");
                        for(Video vx: videos) {
//                            Log.d(TAG, "Deleting "+vx.getVideoUrl());
                            vx.delete();
                        }

                        v.setName(grandparent.getName().replace("/", "").replace("_", " ")+".avi"); //FIXME VOB currently not supported
                        v.setSource(FileSource.SMB);
                        v.setIsMatched(true); //Kind of a lie, but we know it's a thing!
                        //Get all the video files
                        ArrayList<String> urls = new ArrayList<>();
                        for(SmbFile f2: grandparent.listFiles()) {
                            for(SmbFile f3: f2.listFiles()) {
                                if(VideoUtils.isVideoFile(f3.getPath())) {
                                    //Presumably in order
                                    urls.add(f3.getPath());
                                }
                            }
                        }
//                        Log.d(TAG, urls.toString()); //This works well
                        v.setVideoUrl(urls);
                        video_folder = true;
                    } else {
                        //Add the video like normal
                        //Let's delete this video and all like it from our video database
                        List<Video> videos = Select
                                .from(Video.class)
                                .where(Condition.prop("video_url").like("%" + f.getPath().replace("'", "\'") + "%"))
                                .list();
//                        Log.d(TAG, "Purging "+videos.size()+" item(s)");
                        for(Video vx: videos) {
//                            Log.d(TAG, "Deleting "+vx.getVideoUrl());
                            vx.delete();
                        }

                        v.setName(f.getName());
                        v.setSource(FileSource.SMB);
                        v.setVideoUrl(f.getPath());

                        fileCount++;
                        //Send a request to update metadata every second, to prevent as many 429 errors and memory exceptions
                        Message m = new Message();
                        Bundle mBundle = new Bundle();
                        mBundle.putSerializable("VIDEO", v.clone());
                        mBundle.putInt("WHEN", (int) (1000*fileCount+Math.round(Math.random()*100)));
                        m.setData(mBundle);
//                        h.sendEmptyMessageDelayed(1000 * fileCount, 1000 * fileCount);
                        h.sendMessageDelayed(m, 1000 * fileCount);
                        Log.d(TAG, "Queued " + mBundle.getInt("WHEN") + "  -  " + v.getName());
                        v.save(); //Need to save here, otherwise purging won't work as expected
                    }

//                    Log.d(TAG, v.toString());



//                    return;
                }
                //Ignore otherwise
            }
        }
        //Let's do VOB video
        if(video_folder) {
//            Log.d(TAG, "Done rooting through "+root.getPath());
            Log.d(TAG, "Created info for VOB " + v.toString());
            fileCount++;
            //Send a request to update metadata every second, to prevent as many 429 errors and memory exceptions
            Message m = new Message();
            Bundle mBundle = new Bundle();
            mBundle.putSerializable("VIDEO", v.clone());
            m.setData(mBundle);
//                        h.sendEmptyMessageDelayed(1000 * fileCount, 1000 * fileCount);
            h.sendMessageDelayed(m, 1000 * fileCount);
            Log.d(TAG, "Queued " + 1000 * fileCount + "  -  " + v.getName());
            v.save(); //Need to save here, otherwise purging won't work as expected
        }
    }

    /**
     * This takes a video object from the database and updates parameters. If this does not
     * have good data, don't touch it. This runs on its own thread by default
     * @param video SugarRecord video object, the one that we want to update
     * @param dtl Listener for when the method finishes, so we can call refresh on the UI thread
     */
    public static void updateSingleVideo(final Video video, final DownloadTaskListener dtl) {
        //TODO Do a check for video's parent and if it is a nameless VOB, use the parent instead
        final Video original = video.clone();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Run video update");
                    Config config = ApiClient.getInstance().createTMDbClient().getConfig();
                    if (TextUtils.isEmpty(video.getVideoUrl()) || video.getName().toLowerCase().contains(Constants.SAMPLE)) {
                        return;
                    }
                    Log.d(TAG, "Sending guess for "+video.getName());
                    Guess guess = GuessItClient.guess(video.getName());
                    Log.d(TAG, "Guess returns "+guess.toString());

                    if(guess == null) {
                        Log.d(TAG, "There's nothing here for me");
                        return;
                    }

                    // if a guess is not found, search again using the parent directory's name
                    if (guess != null &&
                            (TextUtils.isEmpty(guess.getTitle()) || guess.getTitle().equals(video.getName()))) {

                        String[] sections = video.getVideoUrl().split("/");
                        String name = sections[sections.length - 2];

                        int indexOf = video.getVideoUrl().lastIndexOf(".");
                        String ext = video.getVideoUrl().substring(indexOf, video.getVideoUrl().length());
                        //NO We're NOT going to be doing that.
//                        Log.d(TAG, "Guess not found, use parent directory name: "+name+ext);
//                        guess = GuessItClient.guess(name + ext);
//                    Log.d(TAG, guess.toString());
                    }

                    video.setCreated(video.getCreated());
                    Log.d(TAG, video.getName()+" => "+guess.toString());

                    //Don't update video and ragequit
                    if (guess == null || (TextUtils.isEmpty(guess.getTitle()) && TextUtils.isEmpty(guess.getSeries()))) {
                        Log.d(TAG, "There's nothing here for me");
                        return;
                    }
                    if(guess.getTitle().equals("0")) {
                    /*  This means the guess came back undefined
                        If I decided to pursue with the movie request, it would apparently
                        return with Tai Chi Zero as the title. This is not correct, so I cannot
                        pursue with this request.
                    */

                        Log.d(TAG, "There's nothing here for me");
                        return;
                    }



                    //Not rage quitting; plowing ahead
                    video.setName(WordUtils.capitalizeFully(guess.getTitle()));
//        video.setVideoUrl(video.getVideoUrl());
                    video.setIsMovie(guess.getType().contains("movie"));
                    if(!guess.getType().contains("movie")) {
                        //tv logic
                        if (!TextUtils.isEmpty(guess.getSeries())) {
                            try {
                                TvShow tvShow = null;
                                Long tmdbId = null;

                                // look for the TV show in the database first
                                List<TvShow> tvShows = TvShow.find(TvShow.class, "original_name = ?",
                                        guess.getSeries());

                                // if a TV show is found, clone it.
                                // if not, run a TMDb search for the TV show
                                if (tvShows != null && !tvShows.isEmpty()) {
                                    tvShow = TvShow.copy(tvShows.get(0));
                                    tmdbId = tvShow.getTmdbId();
                                } else {
                                    SearchResult result;
                                    result = ApiClient.getInstance().createTMDbClient()
                                            .findTvShow(guess.getSeries());
                                    if(result == null) {
                                        result = ApiClient.getInstance().createTVDBClient()
                                                .findTvShow(guess.getSeries());
                                    }
                                    if (result.getResults() != null && !result.getResults().isEmpty()) {
                                        tmdbId = result.getResults().get(0).getId();
                                        tvShow = ApiClient.getInstance().createTMDbClient().getTvShow(tmdbId);
                                        if(tvShow == null) {
                                            tvShow = ApiClient.getInstance().createTVDBClient().getTvShow(tmdbId);
                                        }
                                        tvShow.setTmdbId(tmdbId);
                                        tvShow.setId(null);
                                        tvShow.setFlattenedGenres(StringUtils.join(tvShow.getGenres(), ","));
                                    }
                                }

                                if (tmdbId != null) {
                                    // get the Episode information
                                    if (guess.getEpisodeNumber() != null && guess.getSeason() != null) {
                                        Episode episode;
                                        episode = ApiClient.getInstance().createTMDbClient()
                                                .getEpisode(tvShow.getTmdbId(),
                                                        guess.getSeason(), guess.getEpisodeNumber());
                                        if (episode == null) {
                                            episode = ApiClient.getInstance().createTVDBClient()
                                                    .getEpisode(tvShow.getId(),tvShow.getEpisode().getAirDate());
                                        }
                                        if (episode != null) {
                                            if (!TextUtils.isEmpty(episode.getStillPath())) {
                                                String stillPathUrl = config.getImages().getBase_url() + "original" +
                                                        episode.getStillPath();
                                                episode.setStillPath(stillPathUrl);
                                            }

                                            episode.setTmdbId(tmdbId);
                                            episode.setId(null);

                                            episode.save();
                                            tvShow.setEpisode(episode);
                                            video.setIsMatched(true);
                                        }
                                    }

                                    tvShow.save();

                                    video.setName(tvShow.getOriginalName());
                                    video.setOverview(tvShow.getOverview());
                                    video.setTvShow(tvShow);
                                    video.setIsMatched(true);

                                    String cardImageUrl = config.getImages().getBase_url() + "original" +
                                            tvShow.getPosterPath();
                                    video.setCardImageUrl(cardImageUrl);

                                    String bgImageUrl = config.getImages().getBase_url() + "original" +
                                            tvShow.getBackdropPath();
                                    video.setBackgroundImageUrl(bgImageUrl);
                                }
                            } catch (Exception e) {
                                //Too many requests, return in 30 seconds
                                if(e.getMessage().contains("429")) {
                                    //Too many requests, return in 15+x seconds
                                    synchronized (this) {
                                        try {
                                            long wait = 1000 * 15 + Math.round(15000*Math.random());
                                            Log.d(TAG, "Delay "+original.getName()+" by "+wait+"s");
                                            this.wait(wait);
                                            updateSingleVideo(original, dtl);
                                        } catch(Exception E2) {
                                            e.printStackTrace();
                                        }
                                    }
                                } else {
                                    Log.e(TAG, e.getMessage()+"");
                                    e.printStackTrace();
                                }
//                                return;
                            }
                        }

                        video.save();
                    } else {
                        //movie logic
                        if (!TextUtils.isEmpty(guess.getTitle())) {
                            try {
                                // search for the movie
                                SearchResult result = ApiClient
                                        .getInstance().createTMDbClient().findMovie(guess.getTitle(),
                                                guess.getYear());

                                // if found, get the detailed info for the movie
                                if (result.getResults() != null && !result.getResults().isEmpty()) {
                                    Long id = result.getResults().get(0).getId();

                                    if (id != null) {
                                        Movie movie;
                                        movie = ApiClient.getInstance().createTMDbClient().getMovie(id);
                                        if(movie == null) {
                                            movie = ApiClient.getInstance().createTVDBClient().getMovie(id);
                                        }
                                        movie.setTmdbId(id);
                                        movie.setId(null);
                                        movie.setFlattenedGenres(StringUtils.join(movie.getGenres(), ","));
                                        movie.setFlattenedProductionCompanies(
                                                StringUtils.join(movie.getProductionCompanies(), ","));
                                        movie.save();

                                        video.setOverview(movie.getOverview());
                                        video.setName(movie.getTitle());
                                        video.setIsMatched(true);
                                        video.setMovie(movie);
                                    }

                                    String cardImageUrl = config.getImages().getBase_url() + "original" +
                                            result.getResults().get(0).getPoster_path();
                                    video.setCardImageUrl(cardImageUrl);

                                    String bgImageUrl = config.getImages().getBase_url() + "original" +
                                            result.getResults().get(0).getBackdrop_path();
                                    video.setBackgroundImageUrl(bgImageUrl);
                                }
                            } catch (Exception e) {

                                if(e.getMessage().contains("429")) {
                                    //Too many requests, return in 15+x seconds
                                    synchronized (this) {
                                        try {
                                            long wait = 1000 * 15 + Math.round(15000*Math.random());
                                            Log.d(TAG, "Delay "+original.getName()+" by "+wait+"s");
                                            this.wait(wait);
                                            updateSingleVideo(original, dtl);
                                        } catch(Exception E2) {
                                            e.printStackTrace();
                                        }
                                    }
                                } else {
                                    Log.e(TAG, e.getMessage()+"");
                                    e.printStackTrace();
                                }
//                                return;
                            }
                        }

                        video.save();
                    }
                    Log.d("amp:DownloadTaskHelper", video.toString());
                    Handler h = new Handler(Looper.getMainLooper()) {
                        @Override
                        public void handleMessage(Message msg) {
                            super.handleMessage(msg);
                            Log.d(TAG, "Updated video info: "+video.toString());
                            if(dtl != null)
                                dtl.onDownloadFinished();
                        }
                    };
                    h.sendEmptyMessage(0);
                } catch(Exception e) {
                    /*
                    8510-9779/com.jerrellmardis.amphitheatre.dev E/AndroidRuntime? FATAL EXCEPTION: Thread-3591
                    Process: com.jerrellmardis.amphitheatre.dev, PID: 8510
                    retrofit.RetrofitError: 429
                     */
//                    Log.e(TAG, e.getMessage());
                    if(e.getMessage().contains("429")) {
                        //Too many requests, return in 30 seconds
                        synchronized (this) {
                            try {
                                long wait = 1000 * 15 + Math.round(15000*Math.random());
                                Log.d(TAG, "Delay "+original.getName()+" by "+wait+"s");
                                this.wait(wait);
                                updateSingleVideo(original, dtl);
                            } catch(Exception E2) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        Log.e(TAG, "Ran into error "+e.getMessage());
                    }
//                    return;
                }
            }
        }).start();
    }

    /**
     * This serves as a more generic method to pull metadata for any file, movie or tv show
     * @param config Same config variable used previously
     * @param file SuperFile which is used to simplify disparities in file class types
     * @return A video file which is then inserted into the database
     */
    public static Video downloadFileData(Config config, SuperFile file) {
        if (TextUtils.isEmpty(file.getPath()) || file.getName().toLowerCase().contains(Constants.SAMPLE)) {
            return null;
        }

        Guess guess = GuessItClient.guess(file.getName());

        // if a guess is not found, search again using the parent directory's name
        if (guess != null &&
                (TextUtils.isEmpty(guess.getTitle()) || guess.getTitle().equals(file.getName()))) {

            String[] sections = file.getPath().split("/");
            String name = sections[sections.length - 2];

            int indexOf = file.getPath().lastIndexOf(".");
            String ext = file.getPath().substring(indexOf, file.getPath().length());
            guess = GuessItClient.guess(name + ext);
            Log.d("amp:DownloadTask", guess.toString());
        }

        Video video = new Video();

        video.setCreated(file.createTime());

        //Set as undefined video and ragequit
        if (guess == null || TextUtils.isEmpty(guess.getTitle())) {
            video.setName(WordUtils.capitalizeFully(file.getName()));
            video.setVideoUrl(file.getPath());
            video.setIsMatched(false);
            video.setIsMovie(true);
            video.save();
            return video;
        }

        Log.d("amp:DownloadTask", guess.toString());

        //Not rage quitting; plowing ahead
        video.setName(WordUtils.capitalizeFully(guess.getTitle()));
        video.setVideoUrl(file.getPath());
        video.setIsMovie(guess.getType().contains("movie"));
        if(!guess.getType().contains("movie")) {
            //tv logic
            if (!TextUtils.isEmpty(guess.getSeries())) {
                try {
                    TvShow tvShow = null;
                    Long tmdbId = null;

                    // look for the TV show in the database first
                    List<TvShow> tvShows = TvShow.find(TvShow.class, "original_name = ?",
                            guess.getSeries());

                    // if a TV show is found, clone it.
                    // if not, run a TMDb search for the TV show
                    if (tvShows != null && !tvShows.isEmpty()) {
                        tvShow = TvShow.copy(tvShows.get(0));
                        tmdbId = tvShow.getTmdbId();
                    } else {
                        SearchResult result;
                        result = ApiClient.getInstance().createTMDbClient()
                                .findTvShow(guess.getSeries());
                        if(result == null) {
                            result = ApiClient.getInstance().createTVDBClient()
                                    .findTvShow(guess.getSeries());
                        }
                        if (result.getResults() != null && !result.getResults().isEmpty()) {
                            tmdbId = result.getResults().get(0).getId();
                            tvShow = ApiClient.getInstance().createTMDbClient().getTvShow(tmdbId);
                            if(tvShow == null) {
                                tvShow = ApiClient.getInstance().createTVDBClient().getTvShow(tmdbId);
                            }
                            tvShow.setTmdbId(tmdbId);
                            tvShow.setId(null);
                            tvShow.setFlattenedGenres(StringUtils.join(tvShow.getGenres(), ","));
                        }
                    }

                    if (tmdbId != null) {
                        // get the Episode information
                        if (guess.getEpisodeNumber() != null && guess.getSeason() != null) {
                            Episode episode;
                            episode = ApiClient.getInstance().createTMDbClient()
                                    .getEpisode(tvShow.getTmdbId(),
                                            guess.getSeason(), guess.getEpisodeNumber());
                            if (episode == null) {
                                episode = ApiClient.getInstance().createTVDBClient()
                                        .getEpisode(tvShow.getId(),tvShow.getEpisode().getAirDate());
                            }
                            if (episode != null) {
                                if (!TextUtils.isEmpty(episode.getStillPath())) {
                                    String stillPathUrl = config.getImages().getBase_url() + "original" +
                                            episode.getStillPath();
                                    episode.setStillPath(stillPathUrl);
                                }

                                episode.setTmdbId(tmdbId);
                                episode.setId(null);

                                episode.save();
                                tvShow.setEpisode(episode);
                                video.setIsMatched(true);
                            }
                        }

                        tvShow.save();

                        video.setName(tvShow.getOriginalName());
                        video.setOverview(tvShow.getOverview());
                        video.setTvShow(tvShow);

                        String cardImageUrl = config.getImages().getBase_url() + "original" +
                                tvShow.getPosterPath();
                        video.setCardImageUrl(cardImageUrl);

                        String bgImageUrl = config.getImages().getBase_url() + "original" +
                                tvShow.getBackdropPath();
                        video.setBackgroundImageUrl(bgImageUrl);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            video.save();

            return video;
        } else {
            //movie logic
            if (!TextUtils.isEmpty(guess.getTitle())) {
                try {
                    // search for the movie
                    SearchResult result = ApiClient
                            .getInstance().createTMDbClient().findMovie(guess.getTitle(),
                                    guess.getYear());

                    // if found, get the detailed info for the movie
                    if (result.getResults() != null && !result.getResults().isEmpty()) {
                        Long id = result.getResults().get(0).getId();

                        if (id != null) {
                            Movie movie;
                            movie = ApiClient.getInstance().createTMDbClient().getMovie(id);
                            if(movie == null) {
                                movie = ApiClient.getInstance().createTVDBClient().getMovie(id);
                            }
                            movie.setTmdbId(id);
                            movie.setId(null);
                            movie.setFlattenedGenres(StringUtils.join(movie.getGenres(), ","));
                            movie.setFlattenedProductionCompanies(
                                    StringUtils.join(movie.getProductionCompanies(), ","));
                            movie.save();

                            video.setOverview(movie.getOverview());
                            video.setName(movie.getTitle());
                            video.setIsMatched(true);
                            video.setMovie(movie);
                        }

                        String cardImageUrl = config.getImages().getBase_url() + "original" +
                                result.getResults().get(0).getPoster_path();
                        video.setCardImageUrl(cardImageUrl);

                        String bgImageUrl = config.getImages().getBase_url() + "original" +
                                result.getResults().get(0).getBackdrop_path();
                        video.setBackgroundImageUrl(bgImageUrl);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            video.save();

            return video;
        }
    }

    public static Video downloadMovieData(Config config, SmbFile file) {
        if (TextUtils.isEmpty(file.getPath()) || file.getName().toLowerCase().contains(Constants.SAMPLE)) {
            return null;
        }

        Guess guess = GuessItClient.guess(file.getName());

        // if a guess is not found, search again using the parent directory's name
        if (guess != null &&
                (TextUtils.isEmpty(guess.getTitle()) || guess.getTitle().equals(file.getName()))) {

            String[] sections = file.getPath().split("/");
            String name = sections[sections.length - 2];

            int indexOf = file.getPath().lastIndexOf(".");
            String ext = file.getPath().substring(indexOf, file.getPath().length());
            guess = GuessItClient.guess(name + ext);
            Log.d("amp:DownloadTask", guess.toString());
        }

        Video video = new Video();

        try {
            video.setCreated(file.createTime());
        } catch (SmbException e) {
            // do nothing
        }

        if (guess == null || TextUtils.isEmpty(guess.getTitle())) {
            video.setName(WordUtils.capitalizeFully(file.getName()));
            video.setVideoUrl(file.getPath());
            video.setIsMatched(false);
            video.setIsMovie(true);
            video.save();
            return video;
        }

        Log.d("amp:DownloadTask", guess.toString());

        video.setName(WordUtils.capitalizeFully(guess.getTitle()));
        video.setVideoUrl(file.getPath());
        video.setIsMovie(true);
        //Movie true, but need to abstract even more
        Log.d("amp:DownloadTask", "Is really movie? "+guess.getType());

        if (!TextUtils.isEmpty(guess.getTitle())) {
            try {
                // search for the movie
                SearchResult result = ApiClient
                        .getInstance().createTMDbClient().findMovie(guess.getTitle(),
                                guess.getYear());

                // if found, get the detailed info for the movie
                if (result.getResults() != null && !result.getResults().isEmpty()) {
                    Long id = result.getResults().get(0).getId();

                    if (id != null) {
                        Movie movie;
                        movie = ApiClient.getInstance().createTMDbClient().getMovie(id);
                        if(movie == null) {
                            movie = ApiClient.getInstance().createTVDBClient().getMovie(id);
                        }
                        movie.setTmdbId(id);
                        movie.setId(null);
                        movie.setFlattenedGenres(StringUtils.join(movie.getGenres(), ","));
                        movie.setFlattenedProductionCompanies(
                                StringUtils.join(movie.getProductionCompanies(), ","));
                        movie.save();

                        video.setOverview(movie.getOverview());
                        video.setName(movie.getTitle());
                        video.setIsMatched(true);
                        video.setMovie(movie);
                    }

                    String cardImageUrl = config.getImages().getBase_url() + "original" +
                            result.getResults().get(0).getPoster_path();
                    video.setCardImageUrl(cardImageUrl);

                    String bgImageUrl = config.getImages().getBase_url() + "original" +
                            result.getResults().get(0).getBackdrop_path();
                    video.setBackgroundImageUrl(bgImageUrl);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        video.save();

        return video;
    }

    public static Video downloadTvShowData(Config config, SmbFile file) {
        if (TextUtils.isEmpty(file.getPath()) || file.getName().toLowerCase().contains(Constants.SAMPLE)) {
            return null;
        }

        Guess guess = GuessItClient.guess(file.getName());

        // if a guess is not found, search again using the parent directory's name
        if (guess != null &&
                (TextUtils.isEmpty(guess.getSeries()) || guess.getSeries().equals(file.getName()))) {

            String[] sections = file.getPath().split("/");
            String name = sections[sections.length - 2];

            int indexOf = file.getPath().lastIndexOf(".");
            String ext = file.getPath().substring(indexOf, file.getPath().length());
            guess = GuessItClient.guess(name + ext);
        }

        Video video = new Video();

        // couldn't find a match. Create a TV Show, mark it as unmatched and move on.
        if (guess == null || TextUtils.isEmpty(guess.getSeries())) {
            video.setName(WordUtils.capitalizeFully(file.getName()));
            video.setVideoUrl(file.getPath());
            video.setIsMatched(false);
            video.setIsMovie(false);
            video.save();
            return video;
        }

        video.setName(WordUtils.capitalizeFully(guess.getSeries()));
        video.setVideoUrl(file.getPath());
        video.setIsMovie(false);

        if (!TextUtils.isEmpty(guess.getSeries())) {
            try {
                TvShow tvShow = null;
                Long tmdbId = null;

                // look for the TV show in the database first
                List<TvShow> tvShows = TvShow.find(TvShow.class, "original_name = ?",
                        guess.getSeries());

                // if a TV show is found, clone it.
                // if not, run a TMDb search for the TV show
                if (tvShows != null && !tvShows.isEmpty()) {
                    tvShow = TvShow.copy(tvShows.get(0));
                    tmdbId = tvShow.getTmdbId();
                } else {
                    SearchResult result;
                    result = ApiClient.getInstance().createTMDbClient()
                            .findTvShow(guess.getSeries());
                    if(result == null) {
                        result = ApiClient.getInstance().createTVDBClient()
                                .findTvShow(guess.getSeries());
                    }
                    if (result.getResults() != null && !result.getResults().isEmpty()) {
                        tmdbId = result.getResults().get(0).getId();
                        tvShow = ApiClient.getInstance().createTMDbClient().getTvShow(tmdbId);
                        if(tvShow == null) {
                            tvShow = ApiClient.getInstance().createTVDBClient().getTvShow(tmdbId);
                        }
                        tvShow.setTmdbId(tmdbId);
                        tvShow.setId(null);
                        tvShow.setFlattenedGenres(StringUtils.join(tvShow.getGenres(), ","));
                    }
                }

                if (tmdbId != null) {
                    // get the Episode information
                    if (guess.getEpisodeNumber() != null && guess.getSeason() != null) {
                        Episode episode;
                        episode = ApiClient.getInstance().createTMDbClient()
                                .getEpisode(tvShow.getTmdbId(),
                                        guess.getSeason(), guess.getEpisodeNumber());
                        if (episode == null) {
                            episode = ApiClient.getInstance().createTVDBClient()
                                    .getEpisode(tvShow.getId(),tvShow.getEpisode().getAirDate());
                        }
                        if (episode != null) {
                            if (!TextUtils.isEmpty(episode.getStillPath())) {
                                String stillPathUrl = config.getImages().getBase_url() + "original" +
                                        episode.getStillPath();
                                episode.setStillPath(stillPathUrl);
                            }

                            episode.setTmdbId(tmdbId);
                            episode.setId(null);

                            episode.save();
                            tvShow.setEpisode(episode);
                            video.setIsMatched(true);
                        }
                    }

                    tvShow.save();

                    video.setName(tvShow.getOriginalName());
                    video.setOverview(tvShow.getOverview());
                    video.setTvShow(tvShow);

                    String cardImageUrl = config.getImages().getBase_url() + "original" +
                            tvShow.getPosterPath();
                    video.setCardImageUrl(cardImageUrl);

                    String bgImageUrl = config.getImages().getBase_url() + "original" +
                            tvShow.getBackdropPath();
                    video.setBackgroundImageUrl(bgImageUrl);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        video.save();

        return video;
    }

    public interface DownloadTaskListener {
        public void onDownloadFinished();
    }
}
