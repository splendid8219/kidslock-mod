package com.kidslock.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

/**
 * SharedPreferences 封装：管理所有持久化设置和状态。
 * 状态在重启后仍然保留，保证锁屏不会被绕过。
 */
public class PrefManager {

    private static final String PREF_NAME = "kids_lock_prefs";
    private final SharedPreferences prefs;

    // Keys
    private static final String KEY_AUTO_START = "auto_start";
    private static final String KEY_WATCH_LIMIT_MIN = "watch_limit_min";
    private static final String KEY_UNLOCK_COUNT = "unlock_count";
    private static final String KEY_IS_LOCKED = "is_locked";
    private static final String KEY_TIMER_ACTIVE = "timer_active";
    private static final String KEY_TIMER_END_TIME = "timer_end_time";
    private static final String KEY_PARENT_PIN = "parent_pin";
    private static final String KEY_PIN_FAIL_COUNT = "pin_fail_count";
    private static final String KEY_PIN_LOCK_UNTIL = "pin_lock_until";
    // 题目设置：学段(1小学/2初中/3高中)、年级(1-12)、册(0全/1上/2下)、科目模式(0随机/1单科)、固定科目
    private static final String KEY_STAGE = "quiz_stage";
    private static final String KEY_GRADE = "quiz_grade";
    private static final String KEY_VOLUME = "quiz_volume";
    private static final String KEY_SUBJECT_MODE = "quiz_subject_mode";
    private static final String KEY_SUBJECT = "quiz_subject";

    // Defaults
    private static final boolean DEFAULT_AUTO_START = true;
    private static final int DEFAULT_WATCH_LIMIT_MIN = 30;
    private static final int DEFAULT_UNLOCK_COUNT = 3;
    private static final String DEFAULT_PARENT_PIN = "1234";
    private static final int DEFAULT_STAGE = 1;
    private static final int DEFAULT_GRADE = 3;
    private static final int DEFAULT_VOLUME = 0;
    private static final int DEFAULT_SUBJECT_MODE = 0;
    private static final String DEFAULT_SUBJECT = "数学";
    private static final int MAX_PIN_FAILS = 5;
    private static final long PIN_LOCKOUT_MS = 30_000;

    public PrefManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // --- Auto Start ---
    public boolean isAutoStart() {
        return prefs.getBoolean(KEY_AUTO_START, DEFAULT_AUTO_START);
    }

    public void setAutoStart(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply();
    }

    // --- Watch Limit ---
    public int getWatchLimitMinutes() {
        return prefs.getInt(KEY_WATCH_LIMIT_MIN, DEFAULT_WATCH_LIMIT_MIN);
    }

    public void setWatchLimitMinutes(int minutes) {
        prefs.edit().putInt(KEY_WATCH_LIMIT_MIN, minutes).apply();
    }

    // --- Unlock Question Count ---
    public int getUnlockCount() {
        return prefs.getInt(KEY_UNLOCK_COUNT, DEFAULT_UNLOCK_COUNT);
    }

    public void setUnlockCount(int count) {
        prefs.edit().putInt(KEY_UNLOCK_COUNT, count).apply();
    }

    // --- 题目设置：学段 / 年级 / 科目 ---
    public int getStage() {
        int s = prefs.getInt(KEY_STAGE, DEFAULT_STAGE);
        return (s >= 1 && s <= 3) ? s : DEFAULT_STAGE;
    }

    public void setStage(int stage) {
        int s = (stage >= 1 && stage <= 3) ? stage : DEFAULT_STAGE;
        prefs.edit().putInt(KEY_STAGE, s).apply();
        // 学年切换后，把年级夹到该学段范围内
        int g = getGrade();
        int min = QuestionBank.stageMinGrade(s);
        int max = QuestionBank.stageMaxGrade(s);
        if (g < min || g > max) setGrade(min);
    }

    public int getGrade() {
        int g = prefs.getInt(KEY_GRADE, DEFAULT_GRADE);
        return (g >= 1 && g <= 12) ? g : DEFAULT_GRADE;
    }

    public void setGrade(int grade) {
        int min = QuestionBank.stageMinGrade(getStage());
        int max = QuestionBank.stageMaxGrade(getStage());
        if (grade < min) grade = min;
        if (grade > max) grade = max;
        prefs.edit().putInt(KEY_GRADE, grade).apply();
    }

