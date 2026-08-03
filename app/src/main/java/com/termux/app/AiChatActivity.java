package com.termux.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class AiChatActivity extends Activity {

    private static final String PREFS_NAME = "ai_chat_prefs";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_ACTIVE_PROFILE = "active_profile";

    // UI components
    private LinearLayout chatContainer;
    private ScrollView chatScrollView;
    private EditText inputEditText;
    private Button sendButton;
    private TextView topBarTitle;
    private TextView typingIndicator;

    // State
    private List<ChatProfile> profiles = new ArrayList<>();
    private int activeProfileIndex = 0;
    private List<ChatMessage> messages = new ArrayList<>();
    private boolean isStreaming = false;

    // -------------------------------------------------------
    // Data classes
    // -------------------------------------------------------

    static class ChatProfile {
        String name;
        String url;
        String key;
        String model;
        String charName;
        // SSH 配置
        String sshHost;
        String sshUser;
        String sshPass;
        int sshPort;

        ChatProfile(String name, String url, String key, String model, String charName) {
            this.name = name;
            this.url = url;
            this.key = key;
            this.model = model;
            this.charName = charName;
            this.sshHost = "";
            this.sshUser = "root";
            this.sshPass = "";
            this.sshPort = 22;
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("url", url);
            o.put("key", key);
            o.put("model", model);
            o.put("charName", charName);
            o.put("sshHost", sshHost);
            o.put("sshUser", sshUser);
            o.put("sshPass", sshPass);
            o.put("sshPort", sshPort);
            return o;
        }

        static ChatProfile fromJson(JSONObject o) throws Exception {
            ChatProfile p = new ChatProfile(
                o.optString("name", "默认"),
                o.optString("url", ""),
                o.optString("key", ""),
                o.optString("model", "gpt-4o"),
                o.optString("charName", "AI")
            );
            p.sshHost = o.optString("sshHost", "");
            p.sshUser = o.optString("sshUser", "root");
            p.sshPass = o.optString("sshPass", "");
            p.sshPort = o.optInt("sshPort", 22);
            return p;
        }

        boolean hasSsh() {
            return !sshHost.isEmpty() && !sshUser.isEmpty() && !sshPass.isEmpty();
        }
    }

    static class ChatMessage {
        boolean isUser;
        String content;
        boolean isCode;

        ChatMessage(boolean isUser, String content) {
            this.isUser = isUser;
            this.content = content;
            this.isCode = false;
        }
    }

    // -------------------------------------------------------
    // Activity lifecycle
    // -------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadProfiles();
        buildUI();
    }

    // -------------------------------------------------------
    // Profile persistence
    // -------------------------------------------------------

    private void loadProfiles() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_PROFILES, "");
        activeProfileIndex = prefs.getInt(KEY_ACTIVE_PROFILE, 0);
        profiles.clear();
        try {
            if (!TextUtils.isEmpty(json)) {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    profiles.add(ChatProfile.fromJson(arr.getJSONObject(i)));
                }
            }
        } catch (Exception e) {
            // ignore
        }
        if (profiles.isEmpty()) {
            profiles.add(new ChatProfile("默认", "", "", "", "AI"));
        }
        if (activeProfileIndex >= profiles.size()) activeProfileIndex = 0;
    }

    private void saveProfiles() {
        try {
            JSONArray arr = new JSONArray();
            for (ChatProfile p : profiles) arr.put(p.toJson());
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_PROFILES, arr.toString())
                .putInt(KEY_ACTIVE_PROFILE, activeProfileIndex)
                .apply();
        } catch (Exception e) {
            // ignore
        }
    }

    private ChatProfile activeProfile() {
        return profiles.get(activeProfileIndex);
    }

    // -------------------------------------------------------
    // UI construction (programmatic, no layout XML needed)
    // -------------------------------------------------------

    private void buildUI() {
        initTextScale();
        // 外层 FrameLayout，用于叠加悬浮球
        FrameLayout outerFrame = new FrameLayout(this);
        outerFrame.setBackgroundColor(Color.parseColor("#0D1117"));
        setContentView(outerFrame);

        // Root layout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0D1117"));
        outerFrame.addView(root, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Top bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(Color.parseColor("#161B22"));
        topBar.setPadding(dp(8), dp(8), dp(8), dp(8));
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        // Use text-based button since we may not have drawable resources
        Button backBtnTxt = new Button(this);
        backBtnTxt.setText("←");
        backBtnTxt.setTextColor(Color.WHITE);
        backBtnTxt.setBackgroundColor(Color.TRANSPARENT);
        backBtnTxt.setTextSize(sp(18));
        backBtnTxt.setOnClickListener(v -> finish());
        topBar.addView(backBtnTxt, new LinearLayout.LayoutParams(dp(48), dp(40)));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        topBarTitle = new TextView(this);
        topBarTitle.setTextColor(Color.WHITE);
        topBarTitle.setTextSize(sp(15));
        topBarTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        updateTopBarTitle();
        titleCol.addView(topBarTitle);
        TextView statusTv = new TextView(this);
        statusTv.setText("● 在线");
        statusTv.setTextColor(Color.parseColor("#3FB950"));
        statusTv.setTextSize(sp(11));
        titleCol.addView(statusTv);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        topBar.addView(titleCol, titleParams);

        Button profileBtn = new Button(this);
        profileBtn.setText("⚙ 配置");
        profileBtn.setTextColor(Color.parseColor("#58A6FF"));
        profileBtn.setBackgroundColor(Color.TRANSPARENT);
        profileBtn.setTextSize(sp(13));
        profileBtn.setOnClickListener(v -> showProfileDialog());
        topBar.addView(profileBtn, new LinearLayout.LayoutParams(dp(80), dp(40)));

        root.addView(topBar, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Chat scroll area
        chatScrollView = new ScrollView(this);
        chatScrollView.setBackgroundColor(Color.parseColor("#0D1117"));
        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setPadding(dp(8), dp(8), dp(8), dp(8));
        chatScrollView.addView(chatContainer);
        root.addView(chatScrollView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // Typing indicator
        typingIndicator = new TextView(this);
        typingIndicator.setTextColor(Color.parseColor("#8B949E"));
        typingIndicator.setTextSize(sp(12));
        typingIndicator.setPadding(dp(12), dp(4), dp(12), dp(4));
        typingIndicator.setVisibility(View.GONE);
        root.addView(typingIndicator, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Input bar
        LinearLayout inputBar = new LinearLayout(this);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setBackgroundColor(Color.parseColor("#161B22"));
        inputBar.setPadding(dp(8), dp(6), dp(8), dp(6));
        inputBar.setGravity(Gravity.CENTER_VERTICAL);

        inputEditText = new EditText(this);
        inputEditText.setHint("输入消息... (支持命令如: ls, pwd)");
        inputEditText.setHintTextColor(Color.parseColor("#484F58"));
        inputEditText.setTextColor(Color.WHITE);
        inputEditText.setBackgroundColor(Color.parseColor("#21262D"));
        inputEditText.setPadding(dp(12), dp(8), dp(12), dp(8));
        inputEditText.setTextSize(sp(14));
        inputEditText.setMaxLines(4);
        inputEditText.setImeOptions(EditorInfo.IME_ACTION_NONE);
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        etParams.setMarginEnd(dp(8));
        inputBar.addView(inputEditText, etParams);

        sendButton = new Button(this);
        sendButton.setText("发送");
        sendButton.setTextColor(Color.WHITE);
        sendButton.setBackgroundColor(Color.parseColor("#1F6FEB"));
        sendButton.setTextSize(sp(14));
        sendButton.setPadding(dp(16), dp(8), dp(16), dp(8));
        sendButton.setOnClickListener(v -> onSendClicked());
        inputBar.addView(sendButton, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(inputBar, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Welcome message
        addAiMessage("你好！请先点右上角 ⚙ 配置 API 地址和 Key，然后就可以开始聊天了。");

        // 悬浮球 - 右下角，点击打开配置
        Button fab = new Button(this);
        fab.setText("⚙");
        fab.setTextColor(Color.WHITE);
        fab.setTextSize(sp(18));
        fab.setBackgroundColor(Color.parseColor("#1F6FEB"));
        fab.setPadding(0, 0, 0, 0);
        // 圆形外观
        android.graphics.drawable.GradientDrawable fabBg = new android.graphics.drawable.GradientDrawable();
        fabBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        fabBg.setColor(Color.parseColor("#1F6FEB"));
        fab.setBackground(fabBg);

        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(dp(56), dp(56));
        fabLp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
        fabLp.setMargins(0, 0, dp(20), dp(80));
        outerFrame.addView(fab, fabLp);

        // 支持拖动
        fab.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY;
            long downTime;
            boolean moved;
            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                switch (e.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        dX = v.getX() - e.getRawX();
                        dY = v.getY() - e.getRawY();
                        downTime = System.currentTimeMillis();
                        moved = false;
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        float nx = e.getRawX() + dX;
                        float ny = e.getRawY() + dY;
                        v.setX(nx);
                        v.setY(ny);
                        moved = true;
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                        if (!moved || System.currentTimeMillis() - downTime < 200) {
                            showProfileDialog();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void updateTopBarTitle() {
        if (topBarTitle != null) {
            topBarTitle.setText(activeProfile().charName + " ▾  [" + activeProfile().name + "]");
        }
    }

    private int dp(int val) {
        float density = getResources().getDisplayMetrics().density;
        return (int)(val * density);
    }

    private float textScale = 1f;

    private void initTextScale() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float screenWidthDp = dm.widthPixels / dm.density;
        // 基准 360dp，最小 0.85x，最大 1.35x
        textScale = Math.max(0.85f, Math.min(1.35f, screenWidthDp / 360f));
    }

    private float sp(float base) {
        return base * textScale;
    }

    // -------------------------------------------------------
    // Chat message rendering
    // -------------------------------------------------------

    private void addUserMessage(String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(dp(40), dp(4), 0, dp(4));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundColor(Color.parseColor("#1F6FEB"));
        bubble.setPadding(dp(12), dp(8), dp(12), dp(8));

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(sp(14));
        bubble.addView(tv);

        TextView ts = new TextView(this);
        ts.setText(getTimestamp());
        ts.setTextColor(Color.parseColor("#AACCFF"));
        ts.setTextSize(sp(10));
        ts.setGravity(Gravity.END);
        bubble.addView(ts);

        row.addView(bubble);
        chatContainer.addView(row);
        scrollToBottom();
    }

    private void addAiMessage(String text) {
        ChatProfile p = activeProfile();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), dp(40), dp(4));

        // Avatar
        TextView avatar = new TextView(this);
        avatar.setText("🤖");
        avatar.setTextSize(sp(24));
        avatar.setPadding(0, 0, dp(8), 0);
        avatar.setGravity(Gravity.TOP);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);

        TextView nameTv = new TextView(this);
        nameTv.setText(p.charName);
        nameTv.setTextColor(Color.parseColor("#58A6FF"));
        nameTv.setTextSize(sp(11));
        right.addView(nameTv);

        // Check if content has code blocks
        if (text.contains("```")) {
            addMixedContent(right, text);
        } else {
            LinearLayout bubble = new LinearLayout(this);
            bubble.setBackgroundColor(Color.parseColor("#161B22"));
            bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextColor(Color.parseColor("#E6EDF3"));
            tv.setTextSize(sp(14));
            bubble.addView(tv);
            right.addView(bubble);
        }

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        row.addView(right, rp);
        chatContainer.addView(row);
        scrollToBottom();
    }

    private void addMixedContent(LinearLayout parent, String text) {
        String[] parts = text.split("```");
        for (int i = 0; i < parts.length; i++) {
            if (i % 2 == 0) {
                // Normal text
                if (!parts[i].trim().isEmpty()) {
                    LinearLayout bubble = new LinearLayout(this);
                    bubble.setBackgroundColor(Color.parseColor("#161B22"));
                    bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
                    TextView tv = new TextView(this);
                    tv.setText(parts[i].trim());
                    tv.setTextColor(Color.parseColor("#E6EDF3"));
                    tv.setTextSize(sp(14));
                    bubble.addView(tv);
                    parent.addView(bubble);
                }
            } else {
                // Code block
                String codeContent = parts[i];
                String lang = "bash";
                if (codeContent.contains("\n")) {
                    lang = codeContent.substring(0, codeContent.indexOf("\n")).trim();
                    codeContent = codeContent.substring(codeContent.indexOf("\n") + 1);
                }
                final String finalCode = codeContent.trim();
                final String finalLang = lang.isEmpty() ? "bash" : lang;

                LinearLayout codeBlock = new LinearLayout(this);
                codeBlock.setOrientation(LinearLayout.VERTICAL);
                codeBlock.setBackgroundColor(Color.parseColor("#0D1117"));
                codeBlock.setPadding(0, 0, 0, dp(4));

                // Code header bar
                LinearLayout header = new LinearLayout(this);
                header.setOrientation(LinearLayout.HORIZONTAL);
                header.setBackgroundColor(Color.parseColor("#161B22"));
                header.setPadding(dp(12), dp(4), dp(8), dp(4));
                header.setGravity(Gravity.CENTER_VERTICAL);

                TextView langTv = new TextView(this);
                langTv.setText(finalLang);
                langTv.setTextColor(Color.parseColor("#8B949E"));
                langTv.setTextSize(sp(12));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                header.addView(langTv, lp);

                Button execBtn = new Button(this);
                execBtn.setText("▶ 执行");
                execBtn.setTextColor(Color.WHITE);
                execBtn.setBackgroundColor(Color.parseColor("#238636"));
                execBtn.setTextSize(sp(11));
                execBtn.setPadding(dp(8), dp(2), dp(8), dp(2));
                execBtn.setOnClickListener(v -> executeInTermux(finalCode));
                header.addView(execBtn, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(30)));

                Button copyBtn = new Button(this);
                copyBtn.setText("📋");
                copyBtn.setTextColor(Color.WHITE);
                copyBtn.setBackgroundColor(Color.parseColor("#21262D"));
                copyBtn.setTextSize(sp(11));
                copyBtn.setPadding(dp(8), dp(2), dp(8), dp(2));
                copyBtn.setOnClickListener(v -> copyToClipboard(finalCode));
                LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(30));
                cbp.setMarginStart(dp(4));
                header.addView(copyBtn, cbp);

                codeBlock.addView(header);

                // Code text
                TextView codeTv = new TextView(this);
                codeTv.setText(finalCode);
                codeTv.setTextColor(Color.parseColor("#98C379"));
                codeTv.setTypeface(android.graphics.Typeface.MONOSPACE);
                codeTv.setTextSize(sp(13));
                codeTv.setPadding(dp(12), dp(8), dp(12), dp(8));
                codeTv.setBackgroundColor(Color.parseColor("#0D1117"));
                codeBlock.addView(codeTv);

                parent.addView(codeBlock);
            }
        }
    }

    private void scrollToBottom() {
        chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private String getTimestamp() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date());
    }


    // -------------------------------------------------------
    // Send / API
    // -------------------------------------------------------

    private void onSendClicked() {
        String text = inputEditText.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        if (isStreaming) {
            Toast.makeText(this, "正在等待回复...", Toast.LENGTH_SHORT).show();
            return;
        }
        inputEditText.setText("");
        addUserMessage(text);
        messages.add(new ChatMessage(true, text));
        callAiApi(text);
    }

    private void callAiApi(String userMsg) {
        ChatProfile p = activeProfile();
        if (TextUtils.isEmpty(p.url) || TextUtils.isEmpty(p.key)) {
            addAiMessage("⚠️ 请先配置 API 地址和 Key（点右上角 ⚙ 配置）");
            return;
        }

        isStreaming = true;
        sendButton.setEnabled(false);
        typingIndicator.setText(p.charName + " 正在输入...");
        typingIndicator.setVisibility(View.VISIBLE);

        // Build request JSON
        new Thread(() -> {
            try {
                String baseUrl = p.url.endsWith("/") ? p.url : p.url + "/";
                URL url = new URL(baseUrl + "chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + p.key);
                conn.setDoOutput(true);
                conn.setReadTimeout(60000);
                conn.setConnectTimeout(15000);

                JSONArray msgArr = new JSONArray();
                // Add system prompt
                JSONObject sys = new JSONObject();
                sys.put("role", "system");
                sys.put("content", "你是 Hermes，一个智能 AI 助手，帮助用户管理服务器和执行各种任务。" +
                    "当用户需要执行命令时，用代码块包裹命令（```bash\\n命令\\n```），用户可以一键执行。" +
                    "需要执行命令时用代码块包裹（```bash\\n命令\\n```），回答简洁直接。");
                msgArr.put(sys);

                // Add history (last 20 messages)
                int start = Math.max(0, messages.size() - 20);
                for (int i = start; i < messages.size(); i++) {
                    ChatMessage m = messages.get(i);
                    JSONObject mo = new JSONObject();
                    mo.put("role", m.isUser ? "user" : "assistant");
                    mo.put("content", m.content);
                    msgArr.put(mo);
                }

                JSONObject body = new JSONObject();
                body.put("model", p.model);
                body.put("messages", msgArr);
                body.put("stream", false);
                body.put("max_tokens", 2048);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();
                BufferedReader reader;
                if (code == 200) {
                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                } else {
                    reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                }
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                final String respStr = sb.toString();
                new Handler(Looper.getMainLooper()).post(() -> {
                    typingIndicator.setVisibility(View.GONE);
                    isStreaming = false;
                    sendButton.setEnabled(true);
                    try {
                        if (code == 200) {
                            JSONObject resp = new JSONObject(respStr);
                            String content = resp.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");
                            messages.add(new ChatMessage(false, content));
                            addAiMessage(content);
                        } else {
                            addAiMessage("❌ API 错误 " + code + "：" + respStr);
                        }
                    } catch (Exception ex) {
                        addAiMessage("❌ 解析响应失败：" + ex.getMessage());
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    typingIndicator.setVisibility(View.GONE);
                    isStreaming = false;
                    sendButton.setEnabled(true);
                    addAiMessage("❌ 网络错误：" + e.getMessage());
                });
            }
        }).start();
    }

    // -------------------------------------------------------
    // Terminal execution
    // -------------------------------------------------------

    private void executeInTermux(String command) {
        ChatProfile p = activeProfile();
        addAiMessage("⚙ 执行中: `" + command + "`");
        if (p.hasSsh()) {
            // SSH 远程执行
            new Thread(() -> {
                try {
                    com.jcraft.jsch.JSch jsch = new com.jcraft.jsch.JSch();
                    com.jcraft.jsch.Session session = jsch.getSession(p.sshUser, p.sshHost, p.sshPort);
                    session.setPassword(p.sshPass);
                    session.setConfig("StrictHostKeyChecking", "no");
                    session.setTimeout(15000);
                    session.connect();

                    com.jcraft.jsch.ChannelExec ch = (com.jcraft.jsch.ChannelExec) session.openChannel("exec");
                    ch.setCommand(command);
                    ch.setErrStream(System.err);
                    java.io.InputStream in = ch.getInputStream();
                    ch.connect();

                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(in));
                    StringBuilder output = new StringBuilder();
                    String line;
                    int lines = 0;
                    while ((line = br.readLine()) != null && lines < 200) {
                        output.append(line).append("\n");
                        lines++;
                    }
                    if (lines >= 200) output.append("... (输出截断)");
                    ch.disconnect();
                    session.disconnect();

                    String result = output.toString().trim();
                    int exit = ch.getExitStatus();
                    String msg = result.isEmpty() ? "✅ 执行完成 (exit " + exit + ")" : "```\n" + result + "\n```\n✅ exit " + exit;
                    new Handler(Looper.getMainLooper()).post(() -> addAiMessage(msg));
                } catch (Exception e) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        addAiMessage("❌ SSH 执行失败: " + e.getMessage()));
                }
            }).start();
        } else {
            // 本地执行
            new Thread(() -> {
                StringBuilder output = new StringBuilder();
                try {
                    String bash = com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/bash";
                    java.io.File bashFile = new java.io.File(bash);
                    String[] cmd;
                    ProcessBuilder pb;
                    if (bashFile.exists() && bashFile.canExecute()) {
                        cmd = new String[]{bash, "-lc", command};
                        pb = new ProcessBuilder(cmd);
                        pb.environment().put("HOME", com.termux.shared.termux.TermuxConstants.TERMUX_FILES_DIR_PATH + "/home");
                        pb.environment().put("PATH", com.termux.shared.termux.TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH
                            + ":/system/bin:/system/xbin");
                        pb.environment().put("PREFIX", com.termux.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH);
                    } else {
                        cmd = new String[]{"/system/bin/sh", "-c", command};
                        pb = new ProcessBuilder(cmd);
                        pb.environment().put("HOME", "/data/local/tmp");
                        pb.environment().put("PATH", "/system/bin:/system/xbin");
                    }
                    pb.redirectErrorStream(true);
                    Process proc = pb.start();

                    java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(proc.getInputStream()));
                    String line;
                    int lines = 0;
                    while ((line = br.readLine()) != null && lines < 200) {
                        output.append(line).append("\n");
                        lines++;
                    }
                    if (lines >= 200) output.append("... (输出截断)");
                    boolean finished = proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
                    if (!finished) {
                        proc.destroyForcibly();
                        output.append("\n[超时，已终止]");
                    }
                    int exit = finished ? proc.exitValue() : -1;
                    String result = output.toString().trim();
                    String msg = result.isEmpty() ? "✅ 执行完成 (exit " + exit + ")" : "```\n" + result + "\n```\n✅ exit " + exit;
                    new Handler(Looper.getMainLooper()).post(() -> addAiMessage(msg));
                } catch (Exception e) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        addAiMessage("❌ 执行失败: " + e.getMessage()));
                }
            }).start();
        }
    }

    private void copyToClipboard(String text) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
            getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("code", text));
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }


    // -------------------------------------------------------
    // Profile config dialog
    // -------------------------------------------------------

    private void showProfileDialog() {
        // 用全屏自定义 Dialog，把按钮放进 ScrollView 里，彻底避开状态栏遮挡
        android.app.Dialog dlg = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dlg.setContentView(buildProfileDialogView(dlg));
        dlg.show();
    }

    private View buildProfileDialogView(android.app.Dialog dlg) {
        // 根布局：深色背景全屏
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0D1117"));
        root.setFitsSystemWindows(true);

        // 标题栏
        TextView title = new TextView(this);
        title.setText("AI 配置");
        title.setTextColor(Color.WHITE);
        title.setTextSize(sp(16));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(16), dp(16), dp(16), dp(8));
        root.addView(title);

        // 分割线
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#30363D"));
        root.addView(divider, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        // 可滚动内容区
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(sv, svLp);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(8), dp(16), dp(8));
        sv.addView(layout);

        // Profile selector
        TextView profileLabel = new TextView(this);
        profileLabel.setText("选择配置");
        profileLabel.setTextColor(Color.parseColor("#8B949E"));
        profileLabel.setTextSize(sp(12));
        layout.addView(profileLabel);

        String[] profileNames = new String[profiles.size() + 1];
        for (int i = 0; i < profiles.size(); i++) profileNames[i] = profiles.get(i).name;
        profileNames[profiles.size()] = "+ 新建配置";

        Spinner profileSpinner = new Spinner(this);
        ArrayAdapter<String> spAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, profileNames);
        spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profileSpinner.setAdapter(spAdapter);
        profileSpinner.setSelection(activeProfileIndex);
        layout.addView(profileSpinner);

        // Fields
        ChatProfile cur = activeProfile();
        EditText etName  = makeField(layout, "配置名称", cur.name);
        EditText etChar  = makeField(layout, "角色名", cur.charName);
        EditText etUrl   = makeField(layout, "API 地址 (如 https://api.openai.com/v1)", cur.url);
        EditText etKey   = makeField(layout, "API Key", cur.key);
        EditText etModel = makeField(layout, "模型名称", cur.model);

        // SSH 配置分割线
        View sshDivider = new View(this);
        sshDivider.setBackgroundColor(Color.parseColor("#30363D"));
        LinearLayout.LayoutParams sshDivLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        sshDivLp.topMargin = dp(12);
        layout.addView(sshDivider, sshDivLp);

        TextView sshLabel = new TextView(this);
        sshLabel.setText("🖥 SSH 配置（填写后执行命令走远程服务器）");
        sshLabel.setTextColor(Color.parseColor("#58A6FF"));
        sshLabel.setTextSize(sp(12));
        sshLabel.setPadding(0, dp(8), 0, 0);
        layout.addView(sshLabel);

        EditText etSshHost = makeField(layout, "服务器地址 (如 154.219.98.93)", cur.sshHost);
        EditText etSshPort = makeField(layout, "SSH 端口 (默认22)", cur.sshPort > 0 ? String.valueOf(cur.sshPort) : "22");
        EditText etSshUser = makeField(layout, "用户名 (如 root)", cur.sshUser);
        EditText etSshPass = makeField(layout, "密码", cur.sshPass);
        // 密码框隐藏输入
        etSshPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        // Fetch models button
        Button fetchBtn = new Button(this);
        fetchBtn.setText("🔄 从服务器拉取模型列表");
        fetchBtn.setTextColor(Color.WHITE);
        fetchBtn.setBackgroundColor(Color.parseColor("#21262D"));
        fetchBtn.setTextSize(sp(13));
        fetchBtn.setOnClickListener(v -> fetchModels(etUrl.getText().toString().trim(),
            etKey.getText().toString().trim(), etModel));
        LinearLayout.LayoutParams fetchLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fetchLp.topMargin = dp(12);
        layout.addView(fetchBtn, fetchLp);

        // Profile spinner -> fill fields
        profileSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                if (pos < profiles.size()) {
                    ChatProfile p = profiles.get(pos);
                    etName.setText(p.name);
                    etChar.setText(p.charName);
                    etUrl.setText(p.url);
                    etKey.setText(p.key);
                    etModel.setText(p.model);
                    etSshHost.setText(p.sshHost);
                    etSshPort.setText(String.valueOf(p.sshPort > 0 ? p.sshPort : 22));
                    etSshUser.setText(p.sshUser);
                    etSshPass.setText(p.sshPass);
                } else {
                    etName.setText("新配置");
                    etChar.setText("AI");
                    etUrl.setText("");
                    etKey.setText("");
                    etModel.setText("gpt-4o");
                    etSshHost.setText("");
                    etSshPort.setText("22");
                    etSshUser.setText("root");
                    etSshPass.setText("");
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // 分割线
        View divider2 = new View(this);
        divider2.setBackgroundColor(Color.parseColor("#30363D"));
        root.addView(divider2, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        // 底部按钮行（始终可见，不在 ScrollView 里）
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setBackgroundColor(Color.parseColor("#161B22"));
        btnRow.setPadding(dp(8), dp(8), dp(8), dp(8));

        Button btnDelete = new Button(this);
        btnDelete.setText("删除");
        btnDelete.setTextColor(Color.parseColor("#F85149"));
        btnDelete.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnRow.addView(btnDelete, delLp);

        Button btnCancel = new Button(this);
        btnCancel.setText("取消");
        btnCancel.setTextColor(Color.parseColor("#8B949E"));
        btnCancel.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnRow.addView(btnCancel, cancelLp);

        Button btnSave = new Button(this);
        btnSave.setText("保存");
        btnSave.setTextColor(Color.parseColor("#3FB950"));
        btnSave.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnRow.addView(btnSave, saveLp);

        root.addView(btnRow, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 按钮逻辑
        btnCancel.setOnClickListener(v -> dlg.dismiss());

        btnSave.setOnClickListener(v -> {
            int sel = profileSpinner.getSelectedItemPosition();
            String nm = etName.getText().toString().trim();
            String ch = etChar.getText().toString().trim();
            String ur = etUrl.getText().toString().trim();
            String ke = etKey.getText().toString().trim();
            String mo = etModel.getText().toString().trim();
            if (TextUtils.isEmpty(nm)) nm = "配置" + sel;
            if (TextUtils.isEmpty(ch)) ch = "AI";
            ChatProfile np = new ChatProfile(nm, ur, ke, mo, ch);
            np.sshHost = etSshHost.getText().toString().trim();
            np.sshUser = etSshUser.getText().toString().trim();
            np.sshPass = etSshPass.getText().toString().trim();
            try { np.sshPort = Integer.parseInt(etSshPort.getText().toString().trim()); } catch (Exception e2) { np.sshPort = 22; }
            if (np.sshUser.isEmpty()) np.sshUser = "root";
            if (np.sshPort <= 0) np.sshPort = 22;
            if (sel >= profiles.size()) {
                profiles.add(np);
                activeProfileIndex = profiles.size() - 1;
            } else {
                profiles.set(sel, np);
                activeProfileIndex = sel;
            }
            saveProfiles();
            updateTopBarTitle();
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
            dlg.dismiss();
        });

        btnDelete.setOnClickListener(v -> {
            int sel = profileSpinner.getSelectedItemPosition();
            if (sel < profiles.size() && profiles.size() > 1) {
                profiles.remove(sel);
                if (activeProfileIndex >= profiles.size()) activeProfileIndex = profiles.size() - 1;
                saveProfiles();
                updateTopBarTitle();
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
            }
            dlg.dismiss();
        });

        return root;
    }

    private void hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getWindow().getInsetsController().hide(android.view.WindowInsets.Type.statusBars());
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN |
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void showSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getWindow().getInsetsController().show(android.view.WindowInsets.Type.statusBars());
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private EditText makeField(LinearLayout parent, String hint, String value) {
        TextView label = new TextView(this);
        label.setText(hint);
        label.setTextColor(Color.parseColor("#8B949E"));
        label.setTextSize(sp(11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        parent.addView(label, lp);

        EditText et = new EditText(this);
        et.setText(value);
        et.setTextColor(Color.WHITE);
        et.setBackgroundColor(Color.parseColor("#0D1117"));
        et.setPadding(dp(8), dp(6), dp(8), dp(6));
        et.setTextSize(sp(13));
        et.setSingleLine(true);
        parent.addView(et);
        return et;
    }

    // -------------------------------------------------------
    // Fetch models from OpenAI-compatible endpoint
    // -------------------------------------------------------

    private void fetchModels(String baseUrl, String apiKey, EditText targetField) {
        if (TextUtils.isEmpty(baseUrl) || TextUtils.isEmpty(apiKey)) {
            Toast.makeText(this, "请先填写 API 地址和 Key", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在拉取模型列表...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String url = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "models";
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                    code == 200 ? conn.getInputStream() : conn.getErrorStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                List<String> modelIds = new ArrayList<>();
                if (code == 200) {
                    JSONObject resp = new JSONObject(sb.toString());
                    JSONArray data = resp.getJSONArray("data");
                    for (int i = 0; i < data.length(); i++) {
                        modelIds.add(data.getJSONObject(i).getString("id"));
                    }
                }

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (modelIds.isEmpty()) {
                        Toast.makeText(this, "未获取到模型列表", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Show picker dialog
                    String[] arr = modelIds.toArray(new String[0]);
                    new AlertDialog.Builder(this)
                        .setTitle("选择模型 (" + arr.length + " 个)")
                        .setItems(arr, (d, which) -> targetField.setText(arr[which]))
                        .setNegativeButton("取消", null)
                        .show();
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(this, "拉取失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}

