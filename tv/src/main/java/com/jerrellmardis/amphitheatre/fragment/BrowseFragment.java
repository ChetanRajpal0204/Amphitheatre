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

import android.app.Activity;
import android.app.FragmentManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.ObjectAdapter;
import android.support.v17.leanback.widget.OnItemClickedListener;
import android.support.v17.leanback.widget.OnItemSelectedListener;
import android.support.v17.leanback.widget.Row;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.jerrellmardis.amphitheatre.R;
import com.jerrellmardis.amphitheatre.activity.DetailsActivity;
import com.jerrellmardis.amphitheatre.activity.GridViewActivity;
import com.jerrellmardis.amphitheatre.activity.SearchActivity;
import com.jerrellmardis.amphitheatre.model.GridGenre;
import com.jerrellmardis.amphitheatre.model.Source;
import com.jerrellmardis.amphitheatre.model.SuperFile;
import com.jerrellmardis.amphitheatre.model.Video;
import com.jerrellmardis.amphitheatre.model.VideoGroup;
import com.jerrellmardis.amphitheatre.service.LibraryUpdateService;
import com.jerrellmardis.amphitheatre.service.RecommendationsService;
import com.jerrellmardis.amphitheatre.task.DownloadTaskHelper;
import com.jerrellmardis.amphitheatre.task.GetFilesTask;
import com.jerrellmardis.amphitheatre.util.BlurTransform;
import com.jerrellmardis.amphitheatre.util.Constants;
import com.jerrellmardis.amphitheatre.util.Enums;
import com.jerrellmardis.amphitheatre.util.PicassoBackgroundManagerTarget;
import com.jerrellmardis.amphitheatre.util.SecurePreferences;
import com.jerrellmardis.amphitheatre.util.VideoUtils;
import com.jerrellmardis.amphitheatre.widget.CardPresenter;
import com.jerrellmardis.amphitheatre.widget.GridItemPresenter;
import com.jerrellmardis.amphitheatre.widget.SortedObjectAdapter;
import com.jerrellmardis.amphitheatre.widget.TvShowsCardPresenter;
import com.orm.query.Condition;
import com.orm.query.Select;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Target;
import com.squareup.picasso.Transformation;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;

import static android.view.View.OnClickListener;

