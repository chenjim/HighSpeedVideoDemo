package cn.zhengshang.highspeedvideodemo;

/**
 * Created by troels on 2/16/16.
 */

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.Range;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CaptureHighSpeedVideoModeFragment extends Fragment
        implements View.OnClickListener {
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    private static final String TAG = "HSVFragment";
    static final int REQUEST_VIDEO_PERMISSIONS = 1;
    static final String FRAGMENT_DIALOG = "dialog";

    static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    protected static CameraInfo config;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * Button to record video
     */
    private AppCompatCheckBox mRecButtonVideo;
    private Chronometer mChronometer;
    private TextView mInfo;

    /**
     * A refernce to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * A reference to the current {@link CameraCaptureSession} for
     * preview.
     */
    private CameraCaptureSession mPreviewSessionHighSpeed;


    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
             startPreview();
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };

    /**
     * The {@link Size} of camera preview.
     */
    private Size mPreviewSize;

    private Size mVideoSize;

    private String mNextVideoFilePath;
    /**
     * Camera preview.
     */
    private CaptureRequest.Builder mPreviewBuilder;
    /**
     * MediaRecorder
     */
    private MediaRecorder mMediaRecorder;


    private List<Surface> surfaces = new ArrayList<>();

    /**
     * Whether the app is recording video now
     */
    private boolean mIsRecordingVideo = false;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
     */
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
//            startPreview();
            mCameraOpenCloseLock.release();
            if (null != mTextureView) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            Logger.e("");
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            Logger.e(error);
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };
    private String cameraId;

    public static CaptureHighSpeedVideoModeFragment newInstance() {
        return new CaptureHighSpeedVideoModeFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.camera, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mTextureView = view.findViewById(R.id.texture);
        mChronometer = view.findViewById(R.id.chronometer);
        mInfo = view.findViewById(R.id.info);
        mRecButtonVideo = view.findViewById(R.id.video_record);
        mRecButtonVideo.setOnClickListener(this);

    }

    @Override
    public void onResume() {
        surfaces.clear();
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        if (mIsRecordingVideo) {
            stopRecordingVideo();
        }
        closeCamera();
        deleteEmptyFIle(mNextVideoFilePath);
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.video_record) {
            if (mIsRecordingVideo) {
                stopRecordingVideo();
                startPreview();
            } else {
                startRecordingVideo();
            }
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets whether you should show UI with rationale for requesting permissions.
     *
     * @param permissions The permissions your app wants to request.
     * @return Whether you can show permission rationale UI.
     */
    private boolean shouldShowRequestPermissionRationale(String[] permissions) {
        for (String permission : permissions) {
            if (shouldShowRequestPermissionRationale(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Requests permissions needed for recording video.
     */
    private void requestVideoPermissions() {
        if (shouldShowRequestPermissionRationale(VIDEO_PERMISSIONS)) {
            ConfirmationDialog.newInstance(R.string.permission_request)
                    .setOkListener(new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            requestPermissions(VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
                        }
                    })
                    .setCancelListener(new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            getActivity().finish();
                        }
                    })
                    .show(getFragmentManager(), "TAG");
        } else {
            requestPermissions(VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        if (requestCode == REQUEST_VIDEO_PERMISSIONS) {
            if (grantResults.length == VIDEO_PERMISSIONS.length) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        ErrorDialog.newInstance(getString(R.string.permission_request))
                                .show(getChildFragmentManager(), FRAGMENT_DIALOG);
                        break;
                    }
                }
            } else {
                ErrorDialog.newInstance(getString(R.string.permission_request))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(getActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void openCamera(int width, int height) {
        if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            requestVideoPermissions();
            return;
        }
        final Activity activity = getActivity();
        if (null == activity || activity.isFinishing()) {
            return;
        }
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            Log.d(TAG, "tryAcquire");
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            cameraId = Objects.requireNonNull(manager).getCameraIdList()[0];
            if (config != null) {
                cameraId = config.getCameraId();
            }

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map == null) {
                ErrorDialog.newInstance(getString(R.string.open_failed_of_map_null))
                        .show(getFragmentManager(), "TAG");
                return;
            }

            List<Size> highSpeedSizes = new ArrayList<>();

            for (Range<Integer> fpsRange : map.getHighSpeedVideoFpsRanges()) {
                if (fpsRange.getLower().equals(fpsRange.getUpper())) {
                    for (android.util.Size size : map.getHighSpeedVideoSizesFor(fpsRange)) {
                        Size videoSize = new Size(size.getWidth(), size.getHeight());
                        //if (videoSize.hasHighSpeedCamcorder(Integer.parseInt(cameraId))) {
                            videoSize.setFps(fpsRange.getUpper());
                            Logger.d(TAG, "Support HighSpeed video recording add :" + videoSize);
                            highSpeedSizes.add(videoSize);
                       // }
                    }
                }
            }

            for (android.util.Size size : map.getHighSpeedVideoSizes()) {
                boolean isSupport = false;
                if (size.getWidth() == 720 && size.getHeight() == 480) {
                    isSupport = CamcorderProfile.hasProfile(Integer.parseInt(cameraId), CamcorderProfile.QUALITY_HIGH_SPEED_480P);
                } else if (size.getWidth() == 1280 && size.getHeight() == 720) {
                    isSupport = CamcorderProfile.hasProfile(Integer.parseInt(cameraId), CamcorderProfile.QUALITY_HIGH_SPEED_720P);
                } else if (size.getWidth() == 1920 && (size.getHeight() == 1080 || size.getHeight() == 1088)) {
                    isSupport = CamcorderProfile.hasProfile(Integer.parseInt(cameraId), CamcorderProfile.QUALITY_HIGH_SPEED_1080P);
                } else if (size.getWidth() == 3840 && size.getHeight() == 2160) {
                    //2160p (3840 x 2160)
                    isSupport = CamcorderProfile.hasProfile(Integer.parseInt(cameraId), CamcorderProfile.QUALITY_HIGH_SPEED_2160P);
                } else if (size.getWidth() == 4096 && size.getHeight() == 2160) {
                    isSupport = CamcorderProfile.hasProfile(Integer.parseInt(cameraId), CamcorderProfile.QUALITY_HIGH_SPEED_4KDCI);
                }
                Logger.w(TAG, size+",isSupport:" + isSupport);
                Range<Integer>[] ranges = map.getHighSpeedVideoFpsRangesFor(size);
                if (ranges != null && ranges.length > 0) {
                    for (Range<Integer> range : ranges) {
                        Logger.w(TAG, "range:" + range.toString() + ",size:" + size);
                    }
                }
            }

            if (highSpeedSizes.isEmpty()) {
                ErrorDialog.newInstance(getString(R.string.open_failed_of_not_support_high_speed))
                        .show(getFragmentManager(), "TAG");
                return;
            }

            Collections.sort(highSpeedSizes);

            StringBuilder hsBuilder = new StringBuilder();
            for (Size size : highSpeedSizes) {
                hsBuilder.append(size).append(",");
            }
            Logger.w(TAG, "highSpeed  support size and fps:" + hsBuilder);

            mVideoSize = highSpeedSizes.get(highSpeedSizes.size() - 1);
//            mVideoSize = highSpeedSizes.get(8);
            if (config != null) {
                mVideoSize = config.getFpsSize();
            }

            mPreviewSize = mVideoSize;

            Logger.d(TAG,"selected:" + mVideoSize);

            mInfo.setText(getString(R.string.video_info, mVideoSize.getWidth(), mVideoSize.getHeight(), mVideoSize.getFps()));

            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            configureTransform(width, height);
            mMediaRecorder = new MediaRecorder();
//            mMediaFormat = new MediaFormat();
            if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            manager.openCamera(cameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Toast.makeText(activity, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            activity.finish();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    private void closeCamera() {

        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (Exception ignored) {

        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Start the camera preview.
     */
    private void startPreview() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }

        if (mMediaRecorder != null) {
            //这个东西在上一次打开预览的时候,已经设置成了prepare,但是没有使用. 所以需要reset
            mMediaRecorder.reset();
        }

        try {
            surfaces.clear();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            setUpMediaRecorder();
            surfaces.add(mMediaRecorder.getSurface());
            mPreviewBuilder.addTarget(mMediaRecorder.getSurface());

            CameraCaptureSession.StateCallback stateCallback = new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSessionHighSpeed = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Activity activity = getActivity();
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            };

            if (HSConfig.ENABLE) {
                Logger.d(mTextureView.getWidth() + "x" + mTextureView.getHeight());
                mCameraDevice.createConstrainedHighSpeedCaptureSession(surfaces, stateCallback, mBackgroundHandler);
            } else {
                mCameraDevice.createCaptureSession(surfaces, stateCallback, mBackgroundHandler);
            }
            Logger.d("createConstrainedHighSpeedCaptureSession");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            HandlerThread thread = new HandlerThread("CameraHighSpeedPreview");
            thread.start();


            setUpCaptureRequestBuilder(mPreviewBuilder);

            if (HSConfig.ENABLE) {
                List<CaptureRequest> mPreviewBuilderBurst = ((CameraConstrainedHighSpeedCaptureSession) mPreviewSessionHighSpeed)
                        .createHighSpeedRequestList(mPreviewBuilder.build());
                mPreviewSessionHighSpeed.setRepeatingBurst(mPreviewBuilderBurst, null, mBackgroundHandler);
            } else {
                mPreviewSessionHighSpeed.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        int fps = mVideoSize.getFps();
        Range<Integer> fpsRange = Range.create(fps, fps);
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
    }

    /**
     * Configures the necessary {@link Matrix} transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
        Logger.d(viewWidth + "," + viewHeight);
    }

    //    private MediaFormat mMediaFormat;
    private void setUpMediaRecorder() throws IOException {
        final Activity activity = getActivity();
        if (null == activity) {
            return;
        }

        CamcorderProfile profile = mVideoSize.getCamcorderProfile();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setProfile(profile);
        boolean isHfr = true;
        if (isHfr) {
            mMediaRecorder.setCaptureRate(mVideoSize.getFps());
            mMediaRecorder.setVideoFrameRate(30);
            int bt = (int) (mVideoSize.getWidth() * mVideoSize.getHeight() * 30 * 0.1F);
            mMediaRecorder.setVideoEncodingBitRate(bt);
            Logger.d("setUpMediaRecorder," + bt / 1024);
        }
        mNextVideoFilePath = getVideoFile();
        mMediaRecorder.setOutputFile(mNextVideoFilePath);


        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int orientation = ORIENTATIONS.get(rotation);
        if (config != null && "1".equals(config.getCameraId())) {
            orientation += 180;
        }
        mMediaRecorder.setOrientationHint(orientation);
        mMediaRecorder.prepare();
    }

    /**
     * This method chooses where to save the video and what the name of the video file is
     *
     * @return path + filename
     */
    private String getVideoFile() {
        File dirFile = requireActivity().getExternalFilesDir(null);
        dirFile = new File("sdcard/hs");
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
        String date = new SimpleDateFormat("yyMMdd_HHmmss", Locale.CHINA).format(new Date());
        String sizeFps = "" + mVideoSize.getWidth() + "x" + mVideoSize.getHeight() + "_" + mVideoSize.getFps();
        return dirFile.getPath() + "/" + Build.DEVICE + "_HS_" + date + "_" + sizeFps + ".mp4";
    }

    /**
     * 添加记录到媒体库
     */
    private void addToMediaStore() {
        Intent sanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri uri = Uri.fromFile(new File(mNextVideoFilePath));
        sanIntent.setData(uri);
        getContext().sendBroadcast(sanIntent);
    }

    private void startRecordingVideo() {

        mIsRecordingVideo = true;
        mRecButtonVideo.setText(R.string.stop);
        mMediaRecorder.start();
        mChronometer.setBase(SystemClock.elapsedRealtime());
        mChronometer.start();
        mChronometer.setVisibility(View.VISIBLE);
    }

    private void stopRecordingVideo() {
        // UI
        mIsRecordingVideo = false;
        mRecButtonVideo.setText(R.string.start);
        mChronometer.stop();
        mChronometer.setVisibility(View.GONE);
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        Activity activity = getActivity();
        if (null != activity) {
            Toast.makeText(activity, "Video saved: " + mNextVideoFilePath,
                    Toast.LENGTH_LONG).show();
        }
        Logger.w(TAG, "[saved] = " + mNextVideoFilePath);

        addToMediaStore();
    }


    /**
     * 如果传入的文件为空文件(文件存在,但大小为0kb),则删除掉
     * 这些0长度的文件, 是因为MediaRecorder调用了prepare之后没有开始录制生成的. 他们数据无效数据
     *
     * @param filePath 文件路径
     */
    public static void deleteEmptyFIle(String filePath) {
        if (TextUtils.isEmpty(filePath)) {
            return;
        }
        File file = new File(filePath);
        if (file.isFile() && file.length() <= 0) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
