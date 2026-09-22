package com.lunamax.medassistant;

import android.Manifest;
import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.lunamax.medassistant.databinding.ActivityMainBinding;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Single activity shell: system bars, five primary destinations and settings. */
public final class MainActivity extends AppCompatActivity {
    static final int TAB_TODAY = 0;
    static final int TAB_MEDICATIONS = 1;
    static final int TAB_DOCUMENTS = 2;
    static final int TAB_HEALTH = 3;
    static final int TAB_ASSISTANT = 4;
    private static final int REQUEST_EXPORT = 3001;
    private static final int REQUEST_IMPORT = 3002;
    private static final int REQUEST_NOTIFICATIONS = 3003;
    private static final String BACKUP_PREFIX = "PERSONAL_MED_BACKUP_V3\n";
    private static final String LEGACY_BACKUP_PREFIX = "PERSONAL_MED_BACKUP_V2\n";
    private static final String OLD_BACKUP_PREFIX = "LUNA_MAX_BACKUP_V1\n";
    private static final String DATA_CIPHER_PREFIX = "v1:";

    private ActivityMainBinding binding;
    private Palette palette;
    private LunaDatabase database;
    private ReminderScheduler scheduler;
    private AssistantRepository assistant;
    private VisionRepository vision;
    private ProviderProfileRepository providers;
    private int currentTab = TAB_TODAY;
    private boolean changingTab;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        palette = Palette.from(this);
        database = new LunaDatabase(this);
        scheduler = new ReminderScheduler(this, database);
        assistant = new AssistantRepository(this);
        vision = new VisionRepository(this);
        providers = new ProviderProfileRepository(this, database);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        configureSystemBars();
        ViewCompat.setOnApplyWindowInsetsListener(binding.shell, (view, insets) -> {
            WindowInsetsCompat bars = insets;
            int top = bars.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = bars.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(0, top, 0, bottom);
            return insets;
        });
        binding.topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) { showSettings(); return true; }
            return false;
        });
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            if (changingTab) return true;
            if (item.getItemId() == R.id.nav_today) showTab(TAB_TODAY);
            else if (item.getItemId() == R.id.nav_medications) showTab(TAB_MEDICATIONS);
            else if (item.getItemId() == R.id.nav_documents) showTab(TAB_DOCUMENTS);
            else if (item.getItemId() == R.id.nav_health) showTab(TAB_HEALTH);
            else showTab(TAB_ASSISTANT);
            return true;
        });
        currentTab = state == null ? TAB_TODAY : state.getInt("currentTab", TAB_TODAY);
        showTab(currentTab);
        scheduler.rebuild();
    }

    @Override protected void onResume() {
        super.onResume();
        if (scheduler != null) scheduler.rebuild();
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt("currentTab", currentTab);
        super.onSaveInstanceState(outState);
    }

    @Override public void onBackPressed() {
        if (currentTab != TAB_TODAY) { showTab(TAB_TODAY); return; }
        super.onBackPressed();
    }

    void showTab(int tab) {
        currentTab = Math.max(TAB_TODAY, Math.min(TAB_ASSISTANT, tab));
        Fragment fragment;
        String title;
        switch (currentTab) {
            case TAB_MEDICATIONS: fragment = new MedicationsFragment(); title = "药物"; break;
            case TAB_DOCUMENTS: fragment = new DocumentsFragment(); title = "资料"; break;
            case TAB_HEALTH: fragment = new HealthFragment(); title = "健康"; break;
            case TAB_ASSISTANT: fragment = new AssistantFragment(); title = "助手"; break;
            default: fragment = new TodayFragment(); title = "今日"; break;
        }
        binding.topAppBar.setTitle(title);
        binding.topAppBar.setSubtitle("本地优先 · 轻量记录");
        int menuId = currentTab == TAB_TODAY ? R.id.nav_today : currentTab == TAB_MEDICATIONS ? R.id.nav_medications : currentTab == TAB_DOCUMENTS ? R.id.nav_documents : currentTab == TAB_HEALTH ? R.id.nav_health : R.id.nav_assistant;
        if (binding.bottomNavigation.getSelectedItemId() != menuId) {
            changingTab = true;
            try { binding.bottomNavigation.setSelectedItemId(menuId); }
            finally { changingTab = false; }
        }
        getSupportFragmentManager().beginTransaction().setReorderingAllowed(true).replace(R.id.page_container, fragment).commit();
    }

    Palette palette() { return palette; }
    LunaDatabase database() { return database; }
    ReminderScheduler scheduler() { return scheduler; }
    AssistantRepository assistant() { return assistant; }
    VisionRepository vision() { return vision; }
    ProviderProfileRepository providers() { return providers; }

    void feedback(String message) {
        Toast.makeText(this, message == null ? "" : message, Toast.LENGTH_LONG).show();
    }

    private void configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(palette.app);
        getWindow().setNavigationBarColor(palette.app);
        Window window = getWindow();
        int flags = 0;
        if (!isDarkMode()) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26 && !isDarkMode()) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private boolean isDarkMode() {
        return (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    void showSettings() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(8), dp(4), dp(8), dp(4));
        TextView intro = label("设置只影响本机提醒、外观和数据控制。API Key 永不进入备份。");
        form.addView(intro, margin(0, 0, 0, 8));
        MaterialButton notifications = button("管理通知权限");
        notifications.setOnClickListener(v -> requestNotifications());
        form.addView(notifications, margin(0, 0, 0, 6));
        MaterialButton exact = button("管理精确提醒");
        exact.setOnClickListener(v -> { Intent intent = scheduler.exactAlarmSettingsIntent(); if (intent != null) try { startActivity(intent); } catch (Exception ignored) { feedback("请在系统设置中允许精确闹钟"); } });
        form.addView(exact, margin(0, 0, 0, 6));
        MaterialButton rebuild = button("重建未来 30 天提醒");
        rebuild.setOnClickListener(v -> { scheduler.rebuild(); feedback("已重建未来 30 天提醒"); });
        form.addView(rebuild, margin(0, 0, 0, 6));
        MaterialButton export = button("导出加密备份");
        export.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, "personal-medication-backup.pmb"), REQUEST_EXPORT));
        form.addView(export, margin(0, 0, 0, 6));
        MaterialButton importButton = button("导入加密备份");
        importButton.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/octet-stream").addCategory(Intent.CATEGORY_OPENABLE), REQUEST_IMPORT));
        form.addView(importButton, margin(0, 0, 0, 6));
        MaterialButton about = button("关于与版本");
        about.setOnClickListener(v -> showAbout());
        form.addView(about, margin(0, 0, 0, 6));
        MaterialButton wipe = button("彻底删除本机数据");
        wipe.setTextColor(palette.danger);
        wipe.setOnClickListener(v -> confirmWipe());
        form.addView(wipe);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("设置与隐私")
                .setView(form)
                .setPositiveButton("关闭", null);
        builder.show();
    }

    void launchExportBackup() {
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream")
                .putExtra(Intent.EXTRA_TITLE, "personal-medication-backup.pmb"), REQUEST_EXPORT);
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        } else feedback("通知权限已允许");
    }

    private void showAbout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("个人用药助手")
                .setMessage("本地优先 · 可审计的个人健康日志\n\n版本 " + BuildConfig.VERSION_NAME + "（构建 " + BuildConfig.VERSION_CODE + "）\n包名 " + getPackageName() + "\n\n用药提醒、库存和安全检查不替代医生、药师、官方说明书或急救服务。")
                .setPositiveButton("关闭", null)
                .show();
    }

    private void confirmWipe() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("彻底删除本机数据？")
                .setMessage("将删除药物、批次、计划、历史、健康档案、资料索引、本地会话和 API Key，无法撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除全部", (d, w) -> {
                    boolean complete = true;
                    assistant.cancelPending();
                    vision.cancelPending();
                    Fragment current = getSupportFragmentManager().findFragmentById(R.id.page_container);
                    if (current instanceof DocumentsFragment) ((DocumentsFragment) current).cancelPendingUi();
                    try { database.clearData(true); } catch (Exception error) { complete = false; }
                    try { assistant.deleteKey(); } catch (Exception error) { complete = false; }
                    try { new DataCipher(this).deleteKey(); } catch (Exception error) { complete = false; }
                    try { if (!deletePrivateDocuments()) complete = false; } catch (Exception error) { complete = false; }
                    scheduler.rebuild();
                    showTab(TAB_TODAY);
                    feedback(complete ? "本机资料和 Key 已删除" : "已执行删除，但部分清理失败，请重试");
                }).show();
    }

    private boolean deletePrivateDocuments() {
        boolean complete = true;
        File dir = new File(getFilesDir(), "documents");
        File[] files = dir.listFiles();
        if (files != null) for (File file : files) if (!deleteTree(file)) complete = false;
        if (dir.isDirectory() && !dir.delete() && dir.exists()) complete = false;
        return complete;
    }

    private boolean deleteTree(File file) {
        boolean complete = true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) if (!deleteTree(child)) complete = false;
        }
        if (!file.delete() && file.exists()) complete = false;
        return complete;
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQUEST_EXPORT) exportBackup(data.getData());
        else if (requestCode == REQUEST_IMPORT) importBackup(data.getData());
    }

    private void exportBackup(android.net.Uri uri) {
        new Thread(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalArgumentException("无法写入备份");
                String encrypted = new DataCipher(this).encrypt(database.exportBundle().toString());
                out.write((BACKUP_PREFIX + encrypted).getBytes(StandardCharsets.UTF_8));
                runOnUiThread(() -> feedback("加密备份已导出；API Key 未包含在备份中"));
            } catch (Exception error) { runOnUiThread(() -> feedback("备份导出失败：" + safeMessage(error))); }
        }).start();
    }

    private void importBackup(android.net.Uri uri) {
        new Thread(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                String raw = readAll(input);
                String encrypted = raw.startsWith(BACKUP_PREFIX) ? raw.substring(BACKUP_PREFIX.length())
                        : raw.startsWith(LEGACY_BACKUP_PREFIX) ? raw.substring(LEGACY_BACKUP_PREFIX.length())
                        : raw.startsWith(OLD_BACKUP_PREFIX) ? raw.substring(OLD_BACKUP_PREFIX.length()) : "";
                if (encrypted.isEmpty()) throw new IllegalArgumentException("备份格式无法识别");
                if (!encrypted.startsWith(DATA_CIPHER_PREFIX)) throw new IllegalArgumentException("备份必须使用应用加密格式");
                String decoded = new DataCipher(this).decryptStrict(encrypted);
                JSONObject bundle = new JSONObject(decoded);
                runOnUiThread(() -> new MaterialAlertDialogBuilder(this).setTitle("替换本机资料？").setMessage("导入会替换当前药物、计划、健康记录、资料索引和本地会话，不会导入 API Key。")
                        .setNegativeButton("取消", null).setPositiveButton("替换", (d, w) -> { try { database.importBundle(bundle); scheduler.rebuild(); showTab(TAB_TODAY); feedback("加密备份已导入"); } catch (Exception error) { feedback("备份导入失败：" + safeMessage(error)); } }).show());
            } catch (Exception error) { runOnUiThread(() -> feedback("备份导入失败：" + safeMessage(error))); }
        }).start();
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) throw new IllegalArgumentException("无法读取备份");
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n; int total = 0;
        while ((n = input.read(buffer)) >= 0) { total += n; if (total > 8_000_000) throw new IllegalArgumentException("备份文件过大"); out.write(buffer, 0, n); }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    String safeMessage(Exception error) {
        String value = error.getMessage();
        return value == null || value.isEmpty() ? "请稍后重试" : redact(value);
    }

    private static String redact(String value) {
        return value
                .replaceAll("(?i)bearer\\s+\\S+", "Bearer [已隐藏]")
                .replaceAll("(?i)(x-api-key|api[- ]?key)\\s*[:=]?\\s*\\S+", "$1 [已隐藏]")
                .replaceAll("(?i)\\bsk-[A-Za-z0-9_-]{8,}\\b", "[已隐藏]");
    }

    void showMedicationEditor(LunaDatabase.MedicationRow existing) {
        LinearLayout form = verticalForm();
        TextInputEditText brand = addField(form, "商品名", existing == null ? "" : existing.brandName, false);
        TextInputEditText generic = addField(form, "通用名", existing == null ? "" : existing.genericName, false);
        TextInputEditText ingredients = addField(form, "有效成分", existing == null ? "" : existing.ingredients, true);
        TextInputEditText strength = addField(form, "规格", existing == null ? "" : existing.strength, false);
        TextInputEditText dosage = addField(form, "剂型", existing == null ? "" : existing.dosageForm, false);
        TextInputEditText manufacturer = addField(form, "厂家", existing == null ? "" : existing.manufacturer, false);
        TextInputEditText approval = addField(form, "批准文号", existing == null ? "" : existing.approvalNo, false);
        TextInputEditText indication = addField(form, "用途/适应症", existing == null ? "" : existing.indication, true);
        TextInputEditText contraindications = addField(form, "禁忌/注意事项", existing == null ? "" : existing.contraindications, true);
        TextInputEditText notes = addField(form, "备注", existing == null ? "" : existing.notes, true);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle(existing == null ? "添加药物" : "编辑药物").setView(scroll).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String brandValue = value(brand), genericValue = value(generic);
            if (brandValue.isEmpty() && genericValue.isEmpty()) { brand.setError("至少填写商品名或通用名"); return; }
            LunaDatabase.MedicationDraft draft = new LunaDatabase.MedicationDraft();
            draft.brandName = brandValue; draft.genericName = genericValue; draft.ingredients = value(ingredients); draft.strength = value(strength); draft.dosageForm = value(dosage); draft.manufacturer = value(manufacturer); draft.approvalNo = value(approval); draft.indication = value(indication); draft.contraindications = value(contraindications); draft.notes = value(notes); draft.status = existing == null ? "ACTIVE" : existing.status;
            if (existing == null) database.addMedication(draft); else database.updateMedication(existing.id, draft);
            dialog.dismiss(); scheduler.rebuild(); showTab(TAB_MEDICATIONS); feedback(existing == null ? "药物已添加" : "药物已保存");
        }));
        dialog.show();
    }

    void showMedicationMenu(LunaDatabase.MedicationRow medication) {
        String toggle = "ACTIVE".equals(medication.status) ? "停用药物" : "恢复药物";
        new MaterialAlertDialogBuilder(this).setTitle(medication.displayName()).setItems(new String[]{toggle, "删除药物"}, (d, which) -> {
            if (which == 0) { database.setMedicationStatus(medication.id, "ACTIVE".equals(medication.status) ? "ARCHIVED" : "ACTIVE"); scheduler.rebuild(); showTab(TAB_MEDICATIONS); }
            else new MaterialAlertDialogBuilder(this).setTitle("删除这个药物？").setMessage("将删除该药物、批次、计划和资料索引，不能撤销。")
                    .setNegativeButton("取消", null).setPositiveButton("删除", (x, y) -> { database.deleteMedication(medication.id); scheduler.rebuild(); showTab(TAB_MEDICATIONS); feedback("药物已删除"); }).show();
        }).show();
    }

    void showBatchManager(LunaDatabase.MedicationRow medication) {
        LinearLayout list = verticalForm();
        List<LunaDatabase.BatchRow> batches = database.batches(medication.id);
        if (batches.isEmpty()) list.addView(label("还没有实际药盒批次；没有批次时服用不会伪造扣减。"));
        for (LunaDatabase.BatchRow row : batches) {
            LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
            TextView summary = label("批号 " + textOr(row.lotNo, "未填写") + " · " + formatQuantity(row.quantity) + row.quantityUnit + " · " + batchState(row.state) + " · 到期 " + textOr(row.expiryDate, "未填写"));
            line.addView(summary, new LinearLayout.LayoutParams(0, dp(52), 1));
            MaterialButton edit = button("编辑"); edit.setOnClickListener(v -> showBatchEditor(medication, row)); line.addView(edit);
            MaterialButton delete = button("删除"); delete.setTextColor(palette.danger); delete.setOnClickListener(v -> new MaterialAlertDialogBuilder(this).setTitle("删除这个批次？").setMessage("将删除批号和库存记录，不能撤销。").setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> { database.deleteBatch(row.id); showBatchManager(medication); }).show()); line.addView(delete);
            list.addView(line);
        }
        MaterialButton add = button("添加实际批次"); add.setOnClickListener(v -> showBatchEditor(medication, null)); list.addView(add, margin(0, 8, 0, 0));
        ScrollView scroll = new ScrollView(this); scroll.addView(list);
        new MaterialAlertDialogBuilder(this).setTitle(medication.displayName() + " · 实际批次").setView(scroll).setPositiveButton("关闭", null).show();
    }

    private void showBatchEditor(LunaDatabase.MedicationRow medication, LunaDatabase.BatchRow existing) {
        LinearLayout form = verticalForm();
        TextInputEditText lot = addField(form, "批号", existing == null ? "" : existing.lotNo, false);
        TextInputEditText quantity = addField(form, "数量", existing == null ? "0" : Double.toString(existing.quantity), false); quantity.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextInputEditText unit = addField(form, "数量单位，例如片/瓶", existing == null ? "片" : existing.quantityUnit, false);
        TextInputEditText production = addField(form, "生产日期 YYYY-MM-DD", existing == null ? "" : existing.productionDate, false);
        TextInputEditText expiry = addField(form, "有效期 YYYY-MM-DD", existing == null ? "" : existing.expiryDate, false);
        TextInputEditText opened = addField(form, "开封日期 YYYY-MM-DD", existing == null ? "" : existing.openedDate, false);
        TextInputEditText location = addField(form, "存放位置", existing == null ? "" : existing.storageLocation, false);
        TextInputEditText conditions = addField(form, "储存条件", existing == null ? "" : existing.storageConditions, false);
        AutoCompleteTextView state = addChoice(form, "批次状态", new String[]{"有库存", "已开封", "已过期", "已丢弃"}, batchState(existing == null ? "IN_STOCK" : existing.state));
        TextInputEditText notes = addField(form, "备注", existing == null ? "" : existing.notes, true);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle(existing == null ? "添加实际批次" : "编辑实际批次").setView(scroll).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            double number; try { number = Double.parseDouble(value(quantity)); } catch (Exception error) { quantity.setError("请输入数字"); return; }
            if (number < 0 || !validDate(value(production)) || !validDate(value(expiry)) || !validDate(value(opened))) { feedback("日期必须为空或 YYYY-MM-DD，数量不能为负"); return; }
            LunaDatabase.BatchDraft draft = new LunaDatabase.BatchDraft(); draft.lotNo=value(lot); draft.quantity=number; draft.quantityUnit=value(unit); draft.productionDate=value(production); draft.expiryDate=value(expiry); draft.openedDate=value(opened); draft.storageLocation=value(location); draft.storageConditions=value(conditions); draft.state=batchStateCode(state.getText().toString()); draft.notes=value(notes);
            if (existing == null) database.addBatch(medication.id, draft); else database.updateBatch(existing.id, draft);
            dialog.dismiss(); scheduler.rebuild(); showTab(TAB_MEDICATIONS); feedback("批次已保存");
        }));
        dialog.show();
    }

    void showPlanManager(LunaDatabase.MedicationRow medication) {
        LinearLayout list = verticalForm();
        List<LunaDatabase.PlanRow> plans = database.plans(medication.id);
        if (plans.isEmpty()) list.addView(label("暂无提醒计划；按需用药可以只记录实际服用。"));
        for (LunaDatabase.PlanRow row : plans) {
            LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
            TextView summary = label(planLabel(row)); line.addView(summary, new LinearLayout.LayoutParams(0, dp(58), 1));
            MaterialButton edit = button("编辑"); edit.setOnClickListener(v -> showPlanEditor(medication, row)); line.addView(edit);
            MaterialButton pause = button(row.paused ? "恢复" : "暂停"); pause.setOnClickListener(v -> { database.setPlanPaused(row.id, !row.paused); scheduler.rebuild(); showPlanManager(medication); }); line.addView(pause);
            MaterialButton delete = button("删除"); delete.setTextColor(palette.danger); delete.setOnClickListener(v -> new MaterialAlertDialogBuilder(this).setTitle("删除这个提醒计划？").setMessage("将删除尚未发生的提醒记录，不能撤销。").setNegativeButton("取消", null).setPositiveButton("删除", (d, w) -> { database.deletePlan(row.id); scheduler.rebuild(); showPlanManager(medication); }).show()); line.addView(delete); list.addView(line);
        }
        MaterialButton add = button("添加提醒计划"); add.setOnClickListener(v -> showPlanEditor(medication, null)); list.addView(add, margin(0, 8, 0, 0));
        ScrollView scroll = new ScrollView(this); scroll.addView(list);
        new MaterialAlertDialogBuilder(this).setTitle(medication.displayName() + " · 用药计划").setView(scroll).setPositiveButton("关闭", null).show();
    }

    private void showPlanEditor(LunaDatabase.MedicationRow medication, LunaDatabase.PlanRow existing) {
        LinearLayout form = verticalForm();
        String[] types = {"每天", "每周", "按间隔", "按需", "临时疗程"};
        AutoCompleteTextView type = addChoice(form, "计划类型", types, planType(existing == null ? "DAILY" : existing.type));
        TextInputEditText times = addField(form, "时间，例如 08:00,20:00", existing == null ? "08:00" : existing.timesCsv, false);
        TextInputEditText interval = addField(form, "间隔小时", existing == null ? "8" : Integer.toString(existing.intervalHours), false);
        TextView weekLabel = label("每周选择日期"); form.addView(weekLabel, margin(0, 6, 0, 2));
        com.google.android.material.chip.ChipGroup days = new com.google.android.material.chip.ChipGroup(this); days.setSingleSelection(false);
        String[] dayNames = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (int i = 0; i < dayNames.length; i++) { com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this); chip.setText(dayNames[i]); chip.setCheckable(true); chip.setChecked(existing == null || (existing.weekdaysMask & (1 << i)) != 0); days.addView(chip); }
        form.addView(days);
        TextInputEditText start = addField(form, "开始日期 YYYY-MM-DD", existing == null ? LocalDate.now().toString() : existing.startDate, false);
        TextInputEditText end = addField(form, "结束日期 YYYY-MM-DD（可空）", existing == null ? "" : existing.endDate, false);
        TextInputEditText dose = addField(form, "单次剂量", existing == null ? "1 次" : existing.dose, false);
        TextInputEditText meal = addField(form, "饭前/饭后/随餐", existing == null ? "" : existing.meal, false);
        TextInputEditText notes = addField(form, "计划备注", existing == null ? "" : existing.notes, true);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle(existing == null ? "添加提醒计划" : "编辑提醒计划").setMessage("系统会按未来 30 天生成提醒；按需计划不自动生成闹钟。").setView(scroll).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String typeCode = planTypeCode(type.getText().toString()); String timeCsv = value(times); String startDate = value(start); String endDate = value(end);
            if (!"PRN".equals(typeCode) && !OccurrenceEngine.validTimeList(timeCsv)) { times.setError("时间格式应为 HH:mm，可用逗号分隔"); return; }
            if (!validDate(startDate) || !validDate(endDate) || (!endDate.isEmpty() && endDate.compareTo(startDate) < 0)) { feedback("日期必须为空或 YYYY-MM-DD，结束日期不能早于开始日期"); return; }
            int mask = 0; for (int i=0;i<days.getChildCount();i++) if (((com.google.android.material.chip.Chip)days.getChildAt(i)).isChecked()) mask |= 1 << i; if (mask == 0) mask = 127;
            LunaDatabase.PlanDraft draft = new LunaDatabase.PlanDraft(); draft.type=typeCode; draft.timesCsv="PRN".equals(typeCode)?"08:00":timeCsv; draft.weekdaysMask=mask; draft.intervalHours=Math.max(1, parseInt(value(interval), 8)); draft.startDate=startDate; draft.endDate=endDate; draft.dose=value(dose); draft.meal=value(meal); draft.notes=value(notes); draft.paused=existing != null && existing.paused;
            if (existing == null) database.addPlan(medication.id, draft); else database.updatePlan(existing.id, draft);
            dialog.dismiss(); scheduler.rebuild(); showTab(TAB_MEDICATIONS); feedback("提醒计划已保存");
        }));
        dialog.show();
    }

    private TextInputEditText addField(LinearLayout form, String hint, String initial, boolean multiLine) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(hint); layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText field = new TextInputEditText(this); field.setSingleLine(!multiLine); if (multiLine) { field.setMinLines(3); field.setGravity(Gravity.TOP); }
        field.setText(initial == null ? "" : initial); field.setTextSize(15); layout.addView(field, new LinearLayout.LayoutParams(-1, multiLine ? dp(88) : dp(58))); form.addView(layout, margin(0, 4, 0, 2)); return field;
    }

    private AutoCompleteTextView addChoice(LinearLayout form, String hint, String[] values, String initial) {
        TextInputLayout layout = new TextInputLayout(this); layout.setHint(hint); layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        AutoCompleteTextView field = new AutoCompleteTextView(this); field.setInputType(InputType.TYPE_NULL); field.setText(initial, false); field.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, values)); layout.addView(field, new LinearLayout.LayoutParams(-1, dp(58))); form.addView(layout, margin(0, 4, 0, 2)); return field;
    }

    private MaterialButton button(String text) { MaterialButton button = new MaterialButton(this); button.setText(text); button.setAllCaps(false); button.setMinHeight(dp(48)); return button; }
    private LinearLayout verticalForm() { LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(8), dp(4), dp(8), dp(4)); return form; }
    private TextView label(String text) { TextView view = new TextView(this); view.setText(text); view.setTextColor(palette.secondary); view.setTextSize(14); view.setLineSpacing(2, 1); return view; }
    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) { LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(dp(left), dp(top), dp(right), dp(bottom)); return params; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String value(EditText edit) { return edit.getText() == null ? "" : edit.getText().toString().trim(); }
    private static String textOr(String value, String fallback) { return value == null || value.isEmpty() ? fallback : value; }
    private static int parseInt(String value, int fallback) { try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; } }
    private static boolean validDate(String value) { if (value == null || value.isEmpty()) return true; try { LocalDate.parse(value); return value.matches("20\\d{2}-\\d{2}-\\d{2}"); } catch (Exception ignored) { return false; } }
    private static String formatQuantity(double value) { return value == Math.rint(value) ? Integer.toString((int)value) : String.format(Locale.US, "%.1f", value); }
    private static String batchState(String value) { return "IN_STOCK".equals(value) ? "有库存" : "OPENED".equals(value) ? "已开封" : "EXPIRED".equals(value) ? "已过期" : "已丢弃"; }
    private static String batchStateCode(String value) { return "已开封".equals(value) ? "OPENED" : "已过期".equals(value) ? "EXPIRED" : "已丢弃".equals(value) ? "DISCARDED" : "IN_STOCK"; }
    private static String planType(String value) { return "WEEKLY".equals(value) ? "每周" : "INTERVAL".equals(value) ? "按间隔" : "PRN".equals(value) ? "按需" : "TEMP".equals(value) ? "临时疗程" : "每天"; }
    private static String planTypeCode(String value) { return "每周".equals(value) ? "WEEKLY" : "按间隔".equals(value) ? "INTERVAL" : "按需".equals(value) ? "PRN" : "临时疗程".equals(value) ? "TEMP" : "DAILY"; }
    private static String planLabel(LunaDatabase.PlanRow row) { String result = (row.paused ? "已暂停 · " : "") + planType(row.type); if ("INTERVAL".equals(row.type)) result += " · 每 " + row.intervalHours + " 小时"; else if (!"PRN".equals(row.type)) result += " · " + row.timesCsv; if ("WEEKLY".equals(row.type)) result += " · " + daysLabel(row.weekdaysMask); if (!row.dose.isEmpty()) result += " · " + row.dose; return result; }
    private static String daysLabel(int mask) { String[] days={"周一","周二","周三","周四","周五","周六","周日"}; StringBuilder s=new StringBuilder(); for(int i=0;i<7;i++) if((mask&(1<<i))!=0){if(s.length()>0)s.append("、");s.append(days[i]);} return s.length()==0?"未选择日期":s.toString(); }
}
