package com.kidslock.app;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 设备管理员接收器。
 * 用途：仅用于“防误卸载门槛”——激活后，系统会禁止直接卸载本应用，
 * 必须先到“设置 → 安全 → 设备管理员”中取消本应用的管理员权限才能卸载。
 * 不强制任何设备策略（不改锁屏密码、不锁设备等）。
 */
public class KidsLockDeviceAdmin extends DeviceAdminReceiver {

    private static final String TAG = "KidsLockDeviceAdmin";

    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.i(TAG, "Device admin enabled");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Log.i(TAG, "Device admin disabled");
    }
}
