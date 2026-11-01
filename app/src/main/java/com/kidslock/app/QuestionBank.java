package com.kidslock.app;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 全学科适龄题库。
 * - 数学：按年级程序化生成（无限、精确、随年级难度递增）。
 * - 语文/英语/物理/化学/生物/历史/地理/政治：从 assets/questions/*.json 加载，
 *   按 grade 对应的 band（low/high/c/h）筛选适龄题目，题库可随时增删不改代码。
 *
 * 学段：1=小学(语数英) 2=初中 3=高中(全学科)
 * 年级：1..12（小学1-6，初中7-9，高中10-12）
 */
public class QuestionBank {

    private static final String TAG = "QuestionBank";
    private final Random random = new Random();
    private final Context context;
    private final Map<String, List<BankItem>> cache = new HashMap<>();

    public QuestionBank(Context context) {
        this.context = context.getApplicationContext();
    }

    // ===================== 学段 / 年级 工具 =====================

    public static List<String> subjectsForStage(int stage) {
        List<String> l = new ArrayList<>();
        l.add("语文");
        l.add("数学");
        l.add("英语");
        if (stage != 1) {
            l.add("物理");
            l.add("化学");
            l.add("生物");
            l.add("历史");
            l.add("地理");
            l.add("政治");
        }
        return l;
    }

    public static int stageOfGrade(int grade) {
        if (grade <= 6) return 1;
        if (grade <= 9) return 2;
        return 3;
    }

    public static int stageMinGrade(int stage) {
        return stage == 1 ? 1 : stage == 2 ? 7 : 10;
    }

    public static int stageMaxGrade(int stage) {
        return stage == 1 ? 6 : stage == 2 ? 9 : 12;
    }

    private static String bandForGrade(int grade) {
        if (grade <= 3) return "low";
        if (grade <= 6) return "high";
        if (grade <= 9) return "c";
        return "h";
    }

    public static String gradeText(int grade) {
        String[] names = {"", "一年级", "二年级", "三年级", "四年级", "五年级", "六年级",
                "初一", "初二", "初三", "高一", "高二", "高三"};
        if (grade >= 1 && grade <= 12) return names[grade];
        return grade + "年级";
    }

    public static String stageName(int stage) {
        return stage == 1 ? "小学" : stage == 2 ? "初中" : "高中";
    }

    // ===================== 主入口 =====================

    public Question nextQuestion(int stage, int grade, boolean randomSubject, String fixedSubject) {
        List<String> subs = subjectsForStage(stage);
        String subject = randomSubject ? subs.get(random.nextInt(subs.size())) : fixedSubject;
        if (subject == null || subject.isEmpty()) subject = subs.get(0);
        String gradeLabel = gradeText(grade);

        if (subject.equals("数学")) {
            return genMath(grade, gradeLabel);
        }

        List<BankItem> items = filterByBand(loadBank(subject, stage), bandForGrade(grade));
        if (items.isEmpty()) items = loadBank(subject, stage); // 退路：同科目全部
        if (items.isEmpty()) return genMath(grade, gradeLabel); // 兜底：数学

        BankItem it = items.get(random.nextInt(items.size()));
        List<String> opts = new ArrayList<>();
        opts.add(it.a);
        for (String d : it.d) opts.add(d);
        Collections.shuffle(opts, random);
        return new Question(it.q, opts, it.a, subject, gradeLabel);
    }

    // ===================== JSON 题库加载 =====================

    private static class BankItem {
        String q, a;
        String[] d;
        String band;
    }

    private String assetName(String subject, int stage) {
        switch (subject) {
            case "语文":
                return stage == 1 ? "questions/q_chinese_primary.json" : "questions/q_chinese_middle.json";
            case "英语":
                return stage == 1 ? "questions/q_english_primary.json" : "questions/q_english_middle.json";
            case "物理":
                return "questions/q_physics.json";
            case "化学":
                return "questions/q_chemistry.json";
            case "生物":
                return "questions/q_biology.json";
            case "历史":
                return "questions/q_history.json";
            case "地理":
                return "questions/q_geography.json";
            case "政治":
                return "questions/q_politics.json";
            default:
                return null; // 数学走程序生成
        }
    }

