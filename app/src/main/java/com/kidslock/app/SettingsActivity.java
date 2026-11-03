package com.kidslock.app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 设置页：集中管理解锁密码（家长 PIN 码）。
 * 修改前必须先校验当前密码，避免儿童自行修改。
 */
public class SettingsActivity extends AppCompatActivity {

    private PrefManager pref;
    private EditText etCurrent;
    private EditText etNew;
    private EditText etConfirm;
    private TextView tvMsg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pref = new PrefManager(this);
        setContentView(R.layout.activity_settings);

        etCurrent = findViewById(R.id.etCurrentPin);
        etNew = findViewById(R.id.etNewPin);
        etConfirm = findViewById(R.id.etConfirmPin);
        tvMsg = findViewById(R.id.tvSettingsMsg);

        Button btnSave = findViewById(R.id.btnSavePin);
        btnSave.setOnClickListener(v -> savePin());
    }

    private void savePin() {
        String current = etCurrent.getText().toString().trim();
        String newPin = etNew.getText().toString().trim();
        String confirm = etConfirm.getText().toString().trim();

        if (!current.equals(pref.getParentPin())) {
            tvMsg.setText("当前密码错误");
            return;
        }
        if (newPin.length() != 4) {
            tvMsg.setText("新密码必须是 4 位数字");
            return;
        }
        if (!newPin.equals(confirm)) {
            tvMsg.setText("两次输入的新密码不一致");
            return;
        }

        pref.setParentPin(newPin);
        tvMsg.setText("密码已修改，请牢记新密码");
        Toast.makeText(this, "解锁密码已修改", Toast.LENGTH_SHORT).show();
        etCurrent.setText("");
        etNew.setText("");
        etConfirm.setText("");
    }
}
