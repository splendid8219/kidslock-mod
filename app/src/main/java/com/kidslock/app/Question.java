package com.kidslock.app;

import java.util.List;

/**
 * 一道题。
 * - 选择题（isFillIn=false）：题干 + 4 个选项（含 1 个正确答案），适配"大字 + 4 选项"界面。
 * - 填空题（isFillIn=true）：题干 + 正确答案字符串（数字），由屏幕虚拟数字键盘输入。
 *   用于数学计算题（如 347+568=?），答案以字符串比对。
 */
public class Question {
    public final String prompt;      // 题干
    public final List<String> options; // 选择题的 4 个选项（已打乱）；填空题为 null
    public final String correct;     // 正确答案（选择题=选项之一；填空题=数字字符串）
    public final String subject;     // 科目，如 数学/语文/英语/物理...
    public final String gradeLabel;  // 年级标签，如 三年级/初二
    public final boolean isFillIn;   // true=填空题（数学计算）；false=选择题

    public Question(String prompt, List<String> options, String correct,
                    String subject, String gradeLabel, boolean isFillIn) {
        this.prompt = prompt;
        this.options = options;
        this.correct = correct;
        this.subject = subject;
        this.gradeLabel = gradeLabel;
        this.isFillIn = isFillIn;
    }
}
