package com.upaxer.alpha.Fragment.map;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.upaxer.alpha.Fragment.BaseBottomSheetFragment;
import com.upaxer.alpha.R;
import com.upaxer.alpha.Service.MisteryCameraXService;
import com.upaxer.alpha.Service.VideoRecorderService;
import com.upaxer.alpha.Utils.InformationUPXDB;
import com.upaxer.alpha.Utils.MyAdmin;
import com.upaxer.alpha.Utils.ParserUPX;
import com.upaxer.alpha.Utils.UpaxerConstants;
import com.upaxer.alpha.Utils.UpaxerUtils;
import com.upaxer.alpha.beans.LogCloud;
import com.upaxer.alpha.beans.Tarea.utils.Parser;
import com.upaxer.alpha.databinding.BottomVideoRecorderBinding;
import com.upaxer.alpha.models.Usuario;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import static android.content.Context.AUDIO_SERVICE;
import static com.upaxer.alpha.Notifications.Communication.CommunicationFirestoreDB.ACTIVE_SETTINGS;
import static com.upaxer.alpha.Notifications.Communication.CommunicationFirestoreDB.AVAILABLE_MEMORY;
import static com.upaxer.alpha.Notifications.Communication.CommunicationFirestoreDB.BATTERY;
import static com.upaxer.alpha.Notifications.Communication.CommunicationFirestoreDB.MEMORY_RAM;
import static com.upaxer.alpha.Service.VideoRecorderService.ACTION_START;
import static com.upaxer.alpha.Service.VideoRecorderService.ACTION_STOP;
import static com.upaxer.alpha.Utils.UpaxerUtils.SHARED_PREF_CAMERA;
import static com.upaxer.alpha.Utils.UpaxerUtils.checkBatOptimOff;
import static com.upaxer.alpha.Utils.UpaxerUtils.checkPowerSaveOff;
import static com.upaxer.alpha.Utils.UpaxerUtils.getAvailableMemoryRam;
import static com.upaxer.alpha.Utils.UpaxerUtils.getBatteryPercentage;
import static com.upaxer.contentmedia.utils.CameraConstants.BACK_CAMERA;
import static com.upaxer.contentmedia.utils.CameraConstants.FRONT_CAMERA;
import static com.upaxer.contentmedia.utils.CameraConstants.VIDEO_FORMAT;
import static com.upaxer.contentmedia.utils.CameraUtils.displayNotification;
import static com.upaxer.contentmedia.utils.CameraUtils.getFreeSpace;

public class VideoRecorderBottom extends BaseBottomSheetFragment<BottomVideoRecorderBinding> implements VideoRecorderService.Recorder, MisteryCameraXService.CameraXRecorder {

    private final static String FILE_NAME = "name";
    private final static String RECORDING = "recording";

    private String fileName;
    private boolean recording;
    private int camID = Camera.CameraInfo.CAMERA_FACING_BACK;
    private int index;
    private ComponentName compName;
    private DevicePolicyManager deviceManger;

    private boolean activeSettings;
    private int availableMemory;
    private int batteryPercentage;
    private int availableRam;
    private Usuario user;


    public static VideoRecorderBottom newInstance(String filename, boolean recording) {
        VideoRecorderBottom recorder = new VideoRecorderBottom();
        Bundle args = new Bundle();
        args.putString(FILE_NAME, filename);
        args.putBoolean(RECORDING, recording);
        recorder.setArguments(args);
        return recorder;
    }

    @Override
    public int getBindingVariable() {
        return 0;
    }

    @Override
    public int getLayoutId() {
        return R.layout.bottom_video_recorder;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            fileName = getArguments().getString(FILE_NAME);
            recording = getArguments().getBoolean(RECORDING);
        }

        user = Usuario.getUserData(getActivity());
        getDataBinding().btnRecord.setOnClickListener(v -> checkAvailableSpace());

