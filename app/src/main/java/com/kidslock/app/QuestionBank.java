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
 * 全学科适龄题库（第三版：年级细分 + 向下兼容 + 上下册 + 数学含应用题）。
 *
 * 出题规则：
 * - 语文 / 英语：从 assets/questions/*.json 加载固定题集。每条题带 grade(1-12) 与
 *   volume(上/下/通用)。筛选条件：grade <= 选中年级（向下兼容，可出低年级题），
 *   且 (volume=全册 或 该题volume匹配 或 该题为通用)。
 * - 数学：按年级程序化生成，不依赖 JSON。实现年级 g 在 [1, 选中年级] 随机取，
 *   天然向下兼容。
 *     · 计算题 → 填空题（isFillIn=true，屏幕数字键盘输入）
 *     · 应用题 → 选择题（4 选项）
 *
 * 学段：1=小学(语数英) 2=初中 3=高中(全学科)
 * 年级：1..12（小学1-6，初中7-9，高中10-12）
 * 册：0=全册 1=上册 2=下册
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

    /**
     * @param stage         学段 1/2/3
     * @param grade         选中年级 1..12
     * @param volume        册 0全/1上/2下
     * @param randomSubject 是否随机混合科目
     * @param fixedSubject  固定科目（randomSubject=false 时生效）
     */
    public Question nextQuestion(int stage, int grade, int volume,
                                 boolean randomSubject, String fixedSubject) {
        List<String> subs = subjectsForStage(stage);
        String subject = randomSubject ? subs.get(random.nextInt(subs.size())) : fixedSubject;
        if (subject == null || subject.isEmpty()) subject = subs.get(0);
        String gradeLabel = gradeText(grade);

        if (subject.equals("数学")) {
            // 数学按年级程序化生成（含应用题），忽略册，向下兼容
            return genMath(grade, gradeLabel);
        }

        // 语文 / 英语 / 中学各科：按 grade(向下兼容) + volume 筛选
        List<BankItem> items = filterByGradeVolume(loadBank(subject, stage), grade, volume);
        if (items.isEmpty()) items = loadBank(subject, stage); // 退路：同科目全部
        if (items.isEmpty()) return genMath(grade, gradeLabel); // 兜底：数学

        BankItem it = items.get(random.nextInt(items.size()));
        List<String> opts = new ArrayList<>();
        opts.add(it.a);
        for (String d : it.d) opts.add(d);
        Collections.shuffle(opts, random);
        return new Question(it.q, opts, it.a, subject, gradeLabel, false);
    }

    // ===================== JSON 题库加载 =====================

    private static class BankItem {
        String q, a;
        String[] d;
        int grade;      // 0 = 通用（不限年级）
        String volume;  // "" = 通用（不限册）
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
                it.grade = o.optInt("grade", 0);
                it.volume = o.optString("volume", "");
                items.add(it);
            }
            Log.i(TAG, "Loaded " + items.size() + " items for " + key);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load " + name, e);
        }
        cache.put(key, items);
        return items;
    }

    // ===================== 趣味题库（课外拓展，解锁后随机触发） =====================

    private List<BankItem> funItemsCache;

    /**
     * 抽取一道趣味题（课外拓展，与学科/年级无关）。
     * 题库文件 questions/q_fun.json，结构同常规题库：{q, a, d[3干扰项]}。
     * 返回 null 表示题库为空或加载失败。
     */
    public Question nextFunQuestion() {
        if (funItemsCache == null) {
            funItemsCache = loadArray("questions/q_fun.json");
            Log.i(TAG, "Loaded " + (funItemsCache == null ? 0 : funItemsCache.size()) + " fun items");
        }
        if (funItemsCache == null || funItemsCache.isEmpty()) return null;
        BankItem it = funItemsCache.get(random.nextInt(funItemsCache.size()));
        List<String> opts = new ArrayList<>();
        opts.add(it.a);
        for (String d : it.d) opts.add(d);
        Collections.shuffle(opts, random);
        return new Question(it.q, opts, it.a, "趣味题库", "额外奖励", false);
    }

    /** 通用 JSON 数组加载（常规题库与趣味题库共用解析逻辑） */
    private List<BankItem> loadArray(String assetName) {
        List<BankItem> items = new ArrayList<>();
        try {
            InputStream is = context.getAssets().open(assetName);
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
                items.add(it);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load " + assetName, e);
            return null;
        }
        return items;
    }

    /** 筛选：向下兼容（grade <= maxGrade）+ 册匹配 */
    private List<BankItem> filterByGradeVolume(List<BankItem> items, int maxGrade, int volume) {
        List<BankItem> out = new ArrayList<>();
        String volName = volume == 1 ? "上" : volume == 2 ? "下" : "";
        for (BankItem it : items) {
            // 年级：0(通用) 始终可用；否则必须 <= 选中年级（向下兼容）
            if (it.grade > 0 && it.grade > maxGrade) continue;
            // 册：全册 或 通用题 或 volume 匹配
            if (volume != 0 && !volName.equals(it.volume) && !"".equals(it.volume) && !"通用".equals(it.volume)) continue;
            out.add(it);
        }
        return out;
    }

    // ===================== 数学：按年级程序化生成 =====================

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
        return new Question(prompt, opts, fmt(correct), "数学", gradeLabel, false);
    }

    private Question buildFill(String prompt, String correct, String gradeLabel) {
        return new Question(prompt, null, correct, "数学", gradeLabel, true);
    }

    /** 数学总入口：实现年级 g 在 [1, grade] 随机 → 向下兼容 */
    private Question genMath(int grade, String gradeLabel) {
        int g = rnd(1, grade);
        // 60% 计算题(填空) / 40% 应用题(选择)
        if (random.nextInt(10) < 6) return mathCalc(g, gradeLabel);
        return mathWordProblem(g, gradeLabel);
    }

    // ---------- 计算题（填空） ----------
    private Question mathCalc(int g, String gradeLabel) {
        if (g <= 1) {
            if (random.nextBoolean()) {
                int a = rnd(1, 20), b = rnd(1, 20 - a);
                return buildFill(a + " + " + b + " = ?", String.valueOf(a + b), gradeLabel);
            } else {
                int a = rnd(1, 20), b = rnd(1, a);
                return buildFill(a + " − " + b + " = ?", String.valueOf(a - b), gradeLabel);
            }
        } else if (g == 2) {
            int t = random.nextInt(3);
            if (t == 0) { int a = rnd(1, 50), b = rnd(1, 99 - a); return buildFill(a + " + " + b + " = ?", String.valueOf(a + b), gradeLabel); }
            if (t == 1) { int a = rnd(20, 99), b = rnd(1, a); return buildFill(a + " − " + b + " = ?", String.valueOf(a - b), gradeLabel); }
            int a = rnd(2, 9), b = rnd(2, 9); return buildFill(a + " × " + b + " = ?", String.valueOf(a * b), gradeLabel);
        } else if (g == 3) {
            int t = random.nextInt(4);
            if (t == 0) { int a = rnd(100, 999), b = rnd(100, 999); return buildFill(a + " + " + b + " = ?", String.valueOf(a + b), gradeLabel); }
            if (t == 1) { int a = rnd(500, 999), b = rnd(100, a - 1); return buildFill(a + " − " + b + " = ?", String.valueOf(a - b), gradeLabel); }
            if (t == 2) { int a = rnd(100, 999), b = rnd(2, 9); return buildFill(a + " × " + b + " = ?", String.valueOf(a * b), gradeLabel); }
            int b = rnd(2, 9), a = b * rnd(11, 99); return buildFill(a + " ÷ " + b + " = ?", String.valueOf(a / b), gradeLabel);
        } else if (g == 4) {
            int t = random.nextInt(4);
            if (t == 0) { int a = rnd(10, 99), b = rnd(10, 99); return buildFill(a + " × " + b + " = ?", String.valueOf(a * b), gradeLabel); }
            if (t == 1) { int b = rnd(12, 29), a = b * rnd(3, 19); return buildFill(a + " ÷ " + b + " = ?", String.valueOf(a / b), gradeLabel); }
            if (t == 2) { int a = rnd(1, 9), b = rnd(1, 9); double ans = a + b / 10.0; return buildFill(a + " + 0." + b + " = ?", fmt(ans), gradeLabel); }
            int a = rnd(10, 50), b = rnd(2, 9) * 10; return buildFill(a + "% 的 " + b + " 是？", fmt(a * b / 100.0), gradeLabel);
        } else if (g == 5) {
            int t = random.nextInt(5);
            if (t == 0) { double a = rnd(1, 9) + rnd(1, 9) / 10.0, b = rnd(1, 9) + rnd(1, 9) / 10.0; return buildFill(fmt(a) + " × " + fmt(b) + " = ?", fmt(a * b), gradeLabel); }
            if (t == 1) { int a = rnd(2, 9); return buildFill(a + "² = ?", String.valueOf(a * a), gradeLabel); }
            if (t == 2) { int x = rnd(1, 12), a = rnd(2, 9), b = rnd(1, 20), c = a * x + b; return buildFill("x = ?  （" + a + "x + " + b + " = " + c + "）", String.valueOf(x), gradeLabel); }
            if (t == 3) { int a = rnd(3, 20), b = rnd(3, 20); return buildFill("长方形长 " + a + " 宽 " + b + " 面积？", String.valueOf(a * b), gradeLabel); }
            int a = rnd(3, 12), b = rnd(2, 9) * 2; return buildFill("三角形底 " + a + " 高 " + b + " 面积？", String.valueOf(a * b / 2), gradeLabel);
        } else if (g == 6) {
            int t = random.nextInt(5);
            if (t == 0) { int a = rnd(2, 9), b = rnd(2, 9), c = rnd(2, 9); return buildFill(a + "×" + b + "×" + c + " = ?", String.valueOf(a * b * c), gradeLabel); }
            if (t == 1) { int a = rnd(10, 50), b = rnd(2, 9) * 10; return buildFill(a + "% 的 " + b + " 是？", String.valueOf(a * b / 10), gradeLabel); }
            if (t == 2) { int a = rnd(3, 15); return buildFill("正方体棱长 " + a + " 体积？", String.valueOf(a * a * a), gradeLabel); }
            if (t == 3) { int x = rnd(2, 9), a = rnd(2, 9), k = rnd(1, 3), b = a * k, c = x * k; return buildFill(a + ":" + b + " = " + c + ":x，x=？", String.valueOf(x), gradeLabel); }
            int a = rnd(2, 9), b = rnd(2, 9) * 10; return buildFill(a + " 的 " + b + "% 是？", String.valueOf(a * b / 10), gradeLabel);
        } else if (g <= 9) { // 初中 7-9
            int t = random.nextInt(5);
            if (t == 0) { int a = rnd(-9, 9), b = rnd(-9, 9); return buildFill("(" + a + ") + (" + b + ") = ?", String.valueOf(a + b), gradeLabel); }
            if (t == 1) { int a = rnd(2, 5), b = rnd(2, 3); return buildFill(a + " 的 " + b + " 次方 = ?", String.valueOf((int) Math.pow(a, b)), gradeLabel); }
            if (t == 2) { int[][] tr = {{3, 4, 5}, {6, 8, 10}, {5, 12, 13}}; int[] x = tr[rnd(0, tr.length - 1)]; return buildFill("直角三角形直角边 " + x[0] + " 和 " + x[1] + "，斜边？", String.valueOf(x[2]), gradeLabel); }
            if (t == 3) { int a = rnd(2, 5), b = rnd(-5, 5), xv = rnd(1, 6); return buildFill("x=" + xv + " 时 y=" + a + "x" + (b >= 0 ? "+" : "−") + Math.abs(b) + " 的值？", String.valueOf(a * xv + b), gradeLabel); }
            int a = rnd(2, 9); return buildFill(a + "² = ?", String.valueOf(a * a), gradeLabel);
        } else { // 高中 10-12
            int t = random.nextInt(5);
            if (t == 0) { int a = rnd(2, 4), b = rnd(2, 4); return buildFill(a + " 的 " + b + " 次方 = ?", String.valueOf((int) Math.pow(a, b)), gradeLabel); }
            if (t == 1) { int[][] ang = {{30, 1}, {60, 1}}; int[] an = ang[rnd(0, 1)]; return buildFill((an[1] == 1 ? "sin " : "cos ") + an[0] + "° = ?", "0.5", gradeLabel); }
            if (t == 2) { int a = rnd(1, 5), d = rnd(2, 5), n = rnd(4, 8); return buildFill("等差数列首项 " + a + " 公差 " + d + " 第 " + n + " 项？", String.valueOf(a + (n - 1) * d), gradeLabel); }
            if (t == 3) { int l = rnd(2, 9), w = rnd(2, 9), h = rnd(2, 9); return buildFill("长方体 " + l + "×" + w + "×" + h + " 体积？", String.valueOf((long) l * w * h), gradeLabel); }
            int a = rnd(2, 5), b = rnd(2, 4); return buildFill("log_" + a + "(" + (int) Math.pow(a, b) + ") = ?", String.valueOf(b), gradeLabel);
        }
    }

    // ---------- 应用题（选择） ----------
    private Question mathWordProblem(int g, String gradeLabel) {
        if (g <= 2) {
            if (random.nextBoolean()) {
                int a = rnd(3, 20), b = rnd(3, 20);
                return buildMath("小明有 " + a + " 支铅笔，又买了 " + b + " 支，一共有几支？", a + b, gradeLabel);
            } else {
                int a = rnd(10, 30), b = rnd(1, a);
                return buildMath(a + " 个苹果，吃掉 " + b + " 个，还剩几个？", a - b, gradeLabel);
            }
        } else if (g <= 4) {
            int t = random.nextInt(3);
            if (t == 0) { int a = rnd(2, 9), b = rnd(2, 9); return buildMath("每盒有 " + a + " 块糖，" + b + " 盒一共有几块？", a * b, gradeLabel); }
            if (t == 1) { int b = rnd(2, 9), a = rnd(2, 9); int total = a * b; return buildMath(a + " 个小朋友平均分 " + total + " 颗糖，每人几颗？", b, gradeLabel); }
            int a = rnd(3, 20), b = rnd(3, 20); return buildMath("长方形长 " + a + " 厘米、宽 " + b + " 厘米，周长几厘米？", 2 * (a + b), gradeLabel);
        } else if (g <= 6) {
            int t = random.nextInt(3);
            if (t == 0) { int a = rnd(10, 40), b = rnd(2, 9) * 10; return buildMath("一本书 " + a + " 页，小明看了 " + b + "%，看了几页？", a * b / 10, gradeLabel); }
            if (t == 1) { int a = rnd(2, 6), b = rnd(2, 6); return buildMath(a + " 个小组，每组 " + b + " 人，总人数？", a * b, gradeLabel); }
            int a = rnd(3, 12), b = rnd(3, 12); return buildMath("长方形长 " + a + " 宽 " + b + "，面积几？", a * b, gradeLabel);
        } else if (g <= 9) { // 初中
            int t = random.nextInt(3);
            if (t == 0) { int v = rnd(40, 80), t1 = rnd(2, 5); return buildMath("小明骑车速度 " + v + " 千米/时，骑 " + t1 + " 小时走了多少千米？", v * t1, gradeLabel); }
            if (t == 1) { int a = rnd(5, 15), b = rnd(3, 10); return buildMath("工程队每天修 " + a + " 米，" + b + " 天修了多少米？", a * b, gradeLabel); }
            int x = rnd(2, 10), a = rnd(2, 9), b = rnd(1, 20), c = a * x + b; return buildMath("解方程：" + a + "x + " + b + " = " + c + "，x = ？", x, gradeLabel);
        } else { // 高中
            int t = random.nextInt(3);
            if (t == 0) {
                int[] tots = {2, 4, 5}; int total = tots[rnd(0, tots.length - 1)]; int fav = rnd(1, total - 1);
                return buildMath("袋中有 " + total + " 个球，其中 " + fav + " 个红球，摸到红球概率？", (double) fav / total, gradeLabel);
            }
            if (t == 1) { int a = rnd(2, 6), d = rnd(2, 5), n = rnd(3, 8); return buildMath("等差数列首项 " + a + " 公差 " + d + " 第 " + n + " 项？", a + (n - 1) * d, gradeLabel); }
            int a = rnd(2, 9), b = rnd(2, 4); return buildMath("log_" + a + "(" + (int) Math.pow(a, b) + ") = ?", b, gradeLabel);
        }
    }
}
