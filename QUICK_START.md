# Quick Start Guide: SimpleCameraActivity

## Overview
A minimal Android activity that displays USB camera feed without any buttons. Auto-starts on camera connection.

## Features
- 🎥 Full-screen camera view
- 🔌 Auto-detects USB cameras
- 🔐 Auto-requests permissions
- ▶️ Auto-starts streaming
- 🚫 Zero buttons or controls

## File Locations

```
app/src/main/
├── AndroidManifest.xml                     [MODIFIED]
├── java/humer/UvcCamera/
│   └── SimpleCameraActivity.java          [NEW - 411 lines]
└── res/
    └── layout/
        └── simple_camera_layout.xml        [NEW - 13 lines]

Documentation:
├── SIMPLE_CAMERA_README.md                 [NEW - User guide]
├── IMPLEMENTATION_SUMMARY.md               [NEW - Technical details]
└── QUICK_START.md                          [NEW - This file]
```

## Usage

### Option 1: Launch Directly
```java
Intent intent = new Intent(this, SimpleCameraActivity.class);
startActivity(intent);
```

### Option 2: Auto-launch on USB Connect
1. Connect USB camera to Android device
2. Android prompts: "Open with SimpleCameraActivity?"
3. Select "Always" or "Just once"
4. Camera stream starts automatically

### Option 3: Add to Main Activity
In `Main.java`, add a button:
```java
public void openSimpleCamera(View view) {
    Intent intent = new Intent(this, SimpleCameraActivity.class);
    startActivity(intent);
}
```

In `layout_main.xml`, add:
```xml
<Button
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="Simple Camera View"
    android:onClick="openSimpleCamera" />
```

## How It Works

```
1. User launches activity
        ↓
2. Initialize USB manager & native libraries
        ↓
3. Search for USB camera devices
        ↓
4. Found camera? → Request USB permission
        ↓                     ↓ (denied)
   (granted)                  ↓
        ↓                Toast error
5. Check camera permission (Android 8+)
        ↓                     ↓ (denied)
   (granted)                  ↓
        ↓                Toast error
6. Initialize camera with defaults
        ↓
7. Prepare stream (surface + callback)
        ↓
8. Start stream automatically
        ↓
9. Display video on SurfaceView
```

## Default Settings

| Setting | Value | Description |
|---------|-------|-------------|
| Resolution | 640x480 | Image dimensions |
| Format | MJPEG | Video compression |
| FPS | ~30 | Frame rate |
| Packets/Request | 16 | USB transfer size |
| Active URBs | 16 | Parallel transfers |
| Max Packet Size | 3072 | Bytes per packet |

## Customization

Edit `SimpleCameraActivity.java` (lines 75-83):

```java
private int imageWidth = 640;           // Change resolution
private int imageHeight = 480;          // Change resolution
private int camFrameInterval = 333333;  // Change FPS
private String videoformat = "MJPEG";   // Change format
```

**Common resolutions:**
- 320x240 (QVGA)
- 640x480 (VGA) ← default
- 800x600 (SVGA)
- 1280x720 (HD)
- 1920x1080 (Full HD)

**Common frame intervals:**
- 666666 = 15 fps
- 333333 = 30 fps ← default
- 166666 = 60 fps

## Troubleshooting

### Camera not detected
```
Check:
☑ USB camera is plugged in
☑ Using OTG cable/adapter
☑ Camera is UVC compliant
☑ USB debugging enabled
```

### Permission denied
```
Solution:
1. Go to Settings → Apps → [Your App]
2. Permissions → Enable Camera
3. Reconnect USB camera
```

### Black screen
```
Try:
1. Disconnect and reconnect camera
2. Close and reopen app
3. Check logcat: adb logcat | grep SimpleCameraActivity
4. Try different resolution settings
5. Ensure camera works in other UVC apps
```

### Stream fails to start
```
Modify settings in SimpleCameraActivity.java:
- Lower resolution (e.g., 320x240)
- Reduce packetsPerRequest to 8
- Reduce activeUrbs to 8
- Change videoformat to "YUY2"
```

## Testing

### Quick Test
```bash
# Connect device via ADB
adb devices

# View logs
adb logcat | grep SimpleCameraActivity

# Launch activity
adb shell am start -n your.package.name/.SimpleCameraActivity
```

### What to Look For
```
✅ "USB devices count = X" (X > 0)
✅ "USB permission granted"
✅ "Camera stream started successfully"

❌ "No USB camera device found"
❌ "USB permission denied"
❌ "Failed to start stream"
```

## Log Messages

| Log Message | Meaning |
|------------|---------|
| "mNativePtr = [number]" | Native library initialized |
| "USB devices count = X" | Found X USB devices |
| "Requesting USB permissions" | Asking for USB access |
| "USB permission granted" | User approved USB |
| "Camera permission granted" | User approved camera |
| "Camera stream started successfully" | Working! |
| "No USB camera found" | No camera connected |
| "Failed to start stream. Result: X" | Stream error code X |

## Integration Examples

### Example 1: Embedded in Fragment
```java
public class CameraFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Intent intent = new Intent(getActivity(), SimpleCameraActivity.class);
        startActivity(intent);
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }
}
```

### Example 2: Service Integration
```java
public class CameraService extends Service {
    public void startCamera() {
        Intent intent = new Intent(this, SimpleCameraActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
```

### Example 3: Background Launch
```java
// From broadcast receiver or background task
Intent intent = new Intent(context, SimpleCameraActivity.class);
intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
context.startActivity(intent);
```

## Comparison

| Feature | SimpleCameraActivity | Main Activity |
|---------|---------------------|---------------|
| Buttons | None | Many |
| Auto-start | Yes | No |
| Configuration | Fixed | Manual |
| Lines of code | 411 | 2400+ |
| UI complexity | Minimal | Complex |
| Photo/Video | No | Yes |
| Settings | No | Yes |
| Use case | Preview only | Full control |

## Build & Install

```bash
# Build debug APK
./gradlew assembleDebug

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch
adb shell am start -n humer.UvcCamera/.SimpleCameraActivity
```

## Requirements

- Android 4.1+ (API 16+)
- USB OTG support
- UVC-compliant USB camera
- Camera permission (Android 8+)
- USB permission

## Support

For issues or questions:
1. Check `SIMPLE_CAMERA_README.md` for user guide
2. Check `IMPLEMENTATION_SUMMARY.md` for technical details
3. Enable logcat filtering: `adb logcat SimpleCameraActivity:I *:S`
4. Review original project documentation in `README.md`

---

**Quick Reference Card:**

```
┌─────────────────────────────────────┐
│   SimpleCameraActivity Cheat Sheet  │
├─────────────────────────────────────┤
│ Launch: new Intent(SimpleCameraActivity.class) │
│ Layout: simple_camera_layout.xml    │
│ Default: 640x480 MJPEG @ 30fps      │
│ Auto: Detection ✓ Permissions ✓ Start ✓ │
│ Buttons: None                        │
│ Logs: SimpleCameraActivity          │
└─────────────────────────────────────┘
```
