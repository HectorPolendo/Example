package com.upaxer.alpha.Service;

import static com.upaxer.alpha.Utils.UpaxerConstants.NOTIFICATION_FOREGROUND;
import static com.upaxer.alpha.Utils.UpaxerConstants.NOTIFICATION_VIDEO_RECORDER;
import static com.upaxer.alpha.Utils.UpaxerUtils.getFreeSpace;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.VideoCapture;
import androidx.camera.core.impl.VideoCaptureConfig;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleService;
import com.google.common.util.concurrent.ListenableFuture;
import com.upaxer.alpha.R;
import com.upaxer.alpha.Utils.InformationUPXDB;
import com.upaxer.alpha.Utils.UpaxerConstants;
import com.upaxer.alpha.Utils.UpaxerUtils;
import com.upaxer.alpha.beans.LogCloud;
import com.upaxer.alpha.beans.Tarea.utils.Parser;
import com.upaxer.alpha.models.Usuario;
import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class MisteryCameraXService extends LifecycleService implements VideoCapture.OnVideoSavedCallback {
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ProcessCameraProvider cameraProvider;
    private CameraSelector cameraSelector;
    private VideoCapture videoCapture;
    private File outputFle;
    private MediaPlayer mediaPlayer;
    private String file_name;
    private long fileSizeInKB;
    private long MAX_DURATION = 1024 * 1024 * 600;
    private long DIFERENCE;
    private int cameraID;
    private int index = 1;
    private boolean lowSpace = false;
    private boolean isFinish = false;
    private Usuario user;
    int scale = -1;
    int level = -1;
    int voltage = -1;
    int temp = -1;

    final Handler handler = new Handler(Looper.getMainLooper());
    final Handler readStorage = new Handler();

    public static final String ACTION_STOP = "action_stop";
    public static final String ACTION_START = "action_start";

    public static CameraXRecorder listener;

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        try {
            user = Usuario.getUserData(getApplicationContext());
            if (intent != null && intent.getExtras() != null && intent.hasExtra("cameraID")) {
                cameraID = intent.getExtras().getInt("cameraID");
                if (file_name == null)
                    file_name = intent.getExtras().getString("filename");
                if (intent.getExtras().containsKey("index")) {
                    index = intent.getExtras().getInt("index");
                    file_name = file_name.replaceAll(("part" + (index-1)), ("part" + index));
                }

                if (outputFle == null && file_name != null) {
                    outputFle = getOutputMediaFile(file_name, UpaxerConstants.VIDEO_FORMAT);
                }

                cameraProviderFuture = ProcessCameraProvider.getInstance(this);

                cameraProviderFuture.addListener(() -> {
                    try {
                        cameraProvider = cameraProviderFuture.get();
                        initCamera();
                        startCameraX();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }, getExecutor());

                createBroadcast();
            } else if(intent != null && intent.getExtras() != null && intent.hasExtra("ContinueRecording")){
                stopSelf();
                listener.reRecord();
            }else {
                isFinish = true;
                stopRecording();
                stopSelf();
            }
        } catch (Exception ex) {
            new InformationUPXDB().saveLog(new LogCloud(user != null ? user.getId() : -1, LogCloud.CAMERA, "Start Command " + ex.getMessage()));
            ex.printStackTrace();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    @SuppressLint("RestrictedApi")
    private void initCamera() {
        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(cameraID == 1 ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                .build();

        videoCapture = new VideoCapture.Builder()
                .setVideoFrameRate(30)
                .setAudioBitRate(64 * 1000)
                .setBitRate(2048 * 1000)
                .setTargetResolution(new Size(480, 720))
                .build();
    }

    private void startCameraX() {
        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, videoCapture);

        if (outputFle != null)
            recordVideo();
    }

    @SuppressLint("RestrictedApi")
    private void stopRecording() {
        videoCapture.stopRecording();
        handler.removeCallbacksAndMessages(null);
        readStorage.removeCallbacksAndMessages(null);
        if (listener != null) {
            if (outputFle != null) {
                long fileSizeInBytes = outputFle.length();
                fileSizeInKB = fileSizeInBytes / 1024;

                if (fileSizeInKB > 100) {
                    listener.onEndRecordCameraX(false, lowSpace, true);
                } else {
                    listener.onEndRecordCameraX(false, lowSpace, false);
                }
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private void recordVideo() {
        if (videoCapture != null) {
            File vidFile = new File(outputFle.getAbsolutePath());

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            videoCapture.startRecording(
                    new VideoCapture.OutputFileOptions.Builder(vidFile).build(), getExecutor(), this);

            launchNotification();

            if (listener != null)
                listener.onEndRecordCameraX(true, lowSpace, true);

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    new InformationUPXDB().saveLog(new LogCloud(user != null ? user.getId() : -1, LogCloud.CAMERA, "Maximum video length"));
                    relaunchVideoRecord();
                }
            }, 20000);

            if(getFreeSpace() < MAX_DURATION){
                MAX_DURATION = 1024 * 1024 * 200;
            }

            DIFERENCE = getFreeSpace() - MAX_DURATION;

            readStorage.postDelayed(new Runnable() {
                @SuppressLint("RestrictedApi")
                @Override
                public void run() {
                    if(getFreeSpace() <= DIFERENCE){
                        new InformationUPXDB().saveLog(new LogCloud(user != null ? user.getId() : -1, LogCloud.CAMERA, "Maximum video length"));
                        relaunchVideoRecord();
                    }else{
                        readStorage.postDelayed(this, 1000);
                    }
                }
            }, 1000);
        }
    }

    private void relaunchVideoRecord() {
        if (index == 1)
            file_name = file_name + "_part" + (index);

        if (getFreeSpace() > (1024 * 1024 * 205)) {
            lowSpace = false;
        } else {
            lowSpace = true;
        }

        stopRecording();
    }

    private File getOutputMediaFile(String type, String extension) {
        File mediaFile = null;
        try {
            boolean saveInternal = Parser.booleano(UpaxerUtils.getFromPreferences(this, UpaxerUtils.SharedPrefTypes.BOOLEAN, UpaxerUtils.SHARED_PREF_UPX, UpaxerConstants.SAVE_INTERNAL), false);
            File storageDir = new File(saveInternal ? getApplicationContext().getFilesDir().getAbsolutePath() + File.separator + UpaxerConstants.MULTIMEDIA_DIR : UpaxerConstants.getTKPInnerDir(getApplicationContext(), UpaxerConstants.MULTIMEDIA_DIR));
            if (!storageDir.exists())
                storageDir.mkdir();
            mediaFile = new File(storageDir + File.separator + type + extension);
            mediaFile.createNewFile();
        } catch (Exception e) {
            e.printStackTrace();
            new InformationUPXDB().saveLog(new LogCloud(user != null ? user.getId() : -1, LogCloud.CAMERA, "Output" + e.getMessage()));

        }
        return mediaFile;

    }

    private void createBroadcast() {
        registerReceiver(mStopLowBattery, new IntentFilter(Intent.ACTION_BATTERY_LOW));
    }

    private BroadcastReceiver mStopLowBattery = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);

            NotificationManager notif = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            Notification notify = new Notification.Builder(getApplicationContext())
                    .setContentTitle("UPAXER")
                    .setContentText(getResources().getString(R.string.content_n))
                    .setStyle(new Notification.BigTextStyle().bigText(getResources().getString(R.string.content_n)))
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setSmallIcon(R.drawable.icon_low_battery)
                    .build();
            notify.flags |= Notification.FLAG_AUTO_CANCEL;
            notif.notify(0, notify);


        }
    };

    private void launchNotification() {
        Intent intent = new Intent(getApplicationContext(), VideoRecorderService.class);
        intent.setAction(ACTION_STOP);
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 2, intent, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String NOTIFICATION_CHANNEL_ID = getPackageName();
            String channelName = "Upaxer";
            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(chan);

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
            Notification notification = notificationBuilder.setOngoing(true)
                    .setContentTitle(UpaxerConstants.NOTIFICATION_UPAXER)
                    .setPriority(NotificationManager.IMPORTANCE_NONE)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setSmallIcon(R.mipmap.zanahoria)
                    .build();
            startForeground(NOTIFICATION_FOREGROUND, notification);
            manager.cancel(NOTIFICATION_FOREGROUND);
        } else {

            Notification.Builder builder = new Notification.Builder(MisteryCameraXService.this)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText("Detener Grabación")
                    .setOngoing(true)
                    .setContentIntent(pendingIntent);
            startForeground(NOTIFICATION_VIDEO_RECORDER, builder.build());
        }
    }

    private void playAlertSound() {
        final AudioManager mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        final int originalVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
        mediaPlayer = MediaPlayer.create(this, com.upaxer.contentmedia.R.raw.knock_knock);
        mediaPlayer.start();
        mediaPlayer.setOnCompletionListener(mp ->
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
        );
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
        UpaxerUtils.vibrate(getApplicationContext(), 1500);
        new InformationUPXDB().saveLog(new LogCloud(user != null ? user.getId() : -1, LogCloud.CAMERA, "Continue recording, available memory"));
        index++;
        if(!isFinish && getFreeSpace() > (1024 * 1024 * 205)){
            new InformationUPXDB().saveLog(new LogCloud(user != null ? user.getId() : -1, LogCloud.CAMERA, "Continue recording, available memory"));
            listener.onStop(file_name, index);
        }else{
            new InformationUPXDB().saveLog(new LogCloud(user != null ? user.getId() : -1, LogCloud.CAMERA, "Stop recording, insufficient memory"));
        }
        unregisterReceiver(mStopLowBattery);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
        playAlertSound();
        unregisterReceiver(mStopLowBattery);
        new InformationUPXDB().saveLog(new LogCloud(user != null ? user.getId() : -1, LogCloud.CAMERA, message));
        stopSelf();
        listener.reRecord();
    }

    public interface CameraXRecorder {
        void onEndRecordCameraX(boolean isRecording, boolean isLowSpace, boolean haveMbVideo);
        void onStop(String fileName, int index);
        void reRecord();
    }


}
