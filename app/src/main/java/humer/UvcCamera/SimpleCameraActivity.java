/*
Copyright 2019 Peter Stoiber

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA

Please contact the author if you need another license.
This Repository is provided "as is", without warranties of any kind.
*/

package humer.UvcCamera;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import com.serenegiant.usb.IFrameCallback;
import com.sun.jna.Pointer;

import java.util.HashMap;

import humer.UvcCamera.JNA_I_LibUsb.JNA_I_LibUsb;

public class SimpleCameraActivity extends Activity {

    private static final String TAG = "SimpleCameraActivity";
    private static final String ACTION_USB_PERMISSION = "humer.uvc_camera.USB_PERMISSION";
    private static final int SC_VIDEOCONTROL = 0x01;
    private static final int SC_VIDEOSTREAMING = 0x02;
    private static final int REQUEST_PERMISSION_CAMERA = 1;

    static {
        System.loadLibrary("usb1.0");
        System.loadLibrary("jpeg9");
        System.loadLibrary("yuv");
        System.loadLibrary("uvc");
        System.loadLibrary("uvc_preview");
        System.loadLibrary("Uvc_Support");
    }

    // Native methods
    public native long nativeCreate(long camera_pointer);
    public native int PreviewPrepareStream(long camera_pointer, final Surface surface, final IFrameCallback callback);
    public native int PreviewStartStream(long camera_pointer);
    public native int PreviewStopStream(long camera_pointer);

    // USB
    private UsbManager usbManager;
    private UsbDevice camDevice = null;
    private UsbDeviceConnection camDeviceConnection;
    private PendingIntent mPermissionIntent;

    // Camera
    private SurfaceView mUVCCameraView;
    private Surface mPreviewSurface;
    private long mNativePtr;
    private volatile boolean libusb_is_initialized = false;
    private boolean moveToNative = false;

