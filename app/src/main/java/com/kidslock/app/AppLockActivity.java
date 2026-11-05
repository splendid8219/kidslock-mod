package com.kidslock.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 应用打开门禁：本应用新的启动入口（Launcher）。
 * 打开 APP（点图标 / 点通知）时必须先输入“打开密码”（默认 4321）才能进入设置主页。
 * 若当前处于锁屏状态，则直接转去锁屏答题界面（锁屏状态下不需要打开密码）。
 *
 * 与“家长解锁PIN(1234)”区分：
 *  - 打开密码(4321)：进入本应用设置页的门槛；
 *  - 家长解锁PIN(1234)：在锁屏界面解锁电视的密码；
 *  - 管理员密码(1111)：用于卸载保护（见卸载拦截逻辑）。
 */
public class AppLockActivity extends AppCompatActivity {

    private PrefManager pref;
    private TextView tvPinDisplay;
    private StringBuilder pinInput = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pref = new PrefManager(this);

        // 已锁屏：直接进入锁屏答题界面，不要求打开密码
        if (pref.isLocked()) {
            Intent lockIntent = new Intent(this, LockScreenActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(lockIntent);
            finish();
            return;
        }

        setContentView(R.layout.activity_app_lock);
        hideSystemUI();
        initViews();
    }

    private void initViews() {
        tvPinDisplay = findViewById(R.id.tvPinDisplay);

        int[] pinButtonIds = {
                R.id.btnPin0, R.id.btnPin1, R.id.btnPin2, R.id.btnPin3,
                R.id.btnPin4, R.id.btnPin5, R.id.btnPin6, R.id.btnPin7,
                R.id.btnPin8, R.id.btnPin9
        };
        for (int i = 0; i <= 9; i++) {
            final int digit = i;
            findViewById(pinButtonIds[i]).setOnClickListener(v -> onDigit(digit));
        }
        findViewById(R.id.btnPinClear).setOnClickListener(v -> {
            pinInput.setLength(0);
            updatePinDisplay();
        });
        // 返回键：关闭应用，回到桌面
        findViewById(R.id.btnPinBack).setOnClickListener(v -> finish());

        updatePinDisplay();
    }

    private void onDigit(int digit) {
        if (pinInput.length() < 4) {
            pinInput.append(digit);
            updatePinDisplay();
            if (pinInput.length() == 4) {
                handler.postDelayed(this::checkPassword, 200);
            }
        }
    }

    private void updatePinDisplay() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i < pinInput.length()) sb.append("●");
            else sb.append("○");
            if (i < 3) sb.append("  ");
        }
        tvPinDisplay.setText(sb.toString());
    }

    private void checkPassword() {
        if (pinInput.toString().equals(pref.getAppPassword())) {
            // 验证通过：进入设置主页
            Intent main = new Intent(this, MainActivity.class);
            main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(main);
            finish();
        } else {
            pinInput.setLength(0);
            updatePinDisplay();
            Toast.makeText(this, "打开密码错误", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        // 直接退出应用，避免卡在密码界面
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);
    }
}
