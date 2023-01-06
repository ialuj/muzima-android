package com.muzima.adapters.media;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.muzima.R;
import com.muzima.api.model.Media;
import com.muzima.api.model.MediaCategory;

import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class MediaRecyclerViewAdapter extends RecyclerView.Adapter<MediaRecyclerViewAdapter.ViewHolder> {
    private HashMap<MediaCategory, List<Media>> mediaCategoryListHashMap;
    Context context;
    int groupPosition;
    List<MediaCategory> mediaCategoryList;

    public MediaRecyclerViewAdapter(Context context, HashMap<MediaCategory, List<Media>> child, int groupPosition, List<MediaCategory> mediaCategory) {
        this.mediaCategoryListHashMap = child;
        this.context=context;
        this.groupPosition=groupPosition;
        this.mediaCategoryList=mediaCategory;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        CardView cardView;
        ImageView imageView;

        public ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.itemTextView);
            cardView=itemView.findViewById(R.id.cardView);
            imageView=itemView.findViewById(R.id.mediaImageView);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.media_card_view, parent, false);
        MediaRecyclerViewAdapter.ViewHolder vh = new MediaRecyclerViewAdapter.ViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, final int position) {
        Media media = (Media) getChild(groupPosition, position);
        final String mediaName = media.getName();
        holder.name.setText(mediaName);

        String mimeType = media.getMimeType();
        String PATH = Objects.requireNonNull(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)).getAbsolutePath();
        File file = new File(PATH + "/"+media.getName()+"."+mimeType.substring(mimeType.lastIndexOf("/") + 1));

        if(StringUtils.substringBefore(mimeType, "/").equals("image")){
            Uri uri = Uri.fromFile(file);
            Bitmap bitmap = null;
            try {
                bitmap = MediaStore.Images.Media.getBitmap(context.getApplicationContext().getContentResolver(), uri);
                Bitmap thumbnail = ThumbnailUtils.extractThumbnail(bitmap,400,400);
                holder.imageView.setImageBitmap(thumbnail);
            } catch (IOException e) {
                e.printStackTrace();
                Bitmap thumbnail = getDefaultThumbNail();
                holder.imageView.setImageBitmap(thumbnail);
            }
        }else if(mimeType.substring(mimeType.lastIndexOf("/") + 1).equals("mp4")){
            Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(file.getAbsolutePath(), MediaStore.Video.Thumbnails.MICRO_KIND);
            Bitmap thumbnail = ThumbnailUtils.extractThumbnail(bitmap,400,400);
            holder.imageView.setImageBitmap(thumbnail);
        }else if(mimeType.substring(mimeType.lastIndexOf("/") + 1).equals("pdf")) {
            Bitmap thumbnail = getPDFThumbnail(file);
            holder.imageView.setImageBitmap(thumbnail);
        }else{
            Bitmap thumbnail = getDefaultThumbNail();
            holder.imageView.setImageBitmap(thumbnail);
        }


        holder.cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startMediaDisplayActivity(media);
            }
        });
    }

    @Override
    public int getItemCount() {
        return this.mediaCategoryListHashMap.get(this.mediaCategoryList.get(groupPosition)).size();
    }

    public Object getChild(int groupPosition, int childPosititon) {
        return this.mediaCategoryListHashMap.get(this.mediaCategoryList.get(groupPosition)).get(childPosititon);
    }

    private void startMediaDisplayActivity(Media media) {
        String mimeType = media.getMimeType();
        String PATH = Objects.requireNonNull(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)).getAbsolutePath();
        File file = new File(PATH + "/"+media.getName()+"."+mimeType.substring(mimeType.lastIndexOf("/") + 1));
        if(!file.exists()){
            Toast.makeText(context, context.getString(R.string.info_no_media_not_available), Toast.LENGTH_LONG).show();
        } else {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri fileUri = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".provider", file);
            intent.setData(fileUri);
            List<ResolveInfo> resInfoList = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resInfoList) {
                context.grantUriPermission(context.getPackageName() + ".provider", fileUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        }
    }

    private Bitmap getDefaultThumbNail(){
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(),
                R.drawable.splash_background);
        Bitmap thumbnail = ThumbnailUtils.extractThumbnail(bitmap,400,400);
        return thumbnail;
    }

    private Bitmap getPDFThumbnail(File PDFFile){
        Bitmap bitmap = null;
        try {
            PdfRenderer renderer = new PdfRenderer(ParcelFileDescriptor.open(PDFFile, ParcelFileDescriptor.MODE_READ_ONLY));
            final int pageCount = renderer.getPageCount();
            if(pageCount>0){
                PdfRenderer.Page page = renderer.openPage(0);
                int width = context.getResources().getDisplayMetrics().densityDpi / 72 * page.getWidth();
                int height = context.getResources().getDisplayMetrics().densityDpi / 72 * page.getHeight();
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();
            }else{
                bitmap = getDefaultThumbNail();
            }
            renderer.close();
        } catch (Exception ex) {
            ex.printStackTrace();
            bitmap = getDefaultThumbNail();
        }
        Bitmap thumbnail = ThumbnailUtils.extractThumbnail(bitmap,400,400);
        return thumbnail;
    }
}