public class BrowseFragment extends android.support.v17.leanback.app.BrowseFragment
        implements AddSourceDialogFragment.OnClickListener, CustomizeDialogFragment.OnSaveListener {

    private final Handler mHandler = new Handler();

    private Transformation mBlurTransformation;
    private Drawable mDefaultBackground;
    private Target mBackgroundTarget;
    private DisplayMetrics mMetrics;
    private Timer mBackgroundTimer;
    private String mBackgroundImageUrl;
    private ArrayObjectAdapter mAdapter;
    private CardPresenter mCardPresenter;
    private TvShowsCardPresenter mTvShowsCardPresenter;
    private static String TAG ="amp:BrowseFragment";

    private BroadcastReceiver videoUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Serializable obj = bundle.getSerializable(Constants.VIDEO);
                if (obj instanceof Video) {
                    addVideoToUi((Video) bundle.getSerializable(Constants.VIDEO));
                }
            }
        }
    };

    private BroadcastReceiver libraryUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh();
            Toast.makeText(getActivity(), getString(R.string.update_complete),
                    Toast.LENGTH_SHORT).show();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        mCardPresenter = new CardPresenter(getActivity());
        mTvShowsCardPresenter = new TvShowsCardPresenter(getActivity());
        mAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        addSettingsHeader();
        setAdapter(mAdapter);
        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mBlurTransformation = new BlurTransform(getActivity());
        prepareBackgroundManager();
        setupUIElements();
        setupEventListeners();
        refreshLocalLibrary();


        Log.d(TAG, "Activity created, force user to load "+Video.count(Video.class, null, null)+" videos");
        if (Video.count(Video.class, null, null) == 0 && false) {
        //Hardcoded to false b/c local is always an available source
            Log.d(TAG, "Show add source dialog");
            showAddSourceDialog();
        } else {
            Log.d(TAG, "Load videos");
            loadVideos();
        }

    }

    @Override
    public void onStart() {
        super.onStart();

        getActivity().registerReceiver(videoUpdateReceiver,
                new IntentFilter(Constants.VIDEO_UPDATE_ACTION));
        getActivity().registerReceiver(libraryUpdateReceiver,
                new IntentFilter(Constants.LIBRARY_UPDATED_ACTION));

        // TODO video(s) may have been watched before returning back here, so we need to refresh the view.
        // This could be important if we want to display "watched" indicators on the cards.

        // Refresh local library
//        refreshLocalLibrary();
    }

    @Override
    public void onStop() {
        try {
            getActivity().unregisterReceiver(videoUpdateReceiver);
        } catch (IllegalArgumentException e) {
            // do nothing
        }
        try {
            getActivity().unregisterReceiver(libraryUpdateReceiver);
        } catch (IllegalArgumentException e) {
            // do nothing
        }
        super.onStop();
    }

    @Override
    public void onAddClicked(CharSequence user, CharSequence password,
                             final CharSequence path, boolean isMovie) {

        Toast.makeText(getActivity(), getString(R.string.updating_library),
                Toast.LENGTH_SHORT).show();

        Source source = new Source();
        source.setSource(path.toString());
        source.setType(isMovie ? Source.Type.MOVIE.name() : Source.Type.TV_SHOW.name());
        source.save();

        new GetFilesTask(getActivity(), user.toString(), password.toString(), path.toString(),
                isMovie, new GetFilesTask.Callback() {

                    @Override
                    public void success() {
                        if (getActivity() == null) return;

                        Toast.makeText(getActivity(), getString(R.string.update_complete),
                                Toast.LENGTH_SHORT).show();

                        rebuildSubCategories();

                        reloadAdapters();

                        updateRecommendations();
                    }

                    @Override
                    public void failure() {
                        if (getActivity() == null) return;

                        Toast.makeText(getActivity(), getString(R.string.update_failed),
                                Toast.LENGTH_LONG).show();
                    }
                }).execute();

        SecurePreferences securePreferences = new SecurePreferences(getActivity().getApplicationContext());
        securePreferences.edit().putString(Constants.PREFS_USER_KEY, user.toString()).apply();
        securePreferences.edit().putString(Constants.PREFS_PASSWORD_KEY, password.toString()).apply();
    }

    private void reloadAdapters() {
        for (int i = 0; i < mAdapter.size(); i++) {
            ListRow listRow = (ListRow) mAdapter.get(i);
            ObjectAdapter objectAdapter = listRow.getAdapter();
            if (objectAdapter instanceof ArrayObjectAdapter) {
                ArrayObjectAdapter arrayObjectAdapter = ((ArrayObjectAdapter) objectAdapter);
                arrayObjectAdapter.notifyArrayItemRangeChanged(0, arrayObjectAdapter.size());
            }
        }
    }

    private void loadVideos() {
        List<Video> videos = Source.listAll(Video.class);
//        Clear current list and repopulate items (non-dupes)
//        mAdapter.clear();
        if (videos != null && !videos.isEmpty()) {
            for (Video video : videos) {
                Log.d(TAG, "Loading "+video.getName()+" from "+video.getVideoUrl());
                addVideoToUi(video);
            }

            rebuildSubCategories();

            reloadAdapters();
        } else {
            Log.d(TAG, "Videos are null or empty");
        }
    }

    private void prepareBackgroundManager() {
        BackgroundManager backgroundManager = BackgroundManager.getInstance(getActivity());
        backgroundManager.attach(getActivity().getWindow());
        mBackgroundTarget = new PicassoBackgroundManagerTarget(backgroundManager);

        mDefaultBackground = getResources().getDrawable(R.drawable.amphitheatre);

        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

        Picasso.with(getActivity())
                .load(R.drawable.amphitheatre)
                .resize(mMetrics.widthPixels, mMetrics.heightPixels)
                .centerCrop()
                .skipMemoryCache()
                .into(mBackgroundTarget);
    }

    private void setupUIElements() {
        setTitle(getString(R.string.browse_title));

        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        setBrandColor(getResources().getColor(R.color.fastlane_background));
        setSearchAffordanceColor(getResources().getColor(R.color.search_button_background));
    }

    private void setupEventListeners() {
        setOnItemSelectedListener(getDefaultItemSelectedListener());
        setOnItemClickedListener(getDefaultItemClickedListener());
        setOnSearchClickedListener(getSearchClickedListener());
        setBrowseTransitionListener(getBrowseTransitionListener());
    }

    private void resetBackground() {
        // Make sure default background is loaded
        if (mBackgroundImageUrl != null) {
            mBackgroundImageUrl = null;
        }
        startBackgroundTimer();
    }

    private void addVideoToUi(Video video) {
        Comparator<Video> videoNameComparator = new Comparator<Video>() {
            @Override
            public int compare(Video o1, Video o2) {
                if (o2.getName() == null) {
                    return (o1.getName() == null) ? 0 : -1;
                }
                if (o1.getName() == null) {
                    return 1;
                }
                return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
            }
        };

        Comparator<VideoGroup> videoGroupNameComparator = new Comparator<VideoGroup>() {
            @Override
            public int compare(VideoGroup o1, VideoGroup o2) {
                if (o2.getVideo().getName() == null) {
                    return (o1.getVideo().getName() == null) ? 0 : -1;
                }
                if (o1.getVideo().getName() == null) {
                    return 1;
                }
                return o1.getVideo().getName().toLowerCase().compareTo(o2.getVideo().getName().toLowerCase());
            }
        };

        if (!video.isMatched()) {
            ListRow row = findListRow(getString(R.string.unmatched));

            // if found add this video
            // if not, create a new row and add it
            if (row != null) {
                ((SortedObjectAdapter) row.getAdapter()).add(video);
                Log.d(TAG, "Null row, adding video at index 0 to unmatched");
            } else {
                SortedObjectAdapter listRowAdapter = new SortedObjectAdapter(
                        videoNameComparator, mCardPresenter);
                listRowAdapter.add(video);

                HeaderItem header = new HeaderItem(0, getString(R.string.unmatched), null);
                int index = mAdapter.size() > 1 ? mAdapter.size() - 1 : 0;
                Log.d(TAG, "Adding "+video.getVideoUrl()+" at index "+index+" to Unmatched");
                mAdapter.add(index, new ListRow(header, listRowAdapter));
            }
        } else if (video.isMovie()) {
            List<Source> sources = Source.listAll(Source.class);

            for (Source source : sources) {
                // find the video's "source" and use it as a category
                if (video.getVideoUrl().contains(source.toString())) {
                    String[] sections = source.toString().split("/");
                    String category = String.format(getString(R.string.all_category_placeholder),
                            sections[sections.length - 1]);

                    ListRow row = findListRow(category);

                    // if found add this video
                    // if not, create a new row and add it
                    if (row != null) {
                        ((SortedObjectAdapter) row.getAdapter()).add(video);
                    } else {
                        SortedObjectAdapter listRowAdapter = new SortedObjectAdapter(
                                videoNameComparator, mCardPresenter);
                        listRowAdapter.add(video);

                        HeaderItem header = new HeaderItem(0, category, null);
                        mAdapter.add(0, new ListRow(header, listRowAdapter));
                    }

                    break;
                }
            }
        } else {
            ListRow row = findListRow(getString(R.string.all_tv_shows));

            // if found add this video
            // if not, create a new row and add it
            if (row != null) {
                boolean found = false;

                // find the video group and increment the episode count
                for (int i = 0; i < row.getAdapter().size(); i++) {
                    VideoGroup group = (VideoGroup) row.getAdapter().get(i);

                    if (group.getVideo().getName().equals(video.getName())) {
                        if (TextUtils.isEmpty(group.getVideo().getCardImageUrl())) {
                            group.getVideo().setCardImageUrl(video.getCardImageUrl());
                        }

                        group.increment();
                        found = true;
                        break;
                    }
                }

                // if not found, then add the VideoGroup to the row
                if (!found) {
                    ((SortedObjectAdapter) row.getAdapter()).add(new VideoGroup(video));
                }
            } else {
                SortedObjectAdapter listRowAdapter = new SortedObjectAdapter(
                        videoGroupNameComparator, mTvShowsCardPresenter);
                listRowAdapter.add(new VideoGroup(video));

                HeaderItem header = new HeaderItem(0, getString(R.string.all_tv_shows), null);
                mAdapter.add(0, new ListRow(header, listRowAdapter));
            }
        }
    }

    private void rebuildSubCategories() {
        List<Video> videos = Video.listAll(Video.class);
        Collections.sort(videos, new Comparator<Video>() {
            @Override
            public int compare(Video o1, Video o2) {
                return Long.valueOf(o2.getCreated()).compareTo(o1.getCreated());
            }
        });

        // get top 15 movies and TV shows
        final int max = 15;
        List<Video> movies = new ArrayList<Video>(max);
        List<Video> tvShows = new ArrayList<Video>(max);

        for (Video video : videos) {
            if (movies.size() == max && tvShows.size() == max) {
                break;
            }

            if (video.isMatched() && video.isMovie() && movies.size() <= max) {
                movies.add(video);
            } else if (video.isMatched() && !video.isMovie() && tvShows.size() <= max) {
                tvShows.add(video);
            }
        }

        ListRow unMatchedRow = findListRow(getString(R.string.unmatched));

        // recently added movies
        addRecentlyAddedMovies(movies, unMatchedRow);

        // recently added TV shows
        addRecentlyAddedTvShows(tvShows, unMatchedRow);

        // add genres for movies & TV Shows
        addGenres(videos, unMatchedRow);
    }

    private void addRecentlyAddedTvShows(List<Video> tvShows, ListRow unMatchedRow) {
        if (!tvShows.isEmpty()) {
            ListRow row = findListRow(getString(R.string.recently_added_tv_episodes));
            if (row != null) {
                ((ArrayObjectAdapter) row.getAdapter()).clear();
                ((ArrayObjectAdapter) row.getAdapter()).addAll(0, tvShows);
            } else {
                ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(mTvShowsCardPresenter);
                listRowAdapter.addAll(0, tvShows);

                HeaderItem header = new HeaderItem(0, getString(R.string.recently_added_tv_episodes), null);
                int index = mAdapter.size() > 1 ? mAdapter.size() - 1 : 0;
                if (unMatchedRow != null) index -= 1;
                mAdapter.add(index, new ListRow(header, listRowAdapter));
            }
        }
    }

    private void addRecentlyAddedMovies(List<Video> movies, ListRow unMatchedRow) {
        if (!movies.isEmpty()) {
            ListRow row = findListRow(getString(R.string.recently_added_movies));
            if (row != null) {
                ((ArrayObjectAdapter) row.getAdapter()).clear();
                ((ArrayObjectAdapter) row.getAdapter()).addAll(0, movies);
            } else {
                ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(mCardPresenter);
                listRowAdapter.addAll(0, movies);

                HeaderItem header = new HeaderItem(0, getString(R.string.recently_added_movies), null);
                int index = mAdapter.size() > 1 ? mAdapter.size() - 1 : 0;
                if (unMatchedRow != null) index -= 1;
                mAdapter.add(index, new ListRow(header, listRowAdapter));
            }
        }
    }

    private void addGenres(List<Video> videos, ListRow unMatchedRow) {
        Set<String> movieGenres = new TreeSet<String>();
        Set<String> tvShowGenres = new TreeSet<String>();

        for (Video video : videos) {
            if (video.isMovie()) {
                if (video.getMovie() != null && video.getMovie().getFlattenedGenres() != null) {
                    String[] gs = video.getMovie().getFlattenedGenres().split(",");
                    if (gs.length > 0) {
                        for (String genre : gs) {
                            if (genre.trim().length() > 0) {
                                movieGenres.add(genre);
                            }
                        }
                    }
                }
            } else {
                if (video.getTvShow() != null && video.getTvShow().getFlattenedGenres() != null) {
                    String[] gs = video.getTvShow().getFlattenedGenres().split(",");
                    if (gs.length > 0) {
                        for (String genre : gs) {
                            if (genre.trim().length() > 0) {
                                tvShowGenres.add(genre);
                            }
                        }
                    }
                }
            }
        }

        if (!movieGenres.isEmpty()) {
            HeaderItem gridHeader = new HeaderItem(0, getString(R.string.movies_genre), null);
            ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(new GridItemPresenter(getActivity()));
            for (String genre : movieGenres) {
                gridRowAdapter.add(new GridGenre(genre, Source.Type.MOVIE));
            }
            int index = mAdapter.size() > 1 ? mAdapter.size() - 1 : 0;
            if (unMatchedRow != null) index -= 1;
            mAdapter.add(index, new ListRow(gridHeader, gridRowAdapter));
        }

        if (!tvShowGenres.isEmpty()) {
            HeaderItem gridHeader = new HeaderItem(0, getString(R.string.tvshows_genre), null);
            ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(new GridItemPresenter(getActivity()));
            for (String genre : tvShowGenres) {
                gridRowAdapter.add(new GridGenre(genre, Source.Type.TV_SHOW));
            }
            int index = mAdapter.size() > 1 ? mAdapter.size() - 1 : 0;
            if (unMatchedRow != null) index -= 1;
            mAdapter.add(index, new ListRow(gridHeader, gridRowAdapter));
        }
    }

    private void refresh() {
        mAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        addSettingsHeader();
        loadVideos();
        setAdapter(mAdapter);
    }

    private void addSettingsHeader() {
        HeaderItem gridHeader = new HeaderItem(0, getString(R.string.settings), null);
        ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(new GridItemPresenter(getActivity()));
        gridRowAdapter.add(getString(R.string.add_source));
        gridRowAdapter.add("Refresh");
        gridRowAdapter.add("Library Update Service");
        gridRowAdapter.add(getString(R.string.customization));
        //TODO If I want to make any other settings, do so here
        //TODO Add credits section for the libaums
        mAdapter.add(new ListRow(gridHeader, gridRowAdapter));
    }

    private ListRow findListRow(String headerName) {
        for (int i = 0; i < mAdapter.size(); i++) {
            ListRow row = (ListRow) mAdapter.get(i);
            if (headerName.equals(row.getHeaderItem().getName())) {
                return row;
            }
        }

        return null;
    }

    private void updateRecommendations() {
        getActivity().startService(new Intent(getActivity(), RecommendationsService.class));
    }

    private void updateBackground(String url) {
        SharedPreferences sharedPrefs = PreferenceManager
                .getDefaultSharedPreferences(getActivity().getApplicationContext());

        RequestCreator requestCreator = Picasso.with(getActivity())
                .load(url)
                .placeholder(R.drawable.placeholder)
                .resize(mMetrics.widthPixels, mMetrics.heightPixels)
                .centerCrop()
                .skipMemoryCache();

        switch(Enums.BlurState.valueOf(sharedPrefs.getString(Constants.BACKGROUND_BLUR, ""))) {
            case ON:
                requestCreator = requestCreator.transform(mBlurTransformation);
                break;
        }

        requestCreator.into(mBackgroundTarget);
    }

    private void clearBackground() {
        BackgroundManager.getInstance(getActivity()).setDrawable(mDefaultBackground);
    }

    private void startBackgroundTimer() {
        if (null != mBackgroundTimer) {
            mBackgroundTimer.cancel();
        }
        mBackgroundTimer = new Timer();
        mBackgroundTimer.schedule(new UpdateBackgroundTask(), 300);
    }

    private OnClickListener getSearchClickedListener() {
        return new OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), SearchActivity.class);
                startActivity(intent);
            }
        };
    }

    private OnItemSelectedListener getDefaultItemSelectedListener() {
        return new OnItemSelectedListener() {
            @Override
            public void onItemSelected(Object item, Row row) {
                if (item instanceof Video) {
                    try {
                        mBackgroundImageUrl = ((Video) item).getBackgroundImageUrl();
                        startBackgroundTimer();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (item instanceof VideoGroup) {
                    try {
                        mBackgroundImageUrl = ((VideoGroup) item).getVideo().getBackgroundImageUrl();
                        startBackgroundTimer();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };
    }

    private BrowseTransitionListener getBrowseTransitionListener() {
        return new BrowseTransitionListener() {

            @Override
            public void onHeadersTransitionStop(boolean withHeaders) {
                if (withHeaders) {
                    resetBackground();
                }
            }

        };
    }

    /**
     * Card Click Listener
     */
    private OnItemClickedListener getDefaultItemClickedListener() {
        return new OnItemClickedListener() {
            @Override
            public void onItemClicked(Object item, Row row) {
                if (item instanceof Video || item instanceof VideoGroup) {
                    if (item instanceof VideoGroup) {
                        Log.d(TAG, "Item is instance of VideoGroup");
                        Intent intent = new Intent(getActivity(), DetailsActivity.class);
                        intent.putExtra(Constants.IS_VIDEO, false);
                        intent.putExtra(Constants.VIDEO_GROUP, (VideoGroup) item);
                        startActivity(intent);
                    } else if (((Video) item).isMatched()) {
                        Log.d(TAG, "Item is matched");
                        Intent intent = new Intent(getActivity(), DetailsActivity.class);
                        intent.putExtra(Constants.IS_VIDEO, true);
                        intent.putExtra(Constants.VIDEO, (Video) item);
                        startActivity(intent);
                    } else {
                        Log.d(TAG, "Nothing special, play video");
                        VideoUtils.playVideo(new WeakReference<Activity>(getActivity()), (Video) item);
                    }
                } else if (item instanceof GridGenre) {
                    GridGenre genre = (GridGenre) item;
                    Intent intent = new Intent(getActivity(), GridViewActivity.class);
                    intent.putExtra(Constants.GENRE, genre.getTitle());
                    if (genre.getType() == Source.Type.MOVIE) {
                        intent.putExtra(Constants.IS_VIDEO, true);
                    } else {
                        intent.putExtra(Constants.IS_VIDEO, false);
                    }
                    startActivity(intent);
                } else if (item instanceof String && ((String) item).contains(getString(R.string.add_source))) {
                    showAddSourceDialog();
                } else if (item instanceof String && ((String) item).contains(getString(R.string.customization))) {
                    showCustomizeDialog();
                } else if (item instanceof String && ((String) item).contains("LAN")) {
                    showAddLanDialog();
                } else if (item instanceof String && ((String) item).contains("Library Update Service")) {
                    Log.d(TAG, "Starting library update");
                    Intent intent = new Intent(getActivity(), LibraryUpdateService.class);
                    getActivity().startService(intent);
                } else if (item instanceof String && ((String) item).contains("Refresh")) {
                    refresh();
                }
            }
        };
    }

    private void showAddSourceDialog() {
        FragmentManager fm = getFragmentManager();
        AddSourceDialogFragment addSourceDialog = AddSourceDialogFragment.newInstance();
        addSourceDialog.setTargetFragment(this, 0);
        addSourceDialog.show(fm, AddSourceDialogFragment.class.getSimpleName());
    }
    private void showAddLanDialog() {
        FragmentManager fm = getFragmentManager();
        AddSourceDialogFragment addSourceDialog = AddSourceDialogFragment.newInstance();
        addSourceDialog.setTargetFragment(this, 0);
        addSourceDialog.show(fm, AddSourceDialogFragment.class.getSimpleName());
    }

    private void showCustomizeDialog() {
        FragmentManager fragmentManager = getFragmentManager();
        CustomizeDialogFragment customizeFragment = new CustomizeDialogFragment();
        customizeFragment.setTargetFragment(this, 0);
        customizeFragment.show(fragmentManager, CustomizeDialogFragment.class.getSimpleName());
    }

    @Override
    public void onSaveCustomization() {
        reloadAdapters();
    }


    private class UpdateBackgroundTask extends TimerTask {
        @Override
        public void run() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mBackgroundImageUrl != null) {
                        updateBackground(mBackgroundImageUrl);
                    } else {
                        clearBackground();
                    }
                }
            });
        }
    }

    public void refreshLocalLibrary() {
        Log.d(TAG, "Try to find any local videos");
        //Query mediastore
        //via http://www.sandersdenardi.com/querying-and-removing-media-from-android-mediastore/
        String[] retCol = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.MINI_THUMB_MAGIC,
                MediaStore.Video.Media.DATE_TAKEN,
                MediaStore.Video.Media.DATA

        };
        Cursor cur = getActivity().getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                retCol,
                null, null, null
        );
        Log.d(TAG, "Returns "+cur.getCount()+" result(s)");
        //Temporarily delete ALL videos, then change for local only
        /*List<Video> videos = Select
                .from(Video.class)
                .list();
        for(Video v: videos) {
            Log.d(TAG, "Deleting "+v.getName()+" @ "+v.getVideoUrl());
            v.delete();
        }*/
        pushLocalVideos(cur);
        //Do same for any USB drives
        UsbManager manager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while(deviceIterator.hasNext()){
            UsbDevice device = deviceIterator.next();
            Log.d(TAG, "Found "+device.getDeviceName()+" "+device.getProductName()+" "+device.getManufacturerName());            //your code
            Log.d(TAG, "Is "+device.getDeviceClass()+", "+ device.getDeviceSubclass()+" "+ UsbConstants.USB_CLASS_MASS_STORAGE);
            if(device.getDeviceClass() == 0 || device.getDeviceClass() == 8) {
                PendingIntent mPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(ACTION_USB_PERMISSION), 0);
                IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                getActivity().registerReceiver(mUsbReceiver, filter);
                manager.requestPermission(device, mPermissionIntent);
            }
        }

