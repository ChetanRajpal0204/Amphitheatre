package com.jerrellmardis.amphitheatre.model;

import android.app.SearchManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.graphics.Bitmap;
import android.os.Environment;
import android.provider.BaseColumns;
import android.util.Log;

import com.jerrellmardis.amphitheatre.R;
import com.jerrellmardis.amphitheatre.provider.PaginatedCursor;
import com.jerrellmardis.amphitheatre.util.VideoUtils;
import com.squareup.picasso.Picasso;

import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by N on 6/10/2015.
 */
public class VideoDatabase {
    private Context context;
    //The columns we'll include in the video database table
    public static final String KEY_NAME = SearchManager.SUGGEST_COLUMN_TEXT_1;
    public static final String KEY_DESCRIPTION = SearchManager.SUGGEST_COLUMN_TEXT_2;
    public static final String KEY_ICON = SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE;
    public static final String KEY_IS_LIVE = SearchManager.SUGGEST_COLUMN_IS_LIVE;
    public static final String KEY_PURCHASE_PRICE = SearchManager.SUGGEST_COLUMN_PURCHASE_PRICE;
    public static final String KEY_DURATION = SearchManager.SUGGEST_COLUMN_DURATION;
    public static final String KEY_INTENT_EXTRA = SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA;
    public static final String KEY_INTENT_DATA_ID = SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID;
    public static final String KEY_INTENT_DATA = SearchManager.SUGGEST_COLUMN_INTENT_DATA;

    public static final String KEY_DATA_TYPE = SearchManager.SUGGEST_COLUMN_CONTENT_TYPE;
    public static final String KEY_PRODUCTION_YEAR = SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR;
    public static final String KEY_ACTION = SearchManager.SUGGEST_COLUMN_INTENT_ACTION;

    private static final String TAG = "amp:VideoDatabase";
    private static final String DATABASE_NAME = "video_database_leanback";
    private static final String FTS_VIRTUAL_TABLE = "Leanback_table";
    private static final int DATABASE_VERSION = 7;
    private static final HashMap<String, String> COLUMN_MAP = buildColumnMap();
    private static int CARD_WIDTH = 313;
    private static int CARD_HEIGHT = 176;
    private final VideoDatabaseOpenHelper mDatabaseOpenHelper;

    public VideoDatabase(Context mContext) {
        mDatabaseOpenHelper = new VideoDatabaseOpenHelper(mContext);
        context = mContext;
    }

