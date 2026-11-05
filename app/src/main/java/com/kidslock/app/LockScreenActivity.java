package com.kidslock.app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * 锁屏界面：展示适龄全学科题目。
 * 解锁规则：锁屏后首次答对 1 题即解锁；若答错，所需连对题数 +1（封顶 5 题）。
 * 题型双形态：
 *  - 选择题（语文/英语/数学应用题）：4 个选项，方向键选择。
 *  - 填空题（数学计算题）：屏幕虚拟数字键盘输入，适配遥控器。
 * 防绕过：HOME 键拦截、BACK 无效、答错重置连对并使难度 +1。
 */
public class LockScreenActivity extends AppCompatActivity {

    private PrefManager pref;
    private QuestionBank bank;

    // 题目状态
    private Question currentQuestion;
    private List<String> currentOptions;
    private String correctAnswer;
    private int correctCount = 0;
    private int requiredCount;

    // 解锁规则：首次答对 1 题即解锁；每次答错 +1，封顶 5 题
    private static final int INITIAL_REQUIRED = 1;
    private static final int MAX_REQUIRED = 5;

    // Views - 答题主界面
    private View layoutMain;
    private TextView tvSubject;
    private TextView tvQuestion;
    private TextView tvHint;
    private TextView tvProgress;
    private TextView tvFeedback;
    private LinearLayout layoutOptions;
    private Button[] optionButtons;

    // Views - 填空题
    private LinearLayout layoutFill;
    private TextView tvFillAnswer;
    private StringBuilder fillInput = new StringBuilder();

