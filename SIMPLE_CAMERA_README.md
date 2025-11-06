# Simple USB Camera View

This is a simplified Android activity that displays the USB camera feed without any buttons or controls.

## Features

- **Minimal UI**: Only shows the camera view, no buttons or controls
- **Auto-start**: Automatically starts the camera stream when a USB camera is connected
- **Permission handling**: Requests necessary camera permissions when needed
- **USB device detection**: Automatically detects and connects to USB cameras

## Usage

### As a Standalone Activity

The `SimpleCameraActivity` can be launched directly:

```java
Intent intent = new Intent(context, SimpleCameraActivity.class);
startActivity(intent);
```

### With USB Camera Attachment

The activity is configured to launch automatically when a USB camera is attached to the device (if set as the default handler for USB camera devices).

## Implementation Details

### Files Created

1. **SimpleCameraActivity.java** - The main activity class
   - Location: `app/src/main/java/humer/UvcCamera/SimpleCameraActivity.java`
   - Handles USB camera detection, permissions, and streaming

2. **simple_camera_layout.xml** - The minimal layout file
   - Location: `app/src/main/res/layout/simple_camera_layout.xml`
   - Contains only a SurfaceView for displaying the camera feed

3. **AndroidManifest.xml** - Updated to register the new activity
   - Added SimpleCameraActivity registration
   - Configured to respond to USB_DEVICE_ATTACHED events

### Default Camera Settings

The activity uses the following default camera settings:
- **Video Format**: MJPEG
- **Resolution**: 640x480
- **Frame Rate**: ~30 fps (333333 frame interval)
- **Packets Per Request**: 16
- **Active URBs**: 16
- **Max Packet Size**: 3072 bytes

These defaults should work with most USB cameras, but may need adjustment for specific camera models.

### Permissions

The activity handles the following permissions:
- **CAMERA**: Required for Android 8.0+ devices
- **USB_PERMISSION**: Automatically requested when camera is attached

## How It Works

1. **Activity Start**: When the activity starts, it initializes the USB manager and native camera libraries
2. **USB Detection**: Searches for connected USB camera devices
3. **Permission Request**: If a camera is found, requests USB permissions
4. **Camera Initialization**: Once permissions are granted, initializes the camera with default settings
5. **Stream Start**: Automatically starts the camera stream and displays it on the SurfaceView
6. **Auto-cleanup**: Stops the camera stream when the activity is paused or destroyed

## Troubleshooting

If the camera doesn't start automatically:
- Ensure the USB camera is properly connected via OTG cable
- Check that USB permissions are granted
- Verify that the camera is UVC compliant
- Check logcat for error messages (tag: "SimpleCameraActivity")

## Customization

To modify camera settings, edit the default values in `SimpleCameraActivity.java`:

```java
private int imageWidth = 640;           // Image width
private int imageHeight = 480;          // Image height
private int camFrameInterval = 333333;  // Frame rate
private String videoformat = "MJPEG";   // Video format
```

## Differences from Main Activity

Unlike the full-featured main activity, SimpleCameraActivity:
- Has no UI controls or buttons
- Uses fixed camera settings (no manual configuration)
- Automatically starts streaming when connected
- Has a minimal, full-screen display
- Cannot capture photos or record videos
- Cannot adjust camera parameters

This makes it ideal for embedding in other applications or for use cases where only camera preview is needed.
