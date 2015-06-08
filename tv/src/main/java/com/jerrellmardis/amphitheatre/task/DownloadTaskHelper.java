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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import java.util.Collections;
import java.util.List;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * Created by Jerrell Mardis on 8/15/14.
 */
public final class DownloadTaskHelper {
    private static final String TAG = "amp:DownloadTaskHlpr";

    public static List<SmbFile> getFiles(String user, String password, String path) {
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication("", user, password);

        List<SmbFile> files = Collections.emptyList();
        try {
            files = VideoUtils.getFilesFromDir(path, auth);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return files;
    }

    /**
     * This takes a video object from the database and updates parameters. If this does not
     * have good data, don't touch it. This runs on its own thread by default
     * @param video SugarRecord video object, the one that we want to update
     * @param dtl Listener for when the method finishes, so we can call refresh on the UI thread
     */
    public static void updateSingleVideo(final Video video, final DownloadTaskListener dtl) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Config config = ApiClient.getInstance().createTMDbClient().getConfig();
                if (TextUtils.isEmpty(video.getVideoUrl()) || video.getName().toLowerCase().contains(Constants.SAMPLE)) {
                    return;
                }

                Guess guess = GuessItClient.guess(video.getName());

                // if a guess is not found, search again using the parent directory's name
                if (guess != null &&
                        (TextUtils.isEmpty(guess.getTitle()) || guess.getTitle().equals(video.getName()))) {

                    String[] sections = video.getVideoUrl().split("/");
                    String name = sections[sections.length - 2];

                    int indexOf = video.getVideoUrl().lastIndexOf(".");
                    String ext = video.getVideoUrl().substring(indexOf, video.getVideoUrl().length());
                    guess = GuessItClient.guess(name + ext);
//                    Log.d(TAG, guess.toString());
                }

                video.setCreated(video.getCreated());
                Log.d(TAG, video.getName()+" => "+guess.toString());

                //Don't update video and ragequit
                if (guess == null || TextUtils.isEmpty(guess.getTitle())) {
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
                }
                Log.d("amp:DownloadTaskHelper", video.toString());
                Handler h = new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        super.handleMessage(msg);
                        dtl.onDownloadFinished();
                    }
                };
                h.sendEmptyMessage(0);
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
