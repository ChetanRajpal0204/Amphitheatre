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

package com.jerrellmardis.amphitheatre.fragment;

import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.DetailsFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;

import com.jerrellmardis.amphitheatre.R;
import com.jerrellmardis.amphitheatre.activity.DetailsActivity;
import com.jerrellmardis.amphitheatre.listeners.RowBuilderTaskListener;
import com.jerrellmardis.amphitheatre.model.Source;
import com.jerrellmardis.amphitheatre.model.Video;
import com.jerrellmardis.amphitheatre.model.VideoDatabase;
import com.jerrellmardis.amphitheatre.model.VideoGroup;
import com.jerrellmardis.amphitheatre.provider.VideosProvider;
import com.jerrellmardis.amphitheatre.task.DetailRowBuilderTask;
import com.jerrellmardis.amphitheatre.util.BlurTransform;
import com.jerrellmardis.amphitheatre.util.Constants;
import com.jerrellmardis.amphitheatre.util.PicassoBackgroundManagerTarget;
import com.orm.query.Condition;
import com.orm.query.Select;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;
import com.squareup.picasso.Transformation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class VideoDetailsFragment extends DetailsFragment implements RowBuilderTaskListener {

    private Transformation mBlurTransformation;
    private Target mBackgroundTarget;
    private DisplayMetrics mMetrics;
    private String TAG = "amp:VideoDetailsFrag";
    private Video mVideo;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBlurTransformation = new BlurTransform(getActivity());

        BackgroundManager backgroundManager = BackgroundManager.getInstance(getActivity());
        backgroundManager.attach(getActivity().getWindow());
        mBackgroundTarget = new PicassoBackgroundManagerTarget(backgroundManager);

        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

        boolean isVideo = getActivity().getIntent().getBooleanExtra(Constants.IS_VIDEO, true);
        Log.d(TAG, "VideoDetailsFragment");
        try {
            if(searchGlobalSearchIntent()) {
                isVideo = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        /*try {
            Log.d(TAG, searchGlobalSearchIntent()+"");
        } catch (Exception e) {
            e.printStackTrace();
        }*/
        if (isVideo) {
            if(getActivity().getIntent().getSerializableExtra(Constants.VIDEO) != null)
                mVideo = (Video) getActivity().getIntent().getSerializableExtra(Constants.VIDEO);

            Map<String, List<Video>> relatedVideos = Collections.emptyMap();

            if (mVideo.isMovie()) {
                relatedVideos = getRelatedMovies(mVideo);
                updateBackground(mVideo.getBackgroundImageUrl());
                Log.d(TAG, mVideo.getBackgroundImageUrl());
            } else if (mVideo.getTvShow() != null && mVideo.getTvShow().getEpisode() != null) {
                relatedVideos = getRelatedTvShows(mVideo);
                updateBackground(mVideo.getTvShow().getEpisode().getStillPath());
                Log.d(TAG, mVideo.getTvShow().getEpisode().getStillPath());
            }

            new DetailRowBuilderTask(getActivity(), relatedVideos, true, this).execute(mVideo);
        } else {
            VideoGroup videoGroup = (VideoGroup) getActivity().getIntent().getSerializableExtra(Constants.VIDEO_GROUP);
            mVideo = videoGroup.getVideo();
            updateBackground(mVideo.getBackgroundImageUrl());
            new DetailRowBuilderTask(getActivity(), getRelatedTvShows(mVideo), false, this).execute(mVideo);
        }

        setOnItemViewClickedListener(getDefaultItemClickedListener());
    }

    /*
     * Check if there is a global search intent
     */
    private boolean searchGlobalSearchIntent() throws Exception {
        Intent intent = getActivity().getIntent();
        String intentAction = intent.getAction();
        String globalSearch = getString(R.string.global_search);
        if (globalSearch.equalsIgnoreCase(intentAction)) {
            Uri intentData = intent.getData();
            VideoDatabase vdb = new VideoDatabase(getActivity());
            try {
                Log.d(TAG, Arrays.asList(getActivity().getIntent().getExtras().keySet().toArray()).toString());
                Log.d(TAG, getActivity().getIntent().getStringExtra("intent_extra_data_key"));
            } catch(Exception e) {

            }
            Log.d(TAG, "action: " + intentAction + " intentData:" + intentData);
//            int selectedIndex = Integer.parseInt(intentData.getLastPathSegment());
//            String selectedIndex = intentData.getLastPathSegment();
            List<Video> movies = Source.listAll(Video.class);
            int movieIndex = 0;
            if (movies == null) {
                return false;
            }
            for (Video movie : movies) {
//                Log.d(TAG, selectedIndex+" => "+movieIndex+" "+movie.getName()+" "+movie.getId());
//                Log.d(TAG, intentData.toString()+" => "+movie.getName()+" "+movie.getVideoUrl());
                movieIndex++;
//                if (selectedIndex.equals(movie.getVideoUrl())) {
                if (intentData.toString().contains(movie.getVideoUrl())) {
                    mVideo = movie;
                    Log.d(TAG, movie.toString());
                    return true;
                }
            }
        }
        return false;
    }

    protected OnItemViewClickedListener getDefaultItemClickedListener() {
        return new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder viewHolder, Object item, RowPresenter.ViewHolder viewHolder1, Row row) {
                if (item instanceof Video) {
                    Intent intent = new Intent(getActivity(), DetailsActivity.class);
                    intent.putExtra(Constants.IS_VIDEO, true);
                    intent.putExtra(Constants.VIDEO, (Video) item);
                    startActivity(intent);
                }
            }
        };
    }

    private Map<String, List<Video>> getRelatedMovies(Video video) {
        List<Video> videos = Select
                .from(Video.class)
                .where(Condition.prop("is_matched").eq(1),
                        Condition.prop("is_movie").eq(1))
                .list();

        Map<String, List<Video>> relatedVideos = new HashMap<String, List<Video>>();
        String key = getString(R.string.related_videos);
        relatedVideos.put(key, new ArrayList<Video>());

        if (video.getMovie() != null && !TextUtils.isEmpty(video.getMovie().getFlattenedGenres())) {
            String[] genresArray = video.getMovie().getFlattenedGenres().split(",");
            Set<String> genres = new HashSet<String>(Arrays.asList(genresArray));

            for (Video vid : videos) {
                if (vid.getMovie() != null &&
                        !TextUtils.isEmpty(vid.getMovie().getFlattenedGenres())) {

                    Set<String> intersection = new HashSet<String>(Arrays.asList(
                            vid.getMovie().getFlattenedGenres().split(",")));
                    intersection.retainAll(genres);

                    if (intersection.size() == genresArray.length &&
                            !video.getMovie().getTitle().equals(vid.getMovie().getTitle())) {

                        relatedVideos.get(key).add(vid);
                    }
                }
            }
        }

        return relatedVideos;
    }

    private Map<String, List<Video>> getRelatedTvShows(Video video) {
        List<Video> videos = Select
                .from(Video.class)
                .where(Condition.prop("name").eq(video.getName()),
                        Condition.prop("is_movie").eq(0))
                .list();

        Map<String, List<Video>> relatedVideos = new TreeMap<String, List<Video>>(Collections.reverseOrder());

        for (Video vid : videos) {
            // if an Episode item exists then categorize it
            // otherwise, add it to the uncategorized list
            if (vid.getTvShow() != null && vid.getTvShow().getEpisode() != null) {
                int seasonNumber = vid.getTvShow().getEpisode().getSeasonNumber();
                String key = String.format(getString(R.string.season_number), seasonNumber);

                if (relatedVideos.containsKey(key)) {
                    List<Video> subVideos = relatedVideos.get(key);
                    subVideos.add(vid);
                } else {
                    List<Video> list = new ArrayList<Video>();
                    list.add(vid);
                    relatedVideos.put(key, list);
                }
            } else {
                String key = getString(R.string.uncategorized);

                if (relatedVideos.containsKey(key)) {
                    relatedVideos.get(key).add(vid);
                } else {
                    List<Video> list = new ArrayList<Video>();
                    list.add(vid);
                    relatedVideos.put(key, list);
                }
            }
        }

        return relatedVideos;
    }

    private void updateBackground(String url) {
        Picasso.with(getActivity())
                .load(url)
                .transform(mBlurTransformation)
                .placeholder(R.drawable.placeholder)
                .resize(mMetrics.widthPixels, mMetrics.heightPixels)
                .centerCrop()
                .skipMemoryCache()
                .into(mBackgroundTarget);
    }

    @Override
    public void taskCompleted(ArrayObjectAdapter adapter) {
        setAdapter(adapter);
    }
}