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

package com.jerrellmardis.amphitheatre.model;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.text.format.DateFormat;
import android.util.Log;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.jerrellmardis.amphitheatre.model.tmdb.Movie;
import com.jerrellmardis.amphitheatre.model.tmdb.TvShow;
import com.jerrellmardis.amphitheatre.util.Utils;
import com.orm.SugarRecord;

import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Jerrell Mardis on 8/7/14.
 */
public class Video extends SugarRecord<Video> implements Serializable {

    private Movie movie;
    private TvShow tvShow;
    private long created;
    private String name;
    private String cardImageUrl;
    private String backgroundImageUrl;
    private String videoUrl; //Comma separated array
    private String overview;
    private boolean isMatched;
    private boolean isMovie;
    private boolean isWatched;
    private long duration;
    //TODO thumbnail generation
    private FileSource source;
    private boolean hidden;
//    private SuperFile file;

    public String getName() {
        return name.replaceAll("''", "'");
    }

    public void setName(String name) {
        name = name.replaceAll("''", "'");
        this.name = name.replaceAll("'", "''");
    }

    public String getCardImageUrl() {
        return cardImageUrl;
    }

    public void setCardImageUrl(String cardImageUrl) {
        this.cardImageUrl = cardImageUrl;
    }

    public String getBackgroundImageUrl() {
        return backgroundImageUrl;
    }

    public void setBackgroundImageUrl(String backgroundImageUrl) {
        this.backgroundImageUrl = backgroundImageUrl;
    }

    public String getVideoUrl() {
        return videoUrl.split(",")[0];
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
        if(videoUrl.contains("file://"))
            this.source = FileSource.INTERNAL_STORAGE;
        else if(videoUrl.contains("usb://"))
            this.source = FileSource.USB;
        else if(videoUrl.contains("smb://"))
            this.source = FileSource.SMB;
    }
    public void setVideoUrl(List<String> urls) {
        String list = "";
        Iterator<String> iterator = urls.iterator();
        while(iterator.hasNext()) {
            String url = iterator.next();
            list += url+",";
        }
        videoUrl = list;
    }
    public String[] getVideoUrls() {
        return videoUrl.split(",");
    }

    public boolean isMatched() {
        return isMatched;
    }

    public void setIsMatched(boolean matched) {
        this.isMatched = matched;
    }

    public Movie getMovie() {
        return movie;
    }

    public void setMovie(Movie movie) {
        this.movie = movie;
    }

    public TvShow getTvShow() {
        return tvShow;
    }

    public void setTvShow(TvShow tvShow) {
        this.tvShow = tvShow;
    }

    public boolean isMovie() {
        return isMovie;
    }

    public void setIsMovie(boolean isMovie) {
        this.isMovie = isMovie;
    }

    public String getOverview() {
        return overview;
    }

    public void setOverview(String overview) {
        this.overview = overview;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public boolean isWatched() {
        return isWatched;
    }

    public void setWatched(boolean isWatched) {
        this.isWatched = isWatched;
    }
    public String toString() {
        /*
    private long created;
    private String name;
    private String cardImageUrl;
    private String backgroundImageUrl;
    private String videoUrl;
    private String overview;
    private boolean isMatched;
    private boolean isMovie;
    private boolean isWatched;
         */
//        String out = ((isLocalFile())?"LOCAL ":"NOT LOCAL ");
        String out = source.name()+" ";
        out += ((isMovie())?"MOVIE":"TV SHOW");
        return out + "   created "+getCreated()+" named "+getName()+"["+getProductionYear()+"] at "+getVideoUrl()+" [x"+getVideoUrls().length+"];  " + getOverview();
    }

    public boolean isLocalFile() {
        return source == FileSource.INTERNAL_STORAGE;
    }

    public void setIsLocalFile(boolean localFile) {
        if(localFile)
            source = FileSource.INTERNAL_STORAGE;
    }

    @Override
    public void save() {
        super.save();
        Log.d("amp:Video", "Save");
    }

    public FileSource getSource() {
        return source;
    }

    public void setSource(FileSource source) {
        this.source = source;
    }

/*    public UsbFile getUsbFile() {
        return usbFile;
    }

    public void setUsbFile(UsbFile usbFile) {
        this.usbFile = usbFile;
        //This must be a usb file
        source = FileSource.USB;
    }*/

    public SuperFile getFile(Context mContext) throws Exception {
        if(source == FileSource.SMB) {
            try {
                return new SuperFile().newSmbFile(getVideoUrl());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        } else if(source == FileSource.INTERNAL_STORAGE) {
            return new SuperFile().newLocalFile(getVideoUrl());
        } else if(source == FileSource.USB) {
            UsbManager manager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
            HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
            Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
            UsbDevice device = null;
            while(deviceIterator.hasNext()){
                UsbDevice d = deviceIterator.next();
                if(d.getDeviceClass() == 0 || d.getDeviceClass() == 8) {
                    device = d;
                }
            }
            UsbDeviceConnection connection = manager.openDevice(device);
            UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(mContext);
            UsbMassStorageDevice flashDrive = devices[0];
            try {
                flashDrive.init(); //We already have permission
            } catch (IOException e) {
                e.printStackTrace();
            }
            FileSystem fs = flashDrive.getPartitions().get(0).getFileSystem();
            return new SuperFile(Utils.searchUsbFiles(fs.getRootDirectory(), getVideoUrl()));
        }
        throw new Exception("FileSource is either undefined or not supported");
    }
    /*public SuperFile getFile() {
        if(file == null) {
            if(source == FileSource.SMB) {
                try {
                    file = new SuperFile().newSmbFile(getVideoUrl());
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            } else if(source == FileSource.INTERNAL_STORAGE) {
                file = new SuperFile().newLocalFile(getVideoUrl());
            }
        }
        return file;
    }*/

/*    public void setFile(SuperFile file) {
        this.file = file;
    }*/
    //Creates a new version of this video so it can be passed around indirectly for threading
    public Video clone() {
        Video clone = new Video();
        clone.setName(name);
        clone.setVideoUrl(Arrays.asList(getVideoUrls()));
        clone.setIsMovie(isMovie());
        clone.setCreated(getCreated());
        clone.setSource(getSource());
        clone.setBackgroundImageUrl(getBackgroundImageUrl());
        clone.setCardImageUrl(getCardImageUrl());
        clone.setIsLocalFile(isLocalFile());
        clone.setMovie(getMovie());
        clone.setIsMatched(isMatched());
        clone.setOverview(getOverview());
        clone.setTvShow(getTvShow());
        clone.setWatched(isWatched());
        clone.setDuration(getDuration());
        clone.setHidden(isHidden());

        return clone;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public int getProductionYear() {
        if(tvShow != null) {
//            Date prod = new Date(Date.parse(tvShow.getEpisode().getAirDate()));
//            Log.d("amp:Video", Date.parse(tvShow.getEpisode().getAirDate())+" to "+prod.getYear());
//            return prod.getYear();
            if(tvShow.getEpisode() != null)
                return Integer.parseInt(tvShow.getEpisode().getAirDate().substring(0,4));
        } else if(movie != null) {
//            Date prod = new Date(Date.parse(movie.getReleaseDate()));
//            Log.d("amp:Video", Date.parse(movie.getReleaseDate())+" to "+prod.getYear());
//            return prod.getYear();
            if(movie.getReleaseDate().length() > 4)
                return Integer.parseInt(movie.getReleaseDate().substring(0,4));
            else
                Log.e("amp:Video", "Got weird release date "+movie.getReleaseDate());
        }
        return 2015;
    }

    public Serializable getSerializedVideo() {
        return this;
    }
}