    private synchronized List<BankItem> loadBank(String subject, int stage) {
        String key = subject + "_" + stage;
        if (cache.containsKey(key)) return cache.get(key);
        List<BankItem> items = new ArrayList<>();
        String name = assetName(subject, stage);
        if (name == null) {
            cache.put(key, items);
            return items;
        }
        try {
            InputStream is = context.getAssets().open(name);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                BankItem it = new BankItem();
                it.q = o.getString("q");
                it.a = o.getString("a");
                JSONArray da = o.getJSONArray("d");
                it.d = new String[da.length()];
                for (int j = 0; j < da.length(); j++) it.d[j] = da.getString(j);
                it.band = o.optString("band", "");
                items.add(it);
            }
            Log.i(TAG, "Loaded " + items.size() + " items for " + key);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load " + name, e);
        }
        cache.put(key, items);
        return items;
    }

    private List<BankItem> filterByBand(List<BankItem> items, String band) {
        List<BankItem> out = new ArrayList<>();
        for (BankItem it : items) if (band.equals(it.band)) out.add(it);
        return out;
    }

    // ===================== 数学：按年级程序化生成 =====================

    private Question genMath(int grade, String gradeLabel) {
        int band = grade <= 3 ? 0 : grade <= 6 ? 1 : grade <= 9 ? 2 : 3;
        switch (band) {
            case 0:
                return mathLow(gradeLabel);
            case 1:
                return mathPrimaryHigh(gradeLabel);
            case 2:
                return mathMiddle(gradeLabel);
            default:
                return mathHigh(gradeLabel);
        }
    }

    private final Random rnd = new Random();

    private int rnd(int a, int b) {
        return a + random.nextInt(b - a + 1);
    }

    private String fmt(double v) {
        long r = Math.round(v * 100);
        double d = r / 100.0;
        if (Math.abs(d - Math.round(d)) < 1e-9) return String.valueOf((int) Math.round(d));
        return String.valueOf(d);
    }

    private List<String> mathDistractors(double correct) {
        Set<String> seen = new HashSet<>();
        List<String> out = new ArrayList<>();
        double base = Math.abs(correct) < 1 ? 1 : Math.abs(correct);
        double step = base > 50 ? 5 : base > 10 ? 2 : base > 2 ? 1 : 0.5;
        int guard = 0;
        while (out.size() < 3 && guard < 60) {
            guard++;
            double delta = step * (random.nextInt(3) + 1) * (random.nextBoolean() ? 1 : -1);
            double cand = Math.round((correct + delta) * 100.0) / 100.0;
            String s = fmt(cand);
            if (!s.equals(fmt(correct)) && !seen.contains(s) && cand >= 0) {
                seen.add(s);
                out.add(s);
            }
        }
        int k = 1;
        while (out.size() < 3) {
            double cand = Math.round((correct + k * step) * 100.0) / 100.0;
            String s = fmt(cand);
            if (!s.equals(fmt(correct)) && !seen.contains(s)) {
                seen.add(s);
                out.add(s);
            }
            k++;
        }
        return out;
    }

    private Question buildMath(String prompt, double correct, String gradeLabel) {
        List<String> opts = new ArrayList<>();
        opts.add(fmt(correct));
        opts.addAll(mathDistractors(correct));
        Collections.shuffle(opts, random);
        return new Question(prompt, opts, fmt(correct), "数学", gradeLabel);
    }

    // 小学低段 1-3 年级
    private Question mathLow(String gradeLabel) {
        int t = random.nextInt(4);
        int a, b;
        double ans;
        String p;
        switch (t) {
            case 0:
                a = rnd(1, 20);
                b = rnd(1, 20 - a);
                ans = a + b;
                p = a + " + " + b + " = ?";
                break;
            case 1:
                a = rnd(1, 20);
                b = rnd(1, a);
                ans = a - b;
                p = a + " − " + b + " = ?";
                break;
            case 2:
                a = rnd(2, 9);
                b = rnd(2, 9);
                ans = a * b;
                p = a + " × " + b + " = ?";
                break;
            default:
                a = rnd(11, 19);
                b = rnd(1, 9);
                ans = a - b;
                p = a + " − " + b + " = ?";
                break;
        }
        return buildMath(p, ans, gradeLabel);
    }

    // 小学高段 4-6 年级
    private Question mathPrimaryHigh(String gradeLabel) {
        int t = random.nextInt(7);
        int a, b;
        double ans;
        String p;
        switch (t) {
            case 0: // 多位数加减
                if (random.nextBoolean()) {
                    a = rnd(100, 999);
                    b = rnd(100, 999);
                    ans = a + b;
                    p = a + " + " + b + " = ?";
                } else {
                    a = rnd(500, 999);
                    b = rnd(100, a - 1);
                    ans = a - b;
                    p = a + " − " + b + " = ?";
                }
                break;
            case 1: // 多位数 × 一位数
                a = rnd(100, 999);
                b = rnd(2, 9);
                ans = a * b;
                p = a + " × " + b + " = ?";
                break;
            case 2: // 除法（整除）
                b = rnd(2, 9);
                a = b * rnd(11, 99);
                ans = a / b;
                p = a + " ÷ " + b + " = ?";
                break;
            case 3: // 小数加减
                a = rnd(1, 9);
                b = rnd(1, 9);
                if (random.nextBoolean()) {
                    ans = a + b / 10.0;
                    p = a + " + 0." + b + " = ?";
                } else {
                    ans = a - b / 10.0;
                    p = a + " − 0." + b + " = ?";
                }
                break;
            case 4: // 长方形周长
                a = rnd(5, 30);
                b = rnd(5, 30);
                ans = 2 * (a + b);
                p = "长方形长 " + a + "、宽 " + b + "，周长是？";
                break;
            case 5: // 正方形面积
                a = rnd(3, 20);
                ans = a * a;
                p = "正方形边长 " + a + "，面积是？";
                break;
            default: // 百分数
                a = rnd(10, 50);
                b = rnd(2, 9) * 10;
                ans = a * b / 100.0;
                p = a + "% 的 " + b + " 是？";
                break;
        }
        return buildMath(p, ans, gradeLabel);
    }

    // 初中 7-9 年级
    private Question mathMiddle(String gradeLabel) {
        int t = random.nextInt(8);
        int a, b, x;
        double ans;
        String p;
        switch (t) {
            case 0: // 有理数加减（含负）
                a = rnd(-9, 9);
                b = rnd(-9, 9);
                ans = a + b;
                p = a + " + (" + b + ") = ?";
                break;
            case 1: // 乘方
                a = rnd(2, 5);
                b = rnd(2, 3);
                ans = Math.pow(a, b);
                p = a + " 的 " + b + " 次方是？";
                break;
            case 2: // 平方根
                a = rnd(2, 9);
                ans = a;
                p = "√" + (a * a) + " = ?";
                break;
            case 3: // 一元一次方程 ax+b=c
                x = rnd(1, 12);
                a = rnd(2, 9);
                b = rnd(1, 20);
                ans = x;
                p = a + "x + " + b + " = " + (a * x + b) + "，x = ?";
                break;
            case 4: // 勾股
                int[][] tri = {{3, 4, 5}, {6, 8, 10}, {5, 12, 13}, {8, 15, 17}};
                int[] tr = tri[rnd(0, tri.length - 1)];
                ans = tr[2];
                p = "直角三角形两直角边为 " + tr[0] + "、" + tr[1] + "，斜边是？";
                break;
            case 5: // 一次函数
                a = rnd(2, 5);
                b = rnd(-5, 5);
                x = rnd(1, 6);
                ans = a * x + b;
                p = "当 x = " + x + " 时，y = " + a + "x " + (b >= 0 ? "+ " + b : "− " + (-b)) + " 的值是？";
                break;
            case 6: // 百分数/比例
                a = rnd(10, 40);
                b = rnd(50, 200);
                ans = Math.round(a * b / 100.0);
                p = a + "% 的 " + b + " 是？";
                break;
            default: // 因式分解/平方差简单
                a = rnd(2, 9);
                ans = a * a;
                p = a + "² = ?";
                break;
        }
        return buildMath(p, ans, gradeLabel);
    }

    // 高中 10-12 年级
    private Question mathHigh(String gradeLabel) {
        int t = random.nextInt(7);
        double ans;
        String p;
        switch (t) {
            case 0: // 指数
                int a = rnd(2, 4);
                int b = rnd(2, 4);
                ans = Math.pow(a, b);
                p = a + " 的 " + b + " 次方是？";
                break;
            case 1: // 特殊角三角函数（sin30°=cos60°=1/2）
                int[][] ang = {{30, 1}, {60, 1}};
                int[] an = ang[rnd(0, ang.length - 1)];
                ans = 0.5;
                p = (an[1] == 1 ? "sin " : "cos ") + an[0] + "° 的值是？";
                break;
            case 2: // 等差数列第 n 项
                a = rnd(1, 5);
                int d = rnd(2, 5);
                int n = rnd(4, 8);
                ans = a + (n - 1) * d;
                p = "等差数列首项 " + a + "、公差 " + d + "，第 " + n + " 项是？";
                break;
            case 3: // 概率（骰子偶数）
                ans = 0.5;
                p = "掷一枚均匀骰子，出现偶数的概率是？";
                break;
            case 4: // 复数 i²
                ans = -1;
                p = "虚数单位 i 满足 i² = ?";
                break;
            case 5: // 长方体体积
                int l = rnd(2, 9), w = rnd(2, 9), h = rnd(2, 9);
                ans = (long) l * w * h;
                p = "长方体长 " + l + "、宽 " + w + "、高 " + h + "，体积是？";
                break;
            default: // 对数 log_a(a^b)
                a = rnd(2, 5);
                b = rnd(2, 4);
                ans = b;
                p = "log_" + a + "(" + (int) Math.pow(a, b) + ") = ?";
                break;
        }
        return buildMath(p, ans, gradeLabel);
    }
}