    // --- 册（0=全册 / 1=上册 / 2=下册）---
    public int getVolume() {
        int v = prefs.getInt(KEY_VOLUME, DEFAULT_VOLUME);
        return (v >= 0 && v <= 2) ? v : DEFAULT_VOLUME;
    }

    public void setVolume(int v) {
        prefs.edit().putInt(KEY_VOLUME, (v >= 0 && v <= 2) ? v : DEFAULT_VOLUME).apply();
    }

    public int getSubjectMode() {
        return prefs.getInt(KEY_SUBJECT_MODE, DEFAULT_SUBJECT_MODE);
    }

    public void setSubjectMode(int mode) {
        prefs.edit().putInt(KEY_SUBJECT_MODE, mode == 1 ? 1 : 0).apply();
    }

    public String getSubject() {
        return prefs.getString(KEY_SUBJECT, DEFAULT_SUBJECT);
    }

    public void setSubject(String subject) {
        prefs.edit().putString(KEY_SUBJECT, subject).apply();
    }

    // --- Lock State ---
    public boolean isLocked() {
        return prefs.getBoolean(KEY_IS_LOCKED, false);
    }

    public void setLocked(boolean locked) {
        prefs.edit().putBoolean(KEY_IS_LOCKED, locked).apply();
    }

    // --- Timer ---
    public boolean isTimerActive() {
        return prefs.getBoolean(KEY_TIMER_ACTIVE, false);
    }

    public void setTimerActive(boolean active) {
        prefs.edit().putBoolean(KEY_TIMER_ACTIVE, active).apply();
    }

    public long getTimerEndTime() {
        return prefs.getLong(KEY_TIMER_END_TIME, 0);
    }

    public void setTimerEndTime(long endTime) {
        prefs.edit().putLong(KEY_TIMER_END_TIME, endTime).apply();
    }

    public long getRemainingMillis() {
        long end = getTimerEndTime();
        long now = System.currentTimeMillis();
        return Math.max(0, end - now);
    }

    // --- Parent PIN ---
    public String getParentPin() {
        return prefs.getString(KEY_PARENT_PIN, DEFAULT_PARENT_PIN);
    }

    public void setParentPin(String pin) {
        prefs.edit().putString(KEY_PARENT_PIN, pin).apply();
    }

    // --- PIN 防暴力尝试 ---
    public boolean isPinLocked() {
        return System.currentTimeMillis() < prefs.getLong(KEY_PIN_LOCK_UNTIL, 0);
    }

    public long getPinLockRemainingMillis() {
        long until = prefs.getLong(KEY_PIN_LOCK_UNTIL, 0);
        return Math.max(0, until - System.currentTimeMillis());
    }

    public void recordPinFailure() {
        int fails = prefs.getInt(KEY_PIN_FAIL_COUNT, 0) + 1;
        if (fails >= MAX_PIN_FAILS) {
            prefs.edit()
                    .putInt(KEY_PIN_FAIL_COUNT, 0)
                    .putLong(KEY_PIN_LOCK_UNTIL, System.currentTimeMillis() + PIN_LOCKOUT_MS)
                    .apply();
        } else {
            prefs.edit().putInt(KEY_PIN_FAIL_COUNT, fails).apply();
        }
    }

    public void resetPinFailures() {
        prefs.edit().putInt(KEY_PIN_FAIL_COUNT, 0).putLong(KEY_PIN_LOCK_UNTIL, 0).apply();
    }

    // --- Helper: start timer ---
    public void startTimer(int minutes) {
        long endTime = System.currentTimeMillis() + (long) minutes * 60 * 1000;
        setTimerActive(true);
        setTimerEndTime(endTime);
        setLocked(false);
    }

    public void stopTimer() {
        setTimerActive(false);
        setTimerEndTime(0);
    }

    public boolean isTimerExpired() {
        return isTimerActive() && System.currentTimeMillis() >= getTimerEndTime();
    }

    /**
     * 启用/禁用 HOME launcher alias。
     * 锁屏时启用，让按 HOME 键回到锁屏界面。
     * 解锁时禁用，让按 HOME 键回到正常电视桌面。
     */
    public void setHomeAliasEnabled(Context context, boolean enabled) {
        ComponentName alias = new ComponentName(context, "com.kidslock.app.LockScreenHome");
        PackageManager pm = context.getPackageManager();
        int state = enabled
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        pm.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP);
    }
}
