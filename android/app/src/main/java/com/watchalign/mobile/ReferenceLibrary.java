package com.watchalign.mobile;
import android.content.Context;import android.graphics.Bitmap;import java.io.*;import java.util.*;
/** User-confirmed genuine photos stored locally and grouped by exact model. */
final class ReferenceLibrary {static int save(Context c,String model,List<Bitmap> images)throws Exception{File d=new File(c.getFilesDir(),"references/"+model);if(!d.exists()&&!d.mkdirs())throw new IOException("Cannot create reference folder");int n=0;for(Bitmap b:images){if(b==null)continue;File f=new File(d,System.currentTimeMillis()+"-"+n+".jpg");try(FileOutputStream o=new FileOutputStream(f)){if(b.compress(Bitmap.CompressFormat.JPEG,95,o))n++;}}return n;}private ReferenceLibrary(){}}
