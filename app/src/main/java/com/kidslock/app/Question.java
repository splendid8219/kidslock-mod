package com.kidslock.app;

import java.util.List;

/**
 * 一道题：题干 + 4 个选项（含 1 个正确答案）+ 科目/年级标签。
 * 所有题型统一为"单选题"形态，天然适配原锁屏的"大字 + 4 选项"界面。
 */
public class Question {
    public final String prompt;      // 题干
    public final List<String> options; // 4 个选项（已打乱）
    public final String correct;     // 正确答案
    public final String subject;     // 科目，如 数学/语文/英语/物理...
    public final String gradeLabel;  // 年级标签，如 三年级/初二

    public Question(String prompt, List<String> options, String correct, String subject, String gradeLabel) {
        this.prompt = prompt;
        this.options = options;
        this.correct = correct;
        this.subject = subject;
        this.gradeLabel = gradeLabel;
    }
}
