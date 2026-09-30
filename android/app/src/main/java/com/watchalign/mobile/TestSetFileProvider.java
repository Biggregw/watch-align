package com.watchalign.mobile;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Hands test-set files to other apps (alpha66): the exported zip to the share sheet (read), and a
 * file for the camera app to write a new photo into (write). Only two folders of the app's own
 * test-set storage are reachable, and only with a per-use grant (the provider is not exported).
 */
public class TestSetFileProvider extends ContentProvider {
    static final String AUTHORITY="com.watchalign.mobile.testset";

    static Uri uriFor(String area,String name){return Uri.parse("content://"+AUTHORITY+"/"+area+"/"+name);}

    private File fileFor(Uri uri)throws FileNotFoundException{
        List<String> seg=uri.getPathSegments();
        if(seg.size()!=2||!("export".equals(seg.get(0))||"capture".equals(seg.get(0))))throw new FileNotFoundException(uri.toString());
        String name=seg.get(1);
        if(name.contains("/")||name.contains(".."))throw new FileNotFoundException(uri.toString());
        File root=getContext().getExternalFilesDir(null);
        if(root==null)root=getContext().getFilesDir();
        return new File(new File(new File(root,"testset"),"export".equals(seg.get(0))?"exports":"capture"),name);
    }

    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        File f=fileFor(uri);
        List<String> seg=uri.getPathSegments();
        if("export".equals(seg.get(0))&&!"r".equals(mode))throw new FileNotFoundException("read only");
        //noinspection ResultOfMethodCallIgnored
        f.getParentFile().mkdirs();
        return ParcelFileDescriptor.open(f,ParcelFileDescriptor.parseMode(mode));
    }

    @Override public Cursor query(Uri uri,String[] projection,String sel,String[] args,String sort){
        File f;
        try{f=fileFor(uri);}catch(FileNotFoundException e){return null;}
        MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});
        c.addRow(new Object[]{f.getName(),f.length()});
        return c;
    }

    @Override public String getType(Uri uri){
        String p=uri.getLastPathSegment();
        if(p==null)return null;
        return p.endsWith(".zip")?"application/zip":p.endsWith(".jpg")?"image/jpeg":"application/octet-stream";
    }

    @Override public boolean onCreate(){return true;}
    @Override public Uri insert(Uri uri,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