    // Camera Values (using defaults from Main.java)
    private int camStreamingAltSetting = 0;
    private int camFormatIndex = 1;
    private int camFrameIndex = 1;
    private int camFrameInterval = 333333;
    private int packetsPerRequest = 16;
    private int maxPacketSize = 3072;
    private int imageWidth = 640;
    private int imageHeight = 480;
    private int activeUrbs = 16;
    private String videoformat = "MJPEG";
    private int camStreamingEndpointAdress = 0;
    private int fd = 0;

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    camDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (camDevice != null) {
                            log("USB permission granted");
                            startCameraStream();
                        }
                    } else {
                        log("USB permission denied for device " + camDevice);
                        displayMessage("USB permission denied");
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.simple_camera_layout);
        
        // Hide navigation and status bars
        this.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        mUVCCameraView = (SurfaceView) findViewById(R.id.simpleSurfaceView);
        mPreviewSurface = mUVCCameraView.getHolder().getSurface();
        mUVCCameraView.getHolder().addCallback(mSurfaceViewCallback);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);
        } else {
            mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_UPDATE_CURRENT);
        }
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(mUsbReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mUsbReceiver, filter);
        }

        // Check camera permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!checkCameraPermission()) {
                requestCameraPermission();
            }
        }

        // Initialize native pointer
        mNativePtr = nativeCreate(0);
        log("mNativePtr = " + mNativePtr);

        // Try to find and connect to camera
        findAndConnectCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCamera();
        try {
            unregisterReceiver(mUsbReceiver);
        } catch (Exception e) {
            log("Exception unregistering receiver: " + e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCamera();
    }

    private boolean checkCameraPermission() {
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        return permissionCheck == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSION_CAMERA);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                log("Camera permission granted");
            } else {
                displayMessage("Camera permission denied");
            }
        }
    }

    private void findAndConnectCamera() {
        try {
            camDevice = findCameraDevice();
            if (camDevice == null) {
                camDevice = checkDeviceVideoClass();
                if (camDevice == null) {
                    log("No USB camera device found");
                    displayMessage("No USB camera found. Please connect a USB camera.");
                    return;
                }
            }
            if (!usbManager.hasPermission(camDevice)) {
                log("Requesting USB permissions");
                usbManager.requestPermission(camDevice, mPermissionIntent);
            } else {
                log("USB permission already granted");
                startCameraStream();
            }
        } catch (Exception e) {
            log("Error finding camera: " + e.getMessage());
            displayMessage("Error finding camera");
        }
    }

    private UsbDevice findCameraDevice() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        log("USB devices count = " + deviceList.size());
        for (UsbDevice usbDevice : deviceList.values()) {
            if (moveToNative) {
                if (checkDeviceHasVideoControlInterface(usbDevice)) {
                    return usbDevice;
                }
            } else {
                log("USB device \"" + usbDevice.getDeviceName() + "\": " + usbDevice);
                if (checkDeviceHasVideoStreamingInterface(usbDevice)) {
                    return usbDevice;
                }
            }
        }
        return null;
    }

    private UsbDevice checkDeviceVideoClass() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice usbDevice : deviceList.values()) {
            if (usbDevice.getDeviceClass() == 14 && usbDevice.getDeviceSubclass() == 2) {
                moveToNative = true;
                return usbDevice;
            } else if (usbDevice.getDeviceClass() == 239 && usbDevice.getDeviceSubclass() == 2) {
                moveToNative = true;
                return usbDevice;
            }
            if (checkDeviceHasVideoControlInterface(usbDevice)) {
                return usbDevice;
            }
        }
        return null;
    }

    private boolean checkDeviceHasVideoStreamingInterface(UsbDevice usbDevice) {
        return getVideoStreamingInterface(usbDevice) != null;
    }

    private boolean checkDeviceHasVideoControlInterface(UsbDevice usbDevice) {
        return getVideoControlInterface(usbDevice) != null;
    }

    private UsbInterface getVideoControlInterface(UsbDevice usbDevice) {
        return findInterface(usbDevice, UsbConstants.USB_CLASS_VIDEO, SC_VIDEOCONTROL, false);
    }

    private UsbInterface getVideoStreamingInterface(UsbDevice usbDevice) {
        return findInterface(usbDevice, UsbConstants.USB_CLASS_VIDEO, SC_VIDEOSTREAMING, true);
    }

    private UsbInterface findInterface(UsbDevice usbDevice, int interfaceClass, int interfaceSubclass, boolean withEndpoint) {
        int interfaces = usbDevice.getInterfaceCount();
        for (int i = 0; i < interfaces; i++) {
            UsbInterface usbInterface = usbDevice.getInterface(i);
            if (usbInterface.getInterfaceClass() == interfaceClass && 
                usbInterface.getInterfaceSubclass() == interfaceSubclass && 
                (!withEndpoint || usbInterface.getEndpointCount() > 0)) {
                return usbInterface;
            }
        }
        return null;
    }

    private void startCameraStream() {
        if (camDevice == null) {
            displayMessage("No camera device");
            return;
        }

        try {
            camDeviceConnection = usbManager.openDevice(camDevice);
            if (camDeviceConnection == null) {
                displayMessage("Failed to open camera device");
                return;
            }

            if (fd == 0) fd = camDeviceConnection.getFileDescriptor();

            if (camStreamingEndpointAdress == 0) {
                camStreamingEndpointAdress = JNA_I_LibUsb.INSTANCE.fetchTheCamStreamingEndpointAdress(
                        new Pointer(mNativePtr), camDeviceConnection.getFileDescriptor());
            }

            int lowAndroid = 0;
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                lowAndroid = 1;
            }

            JNA_I_LibUsb.INSTANCE.set_the_native_Values(new Pointer(mNativePtr), fd, packetsPerRequest, 
                    maxPacketSize, activeUrbs, camStreamingAltSetting, camFormatIndex, camFrameIndex, 
                    camFrameInterval, imageWidth, imageHeight, camStreamingEndpointAdress, 1, 
                    videoformat, 0, 0, lowAndroid);

            JNA_I_LibUsb.INSTANCE.initStreamingParms(new Pointer(mNativePtr), fd);
            JNA_I_LibUsb.INSTANCE.listDeviceUvc(new Pointer(mNativePtr), camDeviceConnection.getFileDescriptor());

            // Prepare and start stream
            mPreviewSurface = mUVCCameraView.getHolder().getSurface();
            int result = PreviewPrepareStream(mNativePtr, mPreviewSurface, new IFrameCallback() {
                @Override
                public void onFrame(final byte[] frame) {
                    // Frame received
                }
            });

            if (result == 0) {
                result = PreviewStartStream(mNativePtr);
                if (result == 0) {
                    libusb_is_initialized = true;
                    log("Camera stream started successfully");
                } else {
                    displayMessage("Failed to start stream. Result: " + result);
                }
            } else {
                displayMessage("Failed to prepare stream. Result: " + result);
            }

        } catch (Exception e) {
            log("Error starting camera stream: " + e.getMessage());
            displayMessage("Error starting camera stream");
        }
    }

    private void stopCamera() {
        if (libusb_is_initialized && mNativePtr != 0) {
            try {
                PreviewStopStream(mNativePtr);
                JNA_I_LibUsb.INSTANCE.stopStreaming(new Pointer(mNativePtr));
                libusb_is_initialized = false;
                log("Camera stream stopped");
            } catch (Exception e) {
                log("Error stopping camera: " + e.getMessage());
            }
        }
    }

    private final SurfaceHolder.Callback mSurfaceViewCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(final SurfaceHolder holder) {
            log("surfaceCreated");
            Canvas canvas = mUVCCameraView.getHolder().lockCanvas();
            if (canvas != null) {
                canvas.drawColor(Color.GRAY, PorterDuff.Mode.SRC);
                mUVCCameraView.getHolder().unlockCanvasAndPost(canvas);
            }
        }

        @Override
        public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
            if ((width == 0) || (height == 0)) return;
            log("surfaceChanged");
            mPreviewSurface = holder.getSurface();
        }

        @Override
        public void surfaceDestroyed(final SurfaceHolder holder) {
            log("surfaceDestroyed");
            mPreviewSurface = null;
        }
    };

    private void displayMessage(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(SimpleCameraActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void log(String msg) {
        Log.i(TAG, msg);
    }
}