    // Views - PIN模式
    private View layoutPin;
    private TextView tvPinDisplay;
    private StringBuilder pinInput = new StringBuilder();

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock_screen);

        pref = new PrefManager(this);
        bank = new QuestionBank(this);
        requiredCount = INITIAL_REQUIRED;

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );
        hideSystemUI();

        // 确保 HOME 拦截 alias 已启用；并提示家长需将本应用设为默认桌面
        if (pref.isLocked()) {
            pref.setHomeAliasEnabled(this, true);
        }
        if (!isDefaultHome()) {
            Toast.makeText(this,
                    "请将本应用设为默认桌面，才能拦截 HOME 键（设置→应用→默认应用→主屏幕）",
                    Toast.LENGTH_LONG).show();
        }

        initViews();
        loadNextQuestion();
    }

    private void initViews() {
        layoutMain = findViewById(R.id.layoutMain);
        tvSubject = findViewById(R.id.tvSubject);
        tvQuestion = findViewById(R.id.tvQuestion);
        tvHint = findViewById(R.id.tvHint);
        tvProgress = findViewById(R.id.tvProgress);
        tvFeedback = findViewById(R.id.tvFeedback);
        layoutOptions = findViewById(R.id.layoutOptions);
        layoutFill = findViewById(R.id.layoutFill);
        tvFillAnswer = findViewById(R.id.tvFillAnswer);

        optionButtons = new Button[]{
                findViewById(R.id.btnOption1),
                findViewById(R.id.btnOption2),
                findViewById(R.id.btnOption3),
                findViewById(R.id.btnOption4)
        };
        for (int i = 0; i < optionButtons.length; i++) {
            final int idx = i;
            optionButtons[i].setOnClickListener(v -> onOptionSelected(idx));
        }

        // 数字键盘
        int[] fillIds = {
                R.id.btnFill7, R.id.btnFill8, R.id.btnFill9,
                R.id.btnFill4, R.id.btnFill5, R.id.btnFill6,
                R.id.btnFill1, R.id.btnFill2, R.id.btnFill3,
                R.id.btnFill0, R.id.btnFillDot, R.id.btnFillDel
        };
        for (int id : fillIds) {
            final int fid = id;
            findViewById(fid).setOnClickListener(v -> onFillKey(fid));
        }
        findViewById(R.id.btnFillOk).setOnClickListener(v -> onFillSubmit());

        // 家长解锁按钮
        findViewById(R.id.btnParentUnlock).setOnClickListener(v -> showPinPad());

        // PIN界面
        layoutPin = findViewById(R.id.layoutPin);
        tvPinDisplay = findViewById(R.id.tvPinDisplay);

        int[] pinButtonIds = {
                R.id.btnPin0, R.id.btnPin1, R.id.btnPin2, R.id.btnPin3,
                R.id.btnPin4, R.id.btnPin5, R.id.btnPin6, R.id.btnPin7,
                R.id.btnPin8, R.id.btnPin9
        };
        for (int i = 0; i <= 9; i++) {
            final int digit = i;
            findViewById(pinButtonIds[i]).setOnClickListener(v -> onPinDigit(digit));
        }
        findViewById(R.id.btnPinClear).setOnClickListener(v -> {
            pinInput.setLength(0);
            updatePinDisplay();
        });
        findViewById(R.id.btnPinBack).setOnClickListener(v -> {
            layoutPin.setVisibility(View.GONE);
            layoutMain.setVisibility(View.VISIBLE);
            pinInput.setLength(0);
            updatePinDisplay();
        });
    }

    // ==================== 出题 ====================

    private void loadNextQuestion() {
        currentQuestion = bank.nextQuestion(
                pref.getStage(), pref.getGrade(), pref.getVolume(),
                pref.getSubjectMode() == 0, pref.getSubject());
        correctAnswer = currentQuestion.correct;
        currentOptions = currentQuestion.options;
        fillInput.setLength(0);

        tvSubject.setText(currentQuestion.subject + " · " + currentQuestion.gradeLabel);
        tvQuestion.setText(currentQuestion.prompt);

        if (currentQuestion.isFillIn) {
            // 填空题：显示数字键盘，隐藏选项
            tvHint.setText(R.string.fill_prompt);
            layoutOptions.setVisibility(View.GONE);
            layoutFill.setVisibility(View.VISIBLE);
            tvFillAnswer.setText("答案：");
        } else {
            // 选择题：显示选项，隐藏键盘
            tvHint.setText(R.string.what_pronounce);
            layoutFill.setVisibility(View.GONE);
            layoutOptions.setVisibility(View.VISIBLE);
            for (int i = 0; i < optionButtons.length && i < currentOptions.size(); i++) {
                optionButtons[i].setText(currentOptions.get(i));
                optionButtons[i].setBackgroundResource(R.drawable.btn_option_bg);
                optionButtons[i].setEnabled(true);
            }
        }

        updateProgress();
        tvFeedback.setVisibility(View.GONE);
    }

    // ==================== 选择题判定 ====================

    private void onOptionSelected(int index) {
        for (Button b : optionButtons) b.setEnabled(false);
        String selected = currentOptions.get(index);
        boolean correct = selected.equals(correctAnswer);
        if (correct) {
            optionButtons[index].setBackgroundResource(R.drawable.btn_option_correct);
        } else {
            optionButtons[index].setBackgroundResource(R.drawable.btn_option_wrong);
            for (int i = 0; i < currentOptions.size(); i++) {
                if (currentOptions.get(i).equals(correctAnswer)) {
                    optionButtons[i].setBackgroundResource(R.drawable.btn_option_correct);
                    break;
                }
            }
        }
        finishAnswer(correct);
    }

    // ==================== 填空题判定 ====================

    private void onFillKey(int id) {
        if (id == R.id.btnFillDel) {
            if (fillInput.length() > 0) fillInput.deleteCharAt(fillInput.length() - 1);
        } else if (id == R.id.btnFillDot) {
            if (fillInput.indexOf(".") < 0 && fillInput.length() > 0) fillInput.append(".");
        } else {
            String d = ((Button) findViewById(id)).getText().toString();
            if (fillInput.length() < 10) fillInput.append(d);
        }
        tvFillAnswer.setText("答案：" + fillInput.toString());
    }

    private void onFillSubmit() {
        String input = fillInput.toString().trim();
        if (input.isEmpty()) return;
        boolean correct = input.equals(correctAnswer.trim());
        finishAnswer(correct);
    }

    // ==================== 共用判定流程 ====================

    private void finishAnswer(boolean correct) {
        if (correct) {
            correctCount++;
            showFeedback("答对了！", R.color.correct_green);
            if (correctCount >= requiredCount) {
                handler.postDelayed(this::unlock, 600);
            } else {
                handler.postDelayed(this::loadNextQuestion, 1000);
            }
        } else {
            correctCount = 0;
            requiredCount = Math.min(requiredCount + 1, MAX_REQUIRED);
            String tip = requiredCount >= MAX_REQUIRED
                    ? "答错了，难度已到上限（需连对 " + requiredCount + " 题）"
                    : "答错了，难度 +1（需连对 " + requiredCount + " 题）";
            showFeedback(tip, R.color.wrong_red);
            handler.postDelayed(this::loadNextQuestion, 1500);
        }
    }

    private void showFeedback(String msg, int colorRes) {
        tvFeedback.setText(msg);
        tvFeedback.setTextColor(getResources().getColor(colorRes));
        tvFeedback.setVisibility(View.VISIBLE);
    }

    private void updateProgress() {
        tvProgress.setText(String.format("连对 %d 题即可解锁（已答对 %d 题）", requiredCount, correctCount));
    }

    // ==================== 解锁 ====================

    private void unlock() {
        // 解锁后重启倒计时并恢复计时守护：实现“每到设定时间再次自动锁屏”
        LockService.resumeWatching(this);
        finishAffinity();
    }

    // ==================== 家长PIN ====================

    private void showPinPad() {
        layoutMain.setVisibility(View.GONE);
        layoutPin.setVisibility(View.VISIBLE);
        pinInput.setLength(0);
        updatePinDisplay();
    }

    private void onPinDigit(int digit) {
        if (pinInput.length() < 4) {
            pinInput.append(digit);
            updatePinDisplay();
            if (pinInput.length() == 4) {
                handler.postDelayed(this::checkPin, 200);
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

    private void checkPin() {
        if (pref.isPinLocked()) {
            long remainingSec = pref.getPinLockRemainingMillis() / 1000;
            Toast.makeText(this, "尝试次数过多，请 " + remainingSec + " 秒后再试", Toast.LENGTH_SHORT).show();
            pinInput.setLength(0);
            updatePinDisplay();
            return;
        }
        if (pinInput.toString().equals(pref.getParentPin())) {
            pref.resetPinFailures();
            unlock();
        } else {
            pref.recordPinFailure();
            Toast.makeText(this, "PIN码错误", Toast.LENGTH_SHORT).show();
            pinInput.setLength(0);
            updatePinDisplay();
        }
    }

    // ==================== 防绕过 ====================

    private static final long RELOCK_DELAY_MS = 800;

    private final Runnable reLockRunnable = new Runnable() {
        @Override
        public void run() {
            if (pref.isLocked()) {
                Intent lockIntent = new Intent(LockScreenActivity.this, LockScreenActivity.class);
                lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(lockIntent);
            }
        }
    };

    @Override
    public void onBackPressed() {
        if (layoutPin.getVisibility() == View.VISIBLE) {
            layoutPin.setVisibility(View.GONE);
            layoutMain.setVisibility(View.VISIBLE);
            pinInput.setLength(0);
            updatePinDisplay();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (pref.isLocked()) {
            handler.postDelayed(reLockRunnable, RELOCK_DELAY_MS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(reLockRunnable);
        hideSystemUI();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        // 锁屏状态下用户按 HOME / 切后台试图离开时，立即把锁屏重新拉回前台，
        // 作为“默认桌面拦截”失效时的兜底（例如未设为默认桌面、或系统 Launcher 抢焦点）。
        if (pref.isLocked()) {
            Intent i = new Intent(this, LockScreenActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(i);
            } catch (Exception ignored) {
                // 个别 ROM 会忽略该调用，此时依赖悬浮窗 + 服务守护兜底
            }
        }
    }

    /**
     * 判断本应用是否为系统默认桌面（主屏幕应用）。
     * HOME 键拦截依赖此状态：只有默认桌面才能接收到 HOME 意图。
     */
    private boolean isDefaultHome() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = getPackageManager()
                .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri == null || ri.activityInfo == null) return false;
        return getPackageName().equals(ri.activityInfo.packageName);
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