//        onActivityCreated(null);
    }

    /**
     * A singular method to save videos directly using a cursor, which can be obtained through
     * a media query like getting all the videos. Using this method, all videos in the internal
     * storage can be obtained, plus this can be extended to get content from a USB drive
     * @param cur Cursor containing media with certain attributes queried
     */
    public void pushLocalVideos(Cursor cur) {
        if(cur.getCount() > 0) {
            cur.moveToFirst();
            while(!cur.isAfterLast()) {
                int id = cur.getInt(cur.getColumnIndex(MediaStore.MediaColumns._ID));
                Uri uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id);
                String filepath = cur.getString(cur.getColumnIndex(MediaStore.Video.Media.DATA));
                File localVideo = new File(filepath);

                //Let's delete this video and all like it from our video database
                List<Video> videos = Select
                        .from(Video.class)
                        .where(Condition.prop("video_url").like("%" + uri.toString() + "%"))
                        .or(Condition.prop("video_url").like("%" + filepath.substring(7) + "%")) //Remove file://
                        .or(Condition.prop("video_url").like("%" + localVideo.getAbsolutePath() + "%"))
                        .or(Condition.prop("video_url").like("%" + Uri.fromFile(localVideo).toString() + "%"))
                        .list();
                Log.d(TAG, "Purging "+videos.size()+" item(s)");
                for(Video v: videos) {
                    Log.d(TAG, "Deleting "+v.getVideoUrl());
                    v.delete();
                }

                //Now returning to our original thing
                String title = cur.getString(cur.getColumnIndex(MediaStore.MediaColumns.TITLE));

//                String background = MediaStore.Video.Thumbnails.getContentUri(cur.getString(cur.getColumnIndex(MediaStore.Video.Thumbnails.FULL_SCREEN_KIND));)

                long created = cur.getLong(cur.getColumnIndex(MediaStore.Video.VideoColumns.DATE_TAKEN));
                filepath = filepath.substring(20); //Remove "/storage/emulated/0/"
                filepath = "file://"+filepath;
                Log.d(TAG, "Found id "+id+", "+title);
                Log.d(TAG, "At place "+uri.getPath()+" / "+Uri.fromFile(localVideo).toString());
//                Log.d(TAG, "Created " + created);

               /* Log.d(TAG, localVideo.getAbsolutePath());
                Log.d(TAG, localVideo.getPath());
                try {
                    Log.d(TAG, localVideo.getCanonicalPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }*/
//                Log.d(TAG, Uri.fromFile(localVideo).toString());
                Video localFile = new Video();
//                localFile.setBackgroundImageUrl();
//                localFile.setVideoUrl(uri.toString());
//                localFile.setVideoUrl(localVideo.getAbsolutePath());
                localFile.setVideoUrl(Uri.fromFile(localVideo).toString());
                localFile.setName(title);
                localFile.setCreated(created);
                localFile.setIsMovie(true);
                localFile.setIsMatched(false);
                localFile.setIsLocalFile(true);
                localFile.setOverview(filepath); //VideoColumns.Description
                Log.d(TAG, localFile.toString());
                localFile.save();
                DownloadTaskHelper.updateSingleVideo(localFile, new DownloadTaskHelper.DownloadTaskListener() {
                    @Override
                    public void onDownloadFinished() {
                        refresh();
                    }
                });
                Log.d(TAG, "There are "+Video.count(Video.class, null, null)+" video(s)");
                cur.moveToNext();
            }
            cur.close();
            Log.d(TAG, "Updated library, now update fragment");
        }
    }

    //For USB Access
    private static final String ACTION_USB_PERMISSION =
            "com.jerrellmardis.amphitheatre.USB_PERMISSION";
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //call method to set up device communication
                            Log.d(TAG, "We're good to go");
                            Log.d(TAG, "Start reading from USB");
                            Log.d(TAG, Environment.MEDIA_MOUNTED+" "+(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)));
                            Log.d(TAG, device.getConfiguration(0).getInterface(0).getInterfaceClass()+"");
                            UsbInterface usbInterface = device.getConfiguration(0).getInterface(0);
