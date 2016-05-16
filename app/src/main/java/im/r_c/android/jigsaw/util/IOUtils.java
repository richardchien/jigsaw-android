package im.r_c.android.jigsaw.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.support.annotation.AnyRes;
import android.support.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * IOUtils
 * Created by richard on 16/5/5.
 */
public class IOUtils {
    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void deleteFile(String path) {
        File cacheImgFile = new File(path);
        cacheImgFile.delete();
    }

    public static Uri getUriOfResource(@NonNull Context context, @AnyRes int resId) {
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE +
                "://" + context.getResources().getResourcePackageName(resId)
                + '/' + context.getResources().getResourceTypeName(resId)
                + '/' + context.getResources().getResourceEntryName(resId) );
    }
}
