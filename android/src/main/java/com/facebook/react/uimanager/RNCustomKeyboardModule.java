
package com.facebook.react.uimanager;

import android.app.Application;
import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import android.widget.RelativeLayout;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactRootView;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.uimanager.events.EventDispatcher;
import com.facebook.react.uimanager.PixelUtil;
import com.facebook.react.views.textinput.ReactEditText;
import com.facebook.react.ReactInstanceManager;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

public class RNCustomKeyboardModule extends ReactContextBaseJavaModule {
    final long KEYBOARD_ANI_DURATION = 50;

    private static final String TAG = "RNCustomKeyboardModule";
    private static final int DEFAULT_TIMEOUT = 200;
    private static final int RETRY_COUNT = 5;
    private final int TAG_ID = 0xdeadbeaf;
    private int installRetryRef = 0;
    private final ReactApplicationContext reactContext;
    public static ReactInstanceManager rnInstanceManager;

    private Method setShowSoftInputOnFocusMethod;
    private Method setSoftInputShownOnFocusMethod;

    private ReactRootView rootView = null;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    private Map<Integer, Integer> mKeyboardToMaxInputLength = new HashMap<>();
    private Map<Integer, Integer> mKeyboardToHeight = new HashMap<>();

    public RNCustomKeyboardModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        initReflectMethod();
    }

    private void initReflectMethod () {
        Class<ReactEditText> cls = ReactEditText.class;
        try {
            setShowSoftInputOnFocusMethod = cls.getMethod("setShowSoftInputOnFocus", boolean.class);
            setShowSoftInputOnFocusMethod.setAccessible(true);
        } catch (Exception e) {
            Log.i(TAG, "initReflectMethod 1  err=" + e.getMessage());
        }
        try {
            setSoftInputShownOnFocusMethod = cls.getMethod("setSoftInputShownOnFocus", boolean.class);
            setSoftInputShownOnFocusMethod.setAccessible(true);
        } catch (Exception e) {
            Log.i(TAG, "initReflectMethod 2  err=" + e.getMessage());
        }
    }

    private ReactEditText getEditById(int id) throws IllegalViewOperationException{
        try {
            UIViewOperationQueue uii = this.getReactApplicationContext().getNativeModule(UIManagerModule.class).getUIImplementation().getUIViewOperationQueue();
            return (ReactEditText) uii.getNativeViewHierarchyManager().resolveView(id);
        }
        catch(Exception e) {
            Log.i(TAG, "failed to getEditById " + e.getMessage());
        }

        return null;
    }

    private void showKeyboard (final Activity activity, final ReactEditText edit, final int tag) {
        final RNCustomKeyboardModule self = this;

        final ResultReceiver receiver = new ResultReceiver(mHandler) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
//                Log.i(TAG, "showKeyboard ------ resultCode=" + resultCode);
                if (resultCode == InputMethodManager.RESULT_UNCHANGED_HIDDEN || resultCode == InputMethodManager.RESULT_HIDDEN) {
                    final Rect visibleViewArea = new Rect();
                    rootView.getWindowVisibleDisplayFrame(visibleViewArea);
                    final Integer keyboardHeight = mKeyboardToHeight.get(tag);

                    if (null != keyboardHeight ) {
                        WritableMap params = Arguments.createMap();
                        WritableMap coordinates = Arguments.createMap();
                        coordinates.putDouble("screenY", PixelUtil.toDIPFromPixel(visibleViewArea.bottom-keyboardHeight));
                        coordinates.putDouble("screenX", PixelUtil.toDIPFromPixel(visibleViewArea.left));
                        coordinates.putDouble("width", PixelUtil.toDIPFromPixel(visibleViewArea.width()));
                        coordinates.putDouble("height", keyboardHeight);
                        params.putMap("endCoordinates", coordinates);
                        params.putInt("duration", (int)KEYBOARD_ANI_DURATION);
                        self.sendEvent("keyboardWillShow", params);
                    }

                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            View keyboard = (View) edit.getTag(TAG_ID);
                            if (keyboard.getParent() == null && edit.isFocused()) {
//                                Log.i(TAG, "showKeyboard +++++++++");
                                activity.addContentView(keyboard, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                                if (null != keyboardHeight){
                                    final View rootView = self.rootView.getRootView();

                                    WritableMap params = Arguments.createMap();
                                    WritableMap coordinates = Arguments.createMap();
                                    coordinates.putDouble("screenY", PixelUtil.toDIPFromPixel(visibleViewArea.bottom-keyboardHeight));
                                    coordinates.putDouble("screenX", PixelUtil.toDIPFromPixel(visibleViewArea.left));
                                    coordinates.putDouble("width", PixelUtil.toDIPFromPixel(visibleViewArea.width()));
                                    coordinates.putDouble("height", keyboardHeight);
                                    params.putMap("endCoordinates", coordinates);
                                    self.sendEvent("keyboardDidShow", params);
                                }
                            }
                        }
                    }, KEYBOARD_ANI_DURATION);
                }
            }
        };

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                InputMethodManager im = ((InputMethodManager) getReactApplicationContext().getSystemService(Activity.INPUT_METHOD_SERVICE));
                im.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0, receiver);
            }
        });
    }

    /**
     * 禁止Edittext弹出软件盘，光标依然正常显示。
     */
    public void disableShowSoftInput(ReactEditText editText) {
        try {
            setShowSoftInputOnFocusMethod.invoke(editText, false);
        } catch (Exception e) {
            Log.i(TAG, "disableShowSoftInput 1  err=" + e.getMessage());
        }

        try {
            setSoftInputShownOnFocusMethod.invoke(editText, false);
        } catch (Exception e) {
            Log.i(TAG, "disableShowSoftInput 2  err=" + e.getMessage());
        }
    }

    private void sendFocusChangeListener (ReactEditText editText, boolean hasFocus) {
        if (editText != null) {
            EventDispatcher eventDispatcher =
                    reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();
            if (hasFocus) {
                eventDispatcher.dispatchEvent(
                        new ReactTextInputFocusEvent(
                                editText.getId()));
            } else {
                eventDispatcher.dispatchEvent(
                        new ReactTextInputBlurEvent(
                                editText.getId()));

                eventDispatcher.dispatchEvent(
                        new ReactTextInputEndEditingEvent(
                                editText.getId(),
                                editText.getText().toString()));
            }
        }
    }

    private void setEditTextTagAndListener (final ReactEditText edit, final int tag, final String type) {
        final Activity activity = getCurrentActivity();
        if (edit == null || activity == null) {
            Log.e(TAG, "setEditTextListener error null, edit=" + edit);
            return;
        }

        disableShowSoftInput(edit);
        edit.setTag(TAG_ID, createCustomKeyboard(activity, tag, type));
        edit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(final View v, boolean hasFocus) {
                Log.i(TAG, "onFocusChange hasFocus=" + hasFocus);
                sendFocusChangeListener(edit, hasFocus);
                if (hasFocus) {
                    showKeyboard(activity, edit, tag);
                } else {
                    mHandler.removeCallbacksAndMessages(null);
                    View keyboard = (View) edit.getTag(TAG_ID);
                    if (keyboard != null && keyboard.getParent() != null) {
                        ((ViewGroup) keyboard.getParent()).removeView(keyboard);

                        WritableMap params = Arguments.createMap();
                        params.putInt("duration", (int)KEYBOARD_ANI_DURATION);
                        sendEvent("keyboardWillHide", params);

                        sendEvent("keyboardDidHide", null);
                    }
                }
            }
        });
    }

    @ReactMethod
    public void install(final int tag, final String type, final int maxInputLength, final int height) {
        installRetryRef = 0;
        mKeyboardToMaxInputLength.put(tag, maxInputLength);
        mKeyboardToHeight.put(tag, height);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
               doInstall(tag, type);
            }
        });
    }

    private void doInstall (final int tag, final String type) {
        try {
            ReactEditText edit = getEditById(tag);
            setEditTextTagAndListener(edit, tag, type);
            installRetryRef = 0;
        } catch (IllegalViewOperationException e) {
//            Log.i(TAG, "IllegalViewOperationException");
            if (installRetryRef < RETRY_COUNT) {
                installRetryRef++;
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        doInstall(tag, type);
                    }
                }, DEFAULT_TIMEOUT);
            }
        }
    }

    private View createCustomKeyboard(Activity activity, int tag, String type) {
        RelativeLayout layout = new RelativeLayout(activity);
        rootView = new ReactRootView(this.getReactApplicationContext());
//        rootView.setBackgroundColor(Color.WHITE);

        Bundle bundle = new Bundle();
        bundle.putInt("tag", tag);
        bundle.putString("type", type);

        Application application = activity.getApplication();
        rnInstanceManager = ((ReactApplication)application).getReactNativeHost().getReactInstanceManager();
        rootView.startReactApplication(
                rnInstanceManager,
                "CustomKeyboard",
                bundle);

        final float scale = activity.getResources().getDisplayMetrics().density;
        RelativeLayout.LayoutParams lParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(216*scale));
        lParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
        layout.addView(rootView, lParams);