//                            Log.d(TAG, usbInterface.getName()+"");
//                            String extStorageDirectory  = getActivity().getExternalFilesDir(null).getAbsolutePath();
//                            Log.d(TAG, extStorageDirectory);
                            UsbManager manager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
                            UsbDeviceConnection connection = manager.openDevice(device);
//                            Log.d(TAG, connection.getSerial());
                            //TODO Capture MediaMounted actions to get permission ahead of time and update lib for easy access
                            // TODO universal search that refreshes library before doing stuff
                            /*String extStore = System.getenv("EXTERNAL_STORAGE");
                            String secStore = System.getenv("SECONDARY_STORAGE");
                            Log.d(TAG, extStore+" "+secStore);*/
                            /*List<StorageHelper.StorageVolume> volumes = StorageHelper.getStorages(true);
                            for(StorageHelper.StorageVolume v: volumes) {
                                Log.d(TAG, v.device+" "+v.fileSystem+" "+v.file.getAbsolutePath()+" "+v.getType().name());
                            }*/
                            UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(getActivity());
                            Log.d(TAG, "Found "+devices.length +" devices");
                            UsbMassStorageDevice flashDrive = devices[0];
                            try {
                                flashDrive.init(); //We already have permission
                            } catch (IOException e) {
                                e.printStackTrace();
                                Log.d(TAG, e.getMessage()+"");
                            }
                            Log.d(TAG, flashDrive.getPartitions().get(0).getVolumeLabel()+"");