    private static HashMap buildColumnMap() {
        HashMap map = new HashMap();
        map.put(KEY_NAME, KEY_NAME);
        map.put(KEY_DESCRIPTION, KEY_DESCRIPTION);
        map.put(KEY_DATA_TYPE, KEY_DATA_TYPE);
        map.put(KEY_PRODUCTION_YEAR, KEY_PRODUCTION_YEAR);
        map.put(KEY_ACTION, KEY_ACTION);
        map.put(KEY_IS_LIVE, KEY_IS_LIVE);
        map.put(KEY_PURCHASE_PRICE, KEY_PURCHASE_PRICE);
        map.put(KEY_ICON, KEY_ICON);
        map.put(KEY_DURATION, KEY_DURATION);
        map.put(KEY_INTENT_EXTRA, KEY_INTENT_EXTRA);
        map.put(KEY_INTENT_DATA_ID, KEY_INTENT_DATA_ID);
        map.put(KEY_INTENT_DATA, KEY_INTENT_DATA);
        map.put(BaseColumns._ID, "rowid AS " +
                BaseColumns._ID);
        map.put(SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID, "rowid AS " +
                SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID);
        map.put(SearchManager.SUGGEST_COLUMN_SHORTCUT_ID, "rowid AS " +
                SearchManager.SUGGEST_COLUMN_SHORTCUT_ID);
        return map;
    }
    /**
     * Returns a Cursor over all words that match the first letter of the given query
     *
     * @param query   The string to search for
     * @param columns The columns to include, if null then all are included
     * @return Cursor over all words that match, or null if none found.
     */
    public Cursor getWordMatch(String query, String[] columns) {
        /* This builds a query that looks like:
         *     SELECT <columns> FROM <table> WHERE <KEY_WORD> MATCH 'query*'
         * which is an FTS3 search for the query text (plus a wildcard) inside the word column.
         *
         * - "rowid" is the unique id for all rows but we need this value for the "_id" column in
         *    order for the Adapters to work, so the columns need to make "_id" an alias for "rowid"
         * - "rowid" also needs to be used by the SUGGEST_COLUMN_INTENT_DATA alias in order
         *   for suggestions to carry the proper intent data.SearchManager
         *   These aliases are defined in the VideoProvider when queries are made.
         * - This can be revised to also search the definition text with FTS3 by changing
         *   the selection clause to use FTS_VIRTUAL_TABLE instead of KEY_WORD (to search across
         *   the entire table, but sorting the relevance could be difficult.
         */

/*
        String selection = KEY_NAME + " MATCH ?";
        String[] selectionArgs = new String[]{query + "*"};

        return query(selection, selectionArgs, columns);
*/
        String selection = KEY_NAME + " MATCH ?";
        String[] selectionArgs = new String[]{query + "*"};

        Log.d(TAG, "Search for "+query);
        return query(selection, selectionArgs, columns);
    }
    /**
     * Performs a database query.
     *
     * @param selection     The selection clause
     * @param selectionArgs Selection arguments for "?" components in the selection
     * @param columns       The columns to return
     * @return A Cursor over all rows matching the query
     */
    private Cursor query(String selection, String[] selectionArgs, String[] columns) {
        /* The SQLiteBuilder provides a map for all possible columns requested to
         * actual columns in the database, creating a simple column alias mechanism
         * by which the ContentProvider does not need to know the real column names
         */
        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(FTS_VIRTUAL_TABLE);
        builder.setProjectionMap(COLUMN_MAP);

//        mDatabaseOpenHelper.loadDatabase();

        Log.d(TAG, "Querying v" + mDatabaseOpenHelper.getReadableDatabase().getVersion());
        Log.d(TAG, selection+"; "+ Arrays.asList(selectionArgs).toString()+"; "+ Arrays.asList(columns).toString());
        Cursor userQuery = builder.query(mDatabaseOpenHelper.getReadableDatabase(),
                columns, selection, selectionArgs, null, null, null);
        Log.d(TAG, userQuery.getCount()+" items in it");
        Cursor cursor = new PaginatedCursor(userQuery);

        if (cursor == null) {
            return null;
        } else if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }
        return cursor;
    }


    /**
     * This creates/opens the database.
     */
    private static class VideoDatabaseOpenHelper extends SQLiteOpenHelper {

        private final Context mHelperContext;
        private SQLiteDatabase mDatabase;

        VideoDatabaseOpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            Log.d(TAG, "Created VideoDBOpenHelpr");
            mHelperContext = context;
//            loadDatabase();
        }

        /* Note that FTS3 does not support column constraints and thus, you cannot
         * declare a primary key. However, "rowid" is automatically used as a unique
         * identifier, so when making requests, we will use "_id" as an alias for "rowid"
         */
        private static final String FTS_TABLE_CREATE =
                "CREATE VIRTUAL TABLE " + FTS_VIRTUAL_TABLE +
                        " USING fts3 (" +
                        KEY_NAME + ", " +
                        KEY_DESCRIPTION + "," +
                        KEY_DATA_TYPE + "," +
                        KEY_PRODUCTION_YEAR + "," +
                        KEY_IS_LIVE + "," +
                        KEY_PURCHASE_PRICE + "," +
                        KEY_ICON + "," +
                        KEY_DURATION + "," +
                        KEY_INTENT_EXTRA + "," +
                        KEY_INTENT_DATA + "," +
                        KEY_INTENT_DATA_ID + "," +
                        KEY_ACTION + ");";

        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.d(TAG, "Creating VDB v"+db.getVersion());
            mDatabase = db;
            mDatabase.execSQL(FTS_TABLE_CREATE);
            loadDatabase();
        }

        /**
         * Starts a thread to load the database table with words
         */
        private void loadDatabase() {
            new Thread(new Runnable() {
                public void run() {
                    try {
                        loadMovies();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }).start();
        }

        private void loadMovies() throws IOException {
            Log.d(TAG, "Loading movies...");

            List<Video> videos = Source.listAll(Video.class);
            for(Video v: videos) {
//                Log.d(TAG, "Adding "+v.getName());
                addMovie(v);
            }
            Log.d(TAG, "Contains "+videos.size() + " items");

            /*HashMap<String, List<Video>> movies = null;
            try {
                VideoProvider.setContext(mHelperContext);
                movies = VideoProvider.buildMedia(mHelperContext,
                        mHelperContext.getResources().getString(R.string.catalog_url));
            } catch (JSONException e) {
                Log.e(TAG, "JSon Exception when loading movie", e);
            }

            for (Map.Entry<String, List<Video>> entry : movies.entrySet()) {
                List<Video> list = entry.getValue();
                for (Video movie : list) {
                    long id = addMovie(movie);
                    if (id < 0) {
                        Log.e(TAG, "unable to add movie: " + movie.toString());
                    }
                }
            }*/
            // add dummy movies to illustrate action deep link in search detail
            // Android TV Search requires that the media title, MIME type, production year,
            // and duration all match exactly to those found from Google servers.
            /*addMovieForDeepLink(mHelperContext.getString(R.string.noah_title),
                    mHelperContext.getString(R.string.noah_description),
                    R.drawable.noah,
                    8280000,
                    "2014");
            addMovieForDeepLink(mHelperContext.getString(R.string.dragon2_title),
                    mHelperContext.getString(R.string.dragon2_description),
                    R.drawable.dragon2,
                    6300000,
                    "2014");
            addMovieForDeepLink(mHelperContext.getString(R.string.maleficent_title),
                    mHelperContext.getString(R.string.maleficent_description),
                    R.drawable.maleficent,
                    5820000,
                    "2014");*/
        }

        /**
         * Add a movie to the database.
         *
         * @return rowId or -1 if failed
         */
        public long addMovie(Video movie) {
            if(mDatabase == null)
                mDatabase = getWritableDatabase();
            ContentValues initialValues = new ContentValues();
            initialValues.put(KEY_NAME, movie.getName());
            initialValues.put(KEY_DESCRIPTION, movie.getOverview());
            initialValues.put(KEY_DATA_TYPE, VideoUtils.getMimeType(movie.getName(), true)); //TODO Use VideoUtils
            initialValues.put(KEY_PRODUCTION_YEAR, movie.getProductionYear());
            //Now bitmap to file
            Bitmap icon = null;
            try {
                icon = Picasso.with(mHelperContext).load(movie.getCardImageUrl()).get();
                FileOutputStream out = null;
                String path = Environment.getExternalStorageDirectory().toString();
                File file = new File(path, ".amphitheatre"+movie.getName()+".png"); // the File to save to
                try {
                    out = new FileOutputStream(file);
                    icon.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
                    // PNG is a lossless format, the compression factor (100) is ignored
                } catch (Exception e) {
                    e.printStackTrace();
                    initialValues.put(KEY_ICON, movie.getCardImageUrl());
                } finally {
                    try {
                        if (out != null) {
                            out.close();
                            initialValues.put(KEY_ICON, file.getAbsolutePath());
                            Log.d(TAG, "Posting "+file.getAbsolutePath());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        initialValues.put(KEY_ICON, movie.getCardImageUrl());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                initialValues.put(KEY_ICON, movie.getCardImageUrl());
            }
            initialValues.put(KEY_IS_LIVE, false);
            initialValues.put(KEY_PURCHASE_PRICE, "FREE");
            initialValues.put(KEY_DURATION, movie.getDuration());
            initialValues.put(KEY_INTENT_EXTRA, movie.getVideoUrl());
            initialValues.put(KEY_INTENT_DATA_ID,  movie.getVideoUrl());
            initialValues.put(KEY_INTENT_DATA, movie.getVideoUrl());
            initialValues.put(KEY_ACTION, mHelperContext.getString(R.string.global_search));
            return mDatabase.insert(FTS_VIRTUAL_TABLE, null, initialValues);
        }

        /**
         * Add an entry to the database for dummy deep link.
         *
         * @return rowId or -1 if failed
         */
        /*public long addMovieForDeepLink(String title, String description, int icon, long duration, String production_year) {
            ContentValues initialValues = new ContentValues();
            initialValues.put(KEY_NAME, title);
            initialValues.put(KEY_DESCRIPTION, description);
            initialValues.put(KEY_DATA_TYPE, "video/mp4");
            initialValues.put(KEY_PRODUCTION_YEAR, production_year);
            initialValues.put(KEY_ICON, icon);
            initialValues.put(KEY_IS_LIVE, false);
            initialValues.put(KEY_PURCHASE_PRICE, "FREE");
            return mDatabase.insert(FTS_VIRTUAL_TABLE, null, initialValues);
        }*/

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + FTS_VIRTUAL_TABLE);
            onCreate(db);
        }


    }
}
