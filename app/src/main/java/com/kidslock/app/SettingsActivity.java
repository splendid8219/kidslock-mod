package com.kidslock.app;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
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

    // 应用打开密码
    private EditText etCurrentApp;
    private EditText etNewApp;
    private EditText etConfirmApp;
    private TextView tvAppMsg;

    // 设备管理员（防卸载门槛）
    private DevicePolicyManager dpm;
    private ComponentName adminComponent;
    private Button btnActivateAdmin;
    private TextView tvAdminMsg;

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

        // 应用打开密码
        etCurrentApp = findViewById(R.id.etCurrentApp);
        etNewApp = findViewById(R.id.etNewApp);
        etConfirmApp = findViewById(R.id.etConfirmApp);
        tvAppMsg = findViewById(R.id.tvAppMsg);
        Button btnSaveApp = findViewById(R.id.btnSaveApp);
        btnSaveApp.setOnClickListener(v -> saveAppPin());

        // 设备管理员（防卸载门槛）
        dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, KidsLockDeviceAdmin.class);
        btnActivateAdmin = findViewById(R.id.btnActivateAdmin);
        tvAdminMsg = findViewById(R.id.tvAdminMsg);
        btnActivateAdmin.setOnClickListener(v -> toggleAdmin());
        refreshAdminStatus();
    }

    /**
     * 设备管理员：未激活 → 拉起系统“激活设备管理员”界面；已激活 → 提示取消方法。
     * 激活后系统会禁止直接卸载本应用（需先取消管理员）。
     */
    private void toggleAdmin() {
        if (dpm.isAdminActive(adminComponent)) {
            tvAdminMsg.setText("已是设备管理员。卸载前需先到系统“设置→安全→设备管理员”取消本应用。");
            return;
        }
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "激活后可防止儿童误卸载本应用；卸载前需先取消设备管理员权限。");
        startActivity(intent);
    }

    private void refreshAdminStatus() {
        boolean active = dpm.isAdminActive(adminComponent);
        btnActivateAdmin.setText(active ? "设备管理员：已激活" : "激活设备管理员（防误卸载）");
        tvAdminMsg.setText(active ? "已激活：卸载本应用需先取消设备管理员权限。"
                : "未激活：点击上方按钮激活，可在设置中取消。");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAdminStatus();
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

    /**
     * 修改“应用打开密码”（默认 4321）。
     * 修改前先校验当前打开密码，避免儿童自行修改。
     */
    private void saveAppPin() {
        String current = etCurrentApp.getText().toString().trim();
        String newPin = etNewApp.getText().toString().trim();
        String confirm = etConfirmApp.getText().toString().trim();

        if (!current.equals(pref.getAppPassword())) {
            tvAppMsg.setText("当前打开密码错误");
            return;
        }
        if (newPin.length() != 4) {
            tvAppMsg.setText("新密码必须是 4 位数字");
            return;
        }
        if (!newPin.equals(confirm)) {
            tvAppMsg.setText("两次输入的新密码不一致");
            return;
        }

        pref.setAppPassword(newPin);
        tvAppMsg.setText("应用密码已修改，请牢记新密码");
        Toast.makeText(this, "应用打开密码已修改", Toast.LENGTH_SHORT).show();
        etCurrentApp.setText("");
        etNewApp.setText("");
        etConfirmApp.setText("");
    }
}