//                            Log.d(TAG, flashDrive.getPartitions().get(0).getFileSystem().getRootDirectory().getName()+"");
                            Log.d(TAG, flashDrive.getPartitions().get(0).getFileSystem().getVolumeLabel() + "");
                            // we always use the first partition of the device
                            FileSystem fs = flashDrive.getPartitions().get(0).getFileSystem();
                            UsbFile root = fs.getRootDirectory();
                            List<UsbFile> files = new ArrayList<UsbFile>();
                            try {
                                files = Arrays.asList(root.listFiles());
                                traverseAndFindVideos(files);
                                /*for(UsbFile usbFile: files) {

                                }*/
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            File file=new File("/");
                            File[] listFiles = file.listFiles();
                            for ( File f : listFiles){
//                                Log.d(TAG, f.getAbsolutePath());
                                /*if(!f.getAbsolutePath().contains("emulated")) {
                                    //Read drive
                                    File usbDrive = f;
                                    File[] driveFiles = usbDrive.listFiles();
                                    for(File f2: driveFiles) {
                                        Log.d(TAG, f2.getAbsolutePath());
                                        //Is Video?
                                        Log.d(TAG, VideoUtils.isVideoFile(f2.getAbsolutePath())+" < is video?");
                                        //If so, push
                                    }
                                }*/
                                //you can do all file processing part here
                                /*String[] retCol = {
                                        MediaStore.Video.Media._ID,
                                        MediaStore.Video.Media.TITLE,
                                        MediaStore.Video.Media.MINI_THUMB_MAGIC,
                                        MediaStore.Video.Media.DATE_TAKEN,
                                        MediaStore.Video.Media.DATA

                                };
                                Cursor cur = getActivity().getContentResolver().query(
                                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                        retCol,
                                        null, null, null
                                );
                                Log.d(TAG, cur.getCount()+"");
                                cur.close();*/
                            }
                        }
                    }
                    else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };
    public void traverseAndFindVideos(List<UsbFile> usbFiles) throws IOException {
        //Look for videos recursively.
        //If directory, recall function
        //If video, save (and download metadata)
        //Else do nothing
        for(UsbFile file: usbFiles) {
            if(file.isDirectory()) {
                Log.d(TAG, file.getName()+" is a folder");
                traverseAndFindVideos(Arrays.asList(file.listFiles()));
            } else if(VideoUtils.isVideoFile(file.getName())) {
                Log.d(TAG, file.getName()+" is a video");
                String path = "file://WDO_MEDIA32/"+file.getName();
//                file.read(0,);
                Log.d(TAG, "Try "+path);


                //Let's delete this video and all like it from our video database
                List<Video> videos = Select
                        .from(Video.class)
                        .where(Condition.prop("video_url").like("%" + file.getName() + "%"))
                        .list();
                Log.d(TAG, "Purging "+videos.size()+" item(s)");
                for(Video v: videos) {
                    Log.d(TAG, "Deleting "+v.getVideoUrl());
                    v.delete();
                }

                /*Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(Uri.parse(path), VideoUtils.getMimeType(path, true));
                startActivity(i);*/
                Video usbVideo = new Video();
                usbVideo.setVideoUrl(new SuperFile(file).getPath());
                usbVideo.setName(file.getName());
                usbVideo.setCreated(file.createdAt());
                usbVideo.setIsMovie(true);
                usbVideo.setIsMatched(false);
//                usbVideo.setFile(new SuperFile(file));
                usbVideo.setOverview(file.getName()); //VideoColumns.Description
                Log.d(TAG, usbVideo.toString());
                usbVideo.save();
                DownloadTaskHelper.updateSingleVideo(usbVideo, new DownloadTaskHelper.DownloadTaskListener() {
                    @Override
                    public void onDownloadFinished() {
                        refresh();
                    }
                });
//                Log.d(TAG, file.getParent().getName());
            } else {
                Log.d(TAG,"Ignore "+file.getName());
            }
        }
        refresh();
    }

    /*public void cacheFile(UsbFile entry) throws IOException {
        CopyTaskParam param = new CopyTaskParam();
        param.from = entry;
        File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/usbfileman/cache");
        f.mkdirs();
        int index = entry.getName().lastIndexOf(".");
        String prefix = entry.getName().substring(0, index);
        String ext = entry.getName().substring(index);
        // prefix must be at least 3 characters
        if(prefix.length() < 3) {
            prefix += "pad";
        }
        param.to = File.createTempFile(prefix, ext, f);
        new CopyTask().execute(param);
    }*/

}