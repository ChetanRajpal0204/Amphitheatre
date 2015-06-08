package com.jerrellmardis.amphitheatre.model;

import android.net.Uri;
import android.util.Log;

import com.github.mjdev.libaums.fs.UsbFile;

import java.io.File;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.ArrayList;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * Created by N on 6/7/2015.
 * Super class to hold a file, usbfile, smbfile, or any other type of file so that it is easy to
 * do a variety of methods without needing to access the class directly
 */
public class SuperFile implements Serializable {
    private File file;
    private UsbFile usbFile;
    private SmbFile smbFile;
    private String TAG = "amp:SuperFile";

    public SuperFile(File file) {
        this.file = file;
    }
    public SuperFile(UsbFile file) {
        usbFile = file;
    }
    public SuperFile(SmbFile file) {
        smbFile = file;
    }
    public SuperFile() {}
    public SuperFile newSmbFile(String url) throws MalformedURLException {
        smbFile = new SmbFile(url);
        return this;
    }
    public SuperFile newLocalFile(String url) {
        file = new File(url);
        return this;
    }
    public Object getFile() {
        if(file != null)
            return file;
        if(usbFile != null)
            return usbFile;
        if(smbFile != null)
            return smbFile;
        throw new NullPointerException("There is no file type that has been set!");
    }

    public String getName() {
        if(file != null)
            return file.getName();
        else if(usbFile != null)
            return usbFile.getName();
        else if(smbFile != null)
            return smbFile.getName();
        throw new NullPointerException("There is no file type that has been set!");
    }
    public FileSource getFileSource() {
        if(file != null)
            return FileSource.INTERNAL_STORAGE;
        else if(usbFile != null)
            return FileSource.USB;
        else if(smbFile != null)
            return FileSource.SMB;
        throw new NullPointerException("There is no file type that has been set!");
    }
    public String getPath() {
        if(file != null)
            return Uri.fromFile(file).toString();
        else if(usbFile != null) {
            return getPath(usbFile);
        } else if(smbFile != null) {
            return smbFile.getPath();
        }
        throw new NullPointerException("There is no file type that has been set!");
    }
    public String getPath(UsbFile usbFile) {
//        Log.d(TAG, usbFile+"");
        String filepath = usbFile.getName();
        UsbFile parent = usbFile;
        while(parent.getParent() != null) {
            parent = parent.getParent();
//            Log.d(TAG, parent.toString());
            try {
                filepath = parent.getName() + "/" + filepath;
            } catch(Exception e) {
                parent = null;
                break;
            }
        }
        return "usb://"+filepath;
    }

    public long createTime() {
        if(file != null)
            return file.lastModified();
        else if(usbFile != null)
            return usbFile.createdAt();
        else if(smbFile != null) {
            try {
                return smbFile.createTime();
            } catch (SmbException e) {
                e.printStackTrace();
            }
        }
        throw new NullPointerException("There is no file type that has been set!");
    }

}