        getDataBinding().radioCameraGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == FRONT_CAMERA) {
                cameraSelfie();
                camID = Camera.CameraInfo.CAMERA_FACING_FRONT;
                getDataBinding().frontCamera.setTextColor(Color.WHITE);
                getDataBinding().backCamera.setTextColor(Color.BLACK);
                getDataBinding().frontText.setTextColor(Color.WHITE);
                getDataBinding().backText.setTextColor(Color.BLACK);
            } else if (checkedId == BACK_CAMERA) {
                camID = Camera.CameraInfo.CAMERA_FACING_BACK;
                getDataBinding().backCamera.setTextColor(Color.WHITE);
                getDataBinding().frontCamera.setTextColor(Color.BLACK);
                getDataBinding().frontText.setTextColor(Color.BLACK);
                getDataBinding().backText.setTextColor(Color.WHITE);
            }
        });

        if (getActivity() != null) {
            deviceManger = (DevicePolicyManager) Objects.requireNonNull(getActivity()).getSystemService(Context.DEVICE_POLICY_SERVICE);
            compName = new ComponentName(getActivity(), MyAdmin.class);
        }
        if (recording) {
            changeViewBottom();
            lockScreen();
        }


    }

    private void getCameraSettings() {
        activeSettings = ParserUPX.booleano(UpaxerUtils.getFromPreferences(getBaseActivity(), UpaxerUtils.SharedPrefTypes.BOOLEAN, SHARED_PREF_CAMERA, ACTIVE_SETTINGS), false);
        availableMemory = ParserUPX.entero(UpaxerUtils.getFromPreferences(getBaseActivity(), UpaxerUtils.SharedPrefTypes.INT, SHARED_PREF_CAMERA, AVAILABLE_MEMORY), 600);
        batteryPercentage = ParserUPX.entero(UpaxerUtils.getFromPreferences(getBaseActivity(), UpaxerUtils.SharedPrefTypes.INT, SHARED_PREF_CAMERA, BATTERY), 60);
        availableRam = ParserUPX.entero(UpaxerUtils.getFromPreferences(getBaseActivity(), UpaxerUtils.SharedPrefTypes.INT, SHARED_PREF_CAMERA, MEMORY_RAM), availableRam);
    }

    private void checkAvailableSpace() {
        try {
            getCameraSettings();
            if (getFreeSpace() > 1024 * 1024 * 200) {
                if (getFreeSpace() > (long) 1024 * 1024 * availableMemory) {
                    checkNewRecordSettings();
                } else {
                    showAlertFreeSpace();
                }
            } else {
                displayNotification(getActivity(), "", getString(com.upaxer.contentmedia.R.string.free_space_low_cam).replace("@M", "vídeo"), getString(com.upaxer.contentmedia.R.string.aceptar));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkNewRecordSettings() {
        if (activeSettings) {
            if (getAvailableMemoryRam(getBaseActivity()) > availableRam) {
                checkWithoutRam();
            } else {
                showAlertFreeRam();
            }
        } else {
            launchRecord();
        }
    }

    private void checkWithoutRam() {
        if (getBatteryPercentage(getBaseActivity()) > batteryPercentage) {
            checkWithoutBattery();
        } else {
            showAlertPlugPhone();
        }
    }

    private void checkWithoutBattery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkBatOptimOff(getBaseActivity())) {
                checkWithoutBatOptim();
            } else {
                showAlertDisableDoze();
            }
        } else {
            launchRecord();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    private void checkWithoutBatOptim() {
        if (checkPowerSaveOff(getBaseActivity())) {
            launchRecord();
        } else {
            showAlertDisablePower();
        }
    }

    private void launchRecord() {
        UpaxerUtils.newLoggedRedesign(getActivity(), "RecordCameraSegundoPlano");
        getDataBinding().btnRecord.setVisibility(View.INVISIBLE);
        getDataBinding().progress.setVisibility(View.VISIBLE);
        deleteExistingFiles(fileName);

        if (Build.VERSION.SDK_INT >= 30){
            MisteryCameraXService.listener = VideoRecorderBottom.this;
            Intent intentRecording = new Intent(getActivity(), MisteryCameraXService.class);
            intentRecording.setAction(ACTION_START);
            intentRecording.putExtra("cameraID", camID);
            intentRecording.putExtra("filename", fileName);
            ContextCompat.startForegroundService(getBaseActivity(), intentRecording);
        }else{
            VideoRecorderService.listener = VideoRecorderBottom.this;
            Intent intentRecording = new Intent(getActivity(), VideoRecorderService.class);
            intentRecording.setAction(ACTION_START);
            intentRecording.putExtra("cameraID", camID);
            intentRecording.putExtra("filename", fileName);
            ContextCompat.startForegroundService(getBaseActivity(), intentRecording);
        }
    }

    private void continueRecording(String name, int index){
        if (Build.VERSION.SDK_INT >= 30){
            MisteryCameraXService.listener = VideoRecorderBottom.this;
            Intent intentRecording = new Intent(getActivity(), MisteryCameraXService.class);
            intentRecording.setAction(ACTION_START);
            intentRecording.putExtra("cameraID", camID);
            intentRecording.putExtra("filename", name);
            intentRecording.putExtra("index", index);
            ContextCompat.startForegroundService(getBaseActivity(), intentRecording);
        }
    }

    private void showAlertFreeSpace() {
        final long SIZE_MB = 1024L * 1024L;
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(Objects.requireNonNull(getActivity())).create();
        alertDialog.setCancelable(false);
        alertDialog.setTitle(getString(com.upaxer.contentmedia.R.string.free_space_title));
        alertDialog.setMessage(getString(com.upaxer.contentmedia.R.string.free_space_msg_cam, getString(com.upaxer.contentmedia.R.string.available_space, String.valueOf(getFreeSpace() / SIZE_MB))));
        alertDialog.setIcon(com.upaxer.contentmedia.R.drawable.ic_lock);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(com.upaxer.contentmedia.R.string.continue_dialog_btn), (dialog, which) -> launchRecord());
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(com.upaxer.contentmedia.R.string.free_space_dialog_btn), (dialog, which) -> startActivityForResult(new Intent(Settings.ACTION_MEMORY_CARD_SETTINGS), 0));
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(com.upaxer.contentmedia.R.string.cancel_btn), (dialog, which) -> dialog.dismiss());
        new InformationUPXDB().saveLog(new LogCloud(user != null ? user.getId() : -1, LogCloud.DEVICE, "Warning Video, storage not enough " + getFreeSpace()));
        alertDialog.show();
    }

    private void deleteExistingFiles(String filename) {
        try {
            String name = filename.replace(VIDEO_FORMAT, "");
            boolean saveInternal = Parser.booleano(UpaxerUtils.getFromPreferences(getBaseActivity(), UpaxerUtils.SharedPrefTypes.BOOLEAN, UpaxerUtils.SHARED_PREF_UPX, UpaxerConstants.SAVE_INTERNAL), false);
            List<File> fileList = saveInternal ? UpaxerUtils.getListFiles(getBaseActivity(), name, false) : UpaxerUtils.getListFilesSameParent(getBaseActivity(), name, false);
            for (File f : fileList) {
                f.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onEndRecord(boolean isRecording, boolean isLowSpace, boolean haveMbVideo) {
        UpaxerUtils.hideProgress();
        if (haveMbVideo) {
            if (isAdded()) {
                if (isRecording) {
                    changeViewBottom();
                    lockScreen();
                } else {
                    getDataBinding().btnRecord.setText(getString(R.string.grabar));
                    getDataBinding().btnRecord.setOnClickListener(recListener);
                    hideComponents(false);
                    NotificationManager mNotificationManager = (NotificationManager) getBaseActivity().getSystemService(Context.NOTIFICATION_SERVICE);
                    mNotificationManager.cancelAll();
                    playAlertSound();

                }
                if (isLowSpace) {
                    UpaxerUtils.displayNotification(getActivity(), "", getString(R.string.free_space_stop_video).replace("@H", getHora()).replace("@F", "vídeo"), getString(R.string.aceptar));
                }
                getDataBinding().progress.setVisibility(View.INVISIBLE);
                getDataBinding().btnRecord.setVisibility(View.VISIBLE);
            }
        } else {
            showAlertVideoNoHaveMbEnough();
        }
    }


    private boolean cameraSelfie() {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int nCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < nCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                return true;
            }
        }
        UpaxerUtils.displayNotification(getBaseActivity(), "Atencion", "El dispositivo no cuenta con camara frontal", "Aceptar");
        return false;
    }


    private void changeViewBottom() {
        getDataBinding().summaryContent.setText(getString(R.string.text_body_recorder));
        getDataBinding().btnRecord.setText(getString(R.string.detener_grabacion));
        getDataBinding().btnRecord.setOnClickListener(stopRecListener);
        hideComponents(true);
    }

    private void hideComponents(boolean isHide) {
        getDataBinding().layoutCamera.setVisibility(isHide ? View.GONE : View.VISIBLE);
        getDataBinding().textView4.setVisibility(isHide ? View.GONE : View.VISIBLE);
        setCancelable(!isHide);
    }

    private void lockScreen() {
        boolean active = deviceManger.isAdminActive(compName);
        if (active) {
            deviceManger.lockNow();
            deviceManger.removeActiveAdmin(compName);
        } else {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.explain_to_lock_screen));
            startActivityForResult(intent, UpaxerConstants.RESULT_ADMIN);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case UpaxerConstants.RESULT_ADMIN:
                if (resultCode == Activity.RESULT_OK) {
                    boolean active = deviceManger.isAdminActive(compName);
                    if (active) {
                        deviceManger.lockNow();
                        deviceManger.removeActiveAdmin(compName);
                    }
                }
        }
    }

    public String getHora() {
        Date date = new Date();
        SimpleDateFormat hourFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        hourFormat.setTimeZone(TimeZone.getDefault());
        return hourFormat.format(date);
    }

    private View.OnClickListener recListener = v -> checkAvailableSpace();

    private View.OnClickListener stopRecListener = v -> {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(Objects.requireNonNull(getActivity()));
        builder.setCancelable(false);
        builder.setMessage(getString(R.string.detener_grabacion_conf).replace("@@", "vídeo"));
        builder.setPositiveButton(getString(R.string.detener_grabacion), (dialog, id) -> {
            getDataBinding().btnRecord.setVisibility(View.INVISIBLE);
            getDataBinding().btnRecord.setVisibility(View.VISIBLE);

            if (Build.VERSION.SDK_INT >= 30){
                MisteryCameraXService.listener = VideoRecorderBottom.this;
                Intent intentRecording = new Intent(getActivity(), MisteryCameraXService.class);
                intentRecording.setAction(ACTION_STOP);
                ContextCompat.startForegroundService(getBaseActivity(), intentRecording);
            }else{
                VideoRecorderService.listener = VideoRecorderBottom.this;
                Intent intentRecording = new Intent(getActivity(), VideoRecorderService.class);
                intentRecording.setAction(ACTION_STOP);
                ContextCompat.startForegroundService(getBaseActivity(), intentRecording);
            }

            dialog.dismiss();
            dismiss();
        });
        builder.setNegativeButton(getString(R.string.continuar), (dialog, which) -> dialog.dismiss());
        builder.show();
    };

    private void showAlertVideoNoHaveMbEnough() {
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(Objects.requireNonNull(getActivity())).create();
        alertDialog.setCancelable(false);
        alertDialog.setTitle(getString(R.string.tamano_inadecuado));
        alertDialog.setMessage(getString(R.string.tam_inadecuado_));
        alertDialog.setIcon(R.drawable.btn_alerta);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.aceptar), (dialog, which) -> dialog.dismiss());
        new InformationUPXDB().saveLog(new LogCloud(user != null ? user.getId() : -1, LogCloud.MULTIMEDIA_INFO, "Warning Video, video mb to send low"));
        alertDialog.show();
    }

    private void showAlertFreeRam() {
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(Objects.requireNonNull(getActivity())).create();
        alertDialog.setCancelable(false);
        alertDialog.setTitle(getString(R.string.few_ram_available));
        alertDialog.setMessage(getString(R.string.avoid_open_other_apps));
        alertDialog.setIcon(R.drawable.btn_alerta);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.liberar_espacio), (dialog, which) -> {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.continuar), (dialog, which) -> checkWithoutRam());
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.cancelar), (dialog, which) -> dialog.dismiss());
        new InformationUPXDB().saveLog(new LogCloud(user != null ? user.getId() : -1, LogCloud.DEVICE, "Warning Video, low ram " + getAvailableMemoryRam(getBaseActivity())));
        alertDialog.show();
    }

    private void showAlertPlugPhone() {
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(Objects.requireNonNull(getActivity())).create();
        alertDialog.setCancelable(false);
        alertDialog.setTitle(getString(R.string.insufficient_battery));
        alertDialog.setMessage(getString(R.string.charge_it_until, batteryPercentage));
        alertDialog.setIcon(R.drawable.btn_alerta);
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.continuar), (dialog, which) -> checkWithoutBattery());
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.aceptar), (dialog, which) -> dialog.dismiss());
        new InformationUPXDB().saveLog(new LogCloud(user != null ? user.getId() : -1, LogCloud.DEVICE, "Warning Video, battery not enough " + getBatteryPercentage(getBaseActivity())));
        alertDialog.show();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void showAlertDisableDoze() {
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(Objects.requireNonNull(getActivity())).create();
        alertDialog.setCancelable(false);
        alertDialog.setTitle(getString(R.string.battery_optim_mode));
        alertDialog.setMessage(getString(R.string.disable_battery_optim_mode));
        alertDialog.setIcon(R.drawable.btn_alerta);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.disable), (dialog, which) -> {
            disableDozeMode();
        });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.continuar), (dialog, which) -> checkWithoutBatOptim());
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.cancelar), (dialog, which) -> dialog.dismiss());
        new InformationUPXDB().saveLog(new LogCloud(user != null ? user.getId() : -1, LogCloud.MULTIMEDIA_INFO, "Warning Video, battery optimization mode on"));
        alertDialog.show();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void disableDozeMode() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        startActivity(intent);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    private void showAlertDisablePower() {
        AlertDialog alertDialog = new MaterialAlertDialogBuilder(Objects.requireNonNull(getActivity())).create();
        alertDialog.setCancelable(false);
        alertDialog.setTitle(getString(R.string.energy_save_mode));
        alertDialog.setMessage(getString(R.string.disable_energy_save_mode));
        alertDialog.setIcon(R.drawable.btn_alerta);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.disable), (dialog, which) -> {
            disablePowerMode();
        });
        alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.continuar), (dialog, which) -> launchRecord());
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.cancelar), (dialog, which) -> dialog.dismiss());
        new InformationUPXDB().saveLog(new LogCloud(user != null ? user.getId() : -1, LogCloud.MULTIMEDIA_INFO, "Warning Video, energy save mode on"));
        alertDialog.show();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    private void disablePowerMode() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_BATTERY_SAVER_SETTINGS);
        startActivity(intent);
    }

    private void playAlertSound() {
        final AudioManager mAudioManager = (AudioManager) getBaseActivity().getSystemService(AUDIO_SERVICE);
        final int originalVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
        MediaPlayer mediaPlayer = MediaPlayer.create(getBaseActivity(), com.upaxer.contentmedia.R.raw.knock_knock);
        mediaPlayer.start();
        mediaPlayer.setOnCompletionListener(mp ->
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
        );
    }


    @Override
    public void onEndRecordCameraX(boolean isRecording, boolean isLowSpace, boolean haveMbVideo) {
        UpaxerUtils.hideProgress();
        if (haveMbVideo) {
            if (isAdded()) {
                if (isRecording) {
                    changeViewBottom();
                    lockScreen();
                } else {
                    getDataBinding().btnRecord.setText(getString(R.string.grabar));
                    getDataBinding().btnRecord.setOnClickListener(recListener);
                    hideComponents(false);
                    NotificationManager mNotificationManager = (NotificationManager) getBaseActivity().getSystemService(Context.NOTIFICATION_SERVICE);
                    mNotificationManager.cancelAll();
                    playAlertSound();
                }
                if (isLowSpace) {
                    UpaxerUtils.displayNotification(getActivity(), "", getString(R.string.free_space_stop_video).replace("@H", getHora()).replace("@F", "vídeo"), getString(R.string.aceptar));
                }
                getDataBinding().progress.setVisibility(View.INVISIBLE);
                getDataBinding().btnRecord.setVisibility(View.VISIBLE);
            }
        } else {
            showAlertVideoNoHaveMbEnough();
        }
    }

    @Override
    public void onStop(String name, int index) {
        if (!name.equals("")){
            fileName = name;
            this.index = index;
            MisteryCameraXService.listener = VideoRecorderBottom.this;
            Intent intentRecording = new Intent(getActivity(), MisteryCameraXService.class);
            intentRecording.setAction(ACTION_STOP);
            intentRecording.putExtra("ContinueRecording", true);
            ContextCompat.startForegroundService(getBaseActivity(), intentRecording);
        }else {
            MisteryCameraXService.listener = VideoRecorderBottom.this;
            Intent intentRecording = new Intent(getActivity(), MisteryCameraXService.class);
            intentRecording.setAction(ACTION_STOP);
            ContextCompat.startForegroundService(getBaseActivity(), intentRecording);
        }
    }

    @Override
    public void reRecord() {
        continueRecording(fileName, index);
    }
}
