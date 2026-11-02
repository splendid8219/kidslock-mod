package com.kidslock.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * 设置主界面。
 * 家长可以在这里配置：开机自启、观看时长、家长PIN码，
 * 以及【题目设置】：学段（小学/初中/高中）、年级、科目模式（随机混合/单科）。
 * 还可以开始/停止计时、立即锁屏、查看剩余时间。
 */
public class MainActivity extends AppCompatActivity {

    private PrefManager pref;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvStatus;
    private TextView tvRemaining;
    private Button btnAutoStart;
    private TextView tvWatchTime;
    private TextView tvUnlockRule;

    // 题目设置控件
    private Button btnStage1, btnStage2, btnStage3;
    private TextView tvStageInfo;
    private TextView tvGrade;
    private Button btnGradeMinus, btnGradePlus;
    private Button btnSubjectMode;
    private Button btnSubject;
    private Button btnVolAll, btnVolUp, btnVolDown;
    private TextView tvVolume;

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pref = new PrefManager(this);

        // 如果当前处于锁屏状态，禁止进入设置页（防止从桌面图标绕过锁屏解锁）
        if (pref.isLocked()) {
            Intent lockIntent = new Intent(this, LockScreenActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(lockIntent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
        updateStatus();
        updateQuestionSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(updateRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateRunnable);
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvRemaining = findViewById(R.id.tvRemaining);
        btnAutoStart = findViewById(R.id.btnAutoStart);
        tvWatchTime = findViewById(R.id.tvWatchTime);
        tvUnlockRule = findViewById(R.id.tvUnlockRule);

        btnStage1 = findViewById(R.id.btnStage1);
        btnStage2 = findViewById(R.id.btnStage2);
        btnStage3 = findViewById(R.id.btnStage3);
        tvStageInfo = findViewById(R.id.tvStageInfo);
        tvGrade = findViewById(R.id.tvGrade);
        btnGradeMinus = findViewById(R.id.btnGradeMinus);
        btnGradePlus = findViewById(R.id.btnGradePlus);
        btnSubjectMode = findViewById(R.id.btnSubjectMode);
        btnSubject = findViewById(R.id.btnSubject);
        btnVolAll = findViewById(R.id.btnVolAll);
        btnVolUp = findViewById(R.id.btnVolUp);
        btnVolDown = findViewById(R.id.btnVolDown);
        tvVolume = findViewById(R.id.tvVolume);
    }

    private void setupListeners() {
        // 开机自启
        btnAutoStart.setOnClickListener(v -> {
            boolean newVal = !pref.isAutoStart();
            pref.setAutoStart(newVal);
            updateStatus();
            toast(newVal ? "已开启开机自启" : "已关闭开机自启");
        });

        // 观看时长预设
        int[] timeButtons = {R.id.btnTime15, R.id.btnTime30, R.id.btnTime45,
                R.id.btnTime60, R.id.btnTime90, R.id.btnTime120};
        int[] timeValues = {15, 30, 45, 60, 90, 120};
        for (int i = 0; i < timeButtons.length; i++) {
            final int minutes = timeValues[i];
            findViewById(timeButtons[i]).setOnClickListener(v -> {
                pref.setWatchLimitMinutes(minutes);
                updateStatus();
                toast("观看时长设为 " + minutes + " 分钟");
            });
        }

        // 设置PIN码
        findViewById(R.id.btnSetPin).setOnClickListener(v -> showPinDialog());

        // 开始计时
        findViewById(R.id.btnStartTimer).setOnClickListener(v -> {
            int minutes = pref.getWatchLimitMinutes();
            pref.startTimer(minutes);
            startLockService();
            toast("计时开始：" + minutes + " 分钟后锁屏");
            updateStatus();
        });

        // 停止计时
        findViewById(R.id.btnStopTimer).setOnClickListener(v -> {
            pref.stopTimer();
            stopService(new Intent(this, LockService.class));
            toast("计时已停止");
            updateStatus();
        });

        // 立即锁屏
        findViewById(R.id.btnLockNow).setOnClickListener(v -> {
            pref.setLocked(true);
            pref.setHomeAliasEnabled(this, true);
            pref.stopTimer();
            startLockService();
            Intent lockIntent = new Intent(this, LockScreenActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(lockIntent);
        });

        // 立即解锁（如果在锁屏状态，从设置页解锁）
        findViewById(R.id.btnUnlock).setOnClickListener(v -> {
            pref.setLocked(false);
            pref.setHomeAliasEnabled(this, false);
            toast("已解锁");
            updateStatus();
        });

        // ============ 题目设置 ============
        btnStage1.setOnClickListener(v -> { pref.setStage(1); updateQuestionSettings(); toast("学段：小学（语数英）"); });
        btnStage2.setOnClickListener(v -> { pref.setStage(2); updateQuestionSettings(); toast("学段：初中（全学科）"); });
        btnStage3.setOnClickListener(v -> { pref.setStage(3); updateQuestionSettings(); toast("学段：高中（全学科）"); });

        btnGradeMinus.setOnClickListener(v -> { pref.setGrade(pref.getGrade() - 1); updateQuestionSettings(); });
        btnGradePlus.setOnClickListener(v -> { pref.setGrade(pref.getGrade() + 1); updateQuestionSettings(); });

        btnSubjectMode.setOnClickListener(v -> {
            int mode = pref.getSubjectMode() == 0 ? 1 : 0;
            pref.setSubjectMode(mode);
            updateQuestionSettings();
            toast(mode == 0 ? "科目：随机混合" : "科目：固定单科");
        });

        btnSubject.setOnClickListener(v -> {
            List<String> subs = QuestionBank.subjectsForStage(pref.getStage());
            String cur = pref.getSubject();
            int idx = subs.indexOf(cur);
            if (idx < 0) idx = 0;
            idx = (idx + 1) % subs.size();
            pref.setSubject(subs.get(idx));
            updateQuestionSettings();
        });

        // 教材册（0=全册 / 1=上册 / 2=下册）
        btnVolAll.setOnClickListener(v -> { pref.setVolume(0); updateQuestionSettings(); toast("教材册：全册"); });
        btnVolUp.setOnClickListener(v -> { pref.setVolume(1); updateQuestionSettings(); toast("教材册：上册"); });
        btnVolDown.setOnClickListener(v -> { pref.setVolume(2); updateQuestionSettings(); toast("教材册：下册"); });
    }

    private void updateQuestionSettings() {
        int stage = pref.getStage();
        btnStage1.setText("小学");
        btnStage2.setText("初中");
        btnStage3.setText("高中");
        btnStage1.setBackgroundResource(stage == 1 ? R.drawable.btn_settings_bg : R.drawable.btn_option_bg);
        btnStage2.setBackgroundResource(stage == 2 ? R.drawable.btn_settings_bg : R.drawable.btn_option_bg);
        btnStage3.setBackgroundResource(stage == 3 ? R.drawable.btn_settings_bg : R.drawable.btn_option_bg);

        String stageRange = QuestionBank.stageName(stage)
                + "（" + (stage == 1 ? "语数英" : "全学科") + "）";
        tvStageInfo.setText("当前学段：" + stageRange);

        tvGrade.setText("年级：" + QuestionBank.gradeText(pref.getGrade()));

        if (pref.getSubjectMode() == 0) {
            btnSubjectMode.setText("科目：随机混合");
            btnSubject.setVisibility(View.GONE);
        } else {
            btnSubjectMode.setText("科目：固定单科");
            btnSubject.setVisibility(View.VISIBLE);
            btnSubject.setText("单科：" + pref.getSubject());
        }

        // 教材册
        int vol = pref.getVolume();
        btnVolAll.setBackgroundResource(vol == 0 ? R.drawable.btn_settings_bg : R.drawable.btn_option_bg);
        btnVolUp.setBackgroundResource(vol == 1 ? R.drawable.btn_settings_bg : R.drawable.btn_option_bg);
        btnVolDown.setBackgroundResource(vol == 2 ? R.drawable.btn_settings_bg : R.drawable.btn_option_bg);
        String volText = vol == 0 ? "全册" : vol == 1 ? "上册" : "下册";
        tvVolume.setText("教材册：" + volText + "（语文/英语按册筛选，数学不限册）");
    }

    private void showPinDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("设置家长PIN码（4位数字）");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setRawInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String pin = input.getText().toString().trim();
            if (pin.length() == 4) {
                pref.setParentPin(pin);
                toast("PIN码已设置");
            } else {
                toast("PIN码必须是4位数字");
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void startLockService() {
        Intent serviceIntent = new Intent(this, LockService.class);
        serviceIntent.putExtra("action", "start_timer");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void updateStatus() {
        // 开机自启
        btnAutoStart.setText(pref.isAutoStart() ? "开机自启：已开启" : "开机自启：已关闭");

        // 观看时长
        tvWatchTime.setText("观看时长：" + pref.getWatchLimitMinutes() + " 分钟");

        // 解锁规则说明（固定逻辑，无需配置）
        tvUnlockRule.setText("解锁规则：首次答对 1 题即解锁；每答错 1 次难度 +1（需连对题数增加），最多连对 5 题解锁");

        // 状态
        if (pref.isLocked()) {
            tvStatus.setText("当前状态：已锁屏");
            tvStatus.setTextColor(getResources().getColor(R.color.wrong_red));
            tvRemaining.setText("请答题解锁");
        } else if (pref.isTimerActive()) {
            long remaining = pref.getRemainingMillis();
            if (remaining <= 0) {
                tvStatus.setText("当前状态：时间到");
                tvStatus.setTextColor(getResources().getColor(R.color.wrong_red));
            } else {
                tvStatus.setText("当前状态：计时中");
                tvStatus.setTextColor(getResources().getColor(R.color.correct_green));
                long totalSec = remaining / 1000;
                long min = totalSec / 60;
                long sec = totalSec % 60;
                tvRemaining.setText("剩余时间：" + String.format("%02d:%02d", min, sec));
            }
        } else {
            tvStatus.setText("当前状态：未计时");
            tvStatus.setTextColor(getResources().getColor(R.color.text_secondary));
            tvRemaining.setText("");
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