//        activity.addContentView(layout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return layout;
    }

    @ReactMethod
    public void uninstall(final int tag) {
        installRetryRef = 0;
        mHandler.removeCallbacksAndMessages(null);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final Activity activity = getCurrentActivity();
                final ReactEditText edit = getEditById(tag);
                if (edit == null) {
                    return;
                }

                edit.setTag(TAG_ID, null);
            }
        });
    }

    @ReactMethod
    public void getSelectionRange(final int tag, final Callback callback) {
        WritableMap responseMap = Arguments.createMap();
        try {
            ReactEditText edit = getEditById(tag);
            int start = Math.max(edit.getSelectionStart(), 0);
            int end = Math.max(edit.getSelectionEnd(), 0);
            Editable text = edit.getText();
            if (text != null) {
                responseMap.putString("text", text.toString());
                responseMap.putInt("start", start);
                responseMap.putInt("end", end);
            } else {
                Log.e(TAG, "getSelectionRange text=null");
                responseMap.putNull("text");
            }
            callback.invoke(responseMap);
        } catch (Exception e) {
            Log.e(TAG, "getSelectionRange error=" + e.getMessage());
            responseMap.putNull("text");
            callback.invoke(responseMap);
        }
    }

    @ReactMethod
    public void insertText(final int tag, final String text) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final Activity activity = getCurrentActivity();
                final ReactEditText edit = getEditById(tag);
                if (edit == null) {
                    return;
                }

                Integer maxLength = mKeyboardToMaxInputLength.get(tag);
                if (maxLength != null && edit.getText().length() >= maxLength ){
                    return;
                }

                int start = Math.max(edit.getSelectionStart(), 0);
                int end = Math.max(edit.getSelectionEnd(), 0);
                edit.getText().replace(Math.min(start, end), Math.max(start, end),
                        text, 0, text.length());
            }
        });
    }

    @ReactMethod
    public void backSpace(final int tag) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final Activity activity = getCurrentActivity();
                final ReactEditText edit = getEditById(tag);
                if (edit == null) {
                    return;
                }

                int start = Math.max(edit.getSelectionStart(), 0);
                int end = Math.max(edit.getSelectionEnd(), 0);
                if (start != end) {
                    edit.getText().delete(start, end);
                } else if (start > 0) {
                    edit.getText().delete(start - 1, end);
                }
            }
        });
    }

    @ReactMethod
    public void doDelete(final int tag) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final ReactEditText edit = getEditById(tag);
                if (edit == null) {
                    return;
                }

                int start = Math.max(edit.getSelectionStart(), 0);
                int end = Math.max(edit.getSelectionEnd(), 0);
                if (start != end) {
                    edit.getText().delete(start, end);
                } else if (start > 0) {
                    edit.getText().delete(start, end + 1);
                }
            }
        });
    }

    @ReactMethod
    public void moveLeft(final int tag) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final Activity activity = getCurrentActivity();
                final ReactEditText edit = getEditById(tag);
                if (edit == null) {
                    return;
                }

                int start = Math.max(edit.getSelectionStart(), 0);
                int end = Math.max(edit.getSelectionEnd(), 0);
                if (start != end) {
                    edit.setSelection(start, start);
                } else {
                    edit.setSelection(start - 1, start - 1);
                }
            }
        });
    }

    @ReactMethod
    public void moveRight(final int tag) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final Activity activity = getCurrentActivity();
                final ReactEditText edit = getEditById(tag);
                if (edit == null) {
                    return;
                }

                int start = Math.max(edit.getSelectionStart(), 0);
                int end = Math.max(edit.getSelectionEnd(), 0);
                if (start != end) {
                    edit.setSelection(end, end);
                } else if (start > 0) {
                    edit.setSelection(end + 1, end + 1);
                }
            }
        });
    }

    @ReactMethod
    public void deleteLeftAll(final int tag) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final Activity activity = getCurrentActivity();
                final ReactEditText edit = getEditById(tag);
                if (edit == null) {
                    return;
                }

                int end = Math.max(edit.getSelectionEnd(), 0);
                if (0 != end) {
                    edit.getText().delete(0, end);
                }
            }
        });
    }

    @ReactMethod
    public void hideKeyboard(final int tag) {
        WritableMap params = Arguments.createMap();
        params.putInt("duration", (int)KEYBOARD_ANI_DURATION);
        sendEvent("keyboardWillHide", params);

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final Activity activity = getCurrentActivity();
                final ReactEditText edit = getEditById(tag);
                if (edit == null) {
                    return;
                }

                View keyboard = (View) edit.getTag(TAG_ID);
                if (keyboard != null && keyboard.getParent() != null) {
                    ((ViewGroup) keyboard.getParent()).removeView(keyboard);
                    sendEvent("keyboardDidHide", null);
                }

                edit.clearFocus();
            }
        });
    }

    @ReactMethod
    public void switchSystemKeyboard(final int tag) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final Activity activity = getCurrentActivity();
                final ReactEditText edit = getEditById(tag);
                if (edit == null) {
                    return;
                }

                View keyboard = (View) edit.getTag(TAG_ID);
                if (keyboard.getParent() != null) {
                    ((ViewGroup) keyboard.getParent()).removeView(keyboard);
                }
                mHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        ((InputMethodManager) getReactApplicationContext().getSystemService(Activity.INPUT_METHOD_SERVICE)).showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
                    }
                });
            }
        });
    }

    @Override
    public String getName() {
      return "CustomKeyboard";
    }

    /* package */ void sendEvent(String eventName, @Nullable WritableMap params) {
        Log.e("#CustomKeyboard", eventName + " try");
    if (rnInstanceManager != null) {
        rnInstanceManager.getCurrentReactContext()
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);

        }
    }
}
