package com.jerrellmardis.amphitheatre.provider;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import com.jerrellmardis.amphitheatre.model.Source;
import com.jerrellmardis.amphitheatre.model.Video;
import com.jerrellmardis.amphitheatre.model.VideoDatabase;

import java.util.List;

public class VideosProvider extends ContentProvider {
    private static final String TAG = "amp:ContentProvider";
    public static String AUTHORITY = "com.jerrellmardis.amphitheatre";

    // UriMatcher stuff
    private static final int SEARCH_SUGGEST = 0;
    private static final int REFRESH_SHORTCUT = 1;
    private static final UriMatcher URI_MATCHER = buildUriMatcher();
    private VideoDatabase mVideoDatabase;


    public VideosProvider() {
    }

    private static UriMatcher buildUriMatcher() {
        UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        // to get suggestions...
        Log.d(TAG, "suggest_uri_path_query: " + SearchManager.SUGGEST_URI_PATH_QUERY);
        matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH_SUGGEST);
        matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", SEARCH_SUGGEST);
        return matcher;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // Implement this to handle requests to delete one or more rows.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * This method is required in order to query the supported types.
     * It's also useful in our own query() method to determine the type of Uri received.
     */
    @Override
    public String getType(Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case SEARCH_SUGGEST:
                return SearchManager.SUGGEST_MIME_TYPE;
            case REFRESH_SHORTCUT:
                return SearchManager.SHORTCUT_MIME_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URL " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO: Implement this to handle requests to insert a new row.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public boolean onCreate() {
        // TODO: Implement this to initialize your content provider on startup.
        mVideoDatabase = new VideoDatabase(getContext());
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // Use the UriMatcher to see what kind of query we have and format the db query accordingly
        switch (URI_MATCHER.match(uri)) {
            case SEARCH_SUGGEST:
                Log.d(TAG, "search suggest: " + selectionArgs[0] + " URI: " + uri);
                if (selectionArgs == null) {
                    throw new IllegalArgumentException(
                            "selectionArgs must be provided for the Uri: " + uri);
                }
                return getSuggestions(selectionArgs[0]);
            default:
                throw new IllegalArgumentException("Unknown Uri: " + uri);
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        // TODO: Implement this to handle requests to update one or more rows.
        throw new UnsupportedOperationException("Not yet implemented");
    }
    private Cursor getSuggestions(String query) {
        query = query.toLowerCase();
        String[] columns = new String[]{
                BaseColumns._ID,
                VideoDatabase.KEY_NAME,
                VideoDatabase.KEY_DESCRIPTION,
                VideoDatabase.KEY_DATA_TYPE,
                VideoDatabase.KEY_PRODUCTION_YEAR,
                VideoDatabase.KEY_ACTION,
                SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID
        };
        return mVideoDatabase.getWordMatch(query, columns);
    }

    public static List<Video> getMovieList() {
        return Source.listAll(Video.class);
    }
}
