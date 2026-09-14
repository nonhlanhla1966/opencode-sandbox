package com.appfactory.opencodechatbot;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.appfactory.opencodechatbot.data.ChatSettings;
import com.appfactory.opencodechatbot.data.ConversationStore;
import com.appfactory.opencodechatbot.model.Conversation;
import com.appfactory.opencodechatbot.model.Message;
import com.appfactory.opencodechatbot.provider.ChatProvider;
import com.appfactory.opencodechatbot.provider.ChatException;
import com.appfactory.opencodechatbot.provider.OpenAiCompatibleProvider;
import com.appfactory.opencodechatbot.provider.ProviderConfig;
import com.appfactory.opencodechatbot.ui.MessagesAdapter;
import com.appfactory.opencodechatbot.util.AppTheme;
import com.appfactory.opencodechatbot.util.MarkdownSpanner;

import java.util.List;

/**
 * Chat screen — the app's launcher. Owns the active conversation, renders
 * messages, drives the streaming provider on a background thread and keeps
 * the UI responsive.
 */
public final class MainActivity extends Activity
        implements MessagesAdapter.OnActionListener {

    private static final int REQ_CHATS = 1;
    private static final int REQ_SETTINGS = 2;

    private static final int GRP_MSG = 1;
    private static final int ITEM_COPY = 11;
    private static final int ITEM_SHARE = 12;
    private static final int ITEM_REGENERATE = 13;
    private static final int ITEM_EDIT_RESEND = 14;
    private static final int ITEM_RETRY = 15;
    private static final int ITEM_DELETE = 16;

    private final Handler main = new Handler(Looper.getMainLooper());

    private ChatSettings settings;
    private ConversationStore store;
    private Conversation conversation;

    private ListView msgList;
    private EditText editInput;
    private Button btnSend;
    private Button btnStop;
    private TextView chatTitle;
    private MessagesAdapter adapter;

    private OpenAiCompatibleProvider provider;
    private Thread worker;
    private volatile boolean generating;
    private Message editingUserMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = new ChatSettings(this);
        AppTheme.apply(this, settings);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        store = new ConversationStore(this);
        MarkdownSpanner spanner = new MarkdownSpanner(this);
        adapter = new MessagesAdapter(this, spanner, this);

        msgList = findViewById(R.id.msg_list);
        msgList.setAdapter(adapter);
        msgList.setEmptyView(findViewById(R.id.msg_empty));
        registerForContextMenu(msgList);

        editInput = findViewById(R.id.edit_input);
        btnSend = findViewById(R.id.btn_send);
        btnStop = findViewById(R.id.btn_stop);
        chatTitle = findViewById(R.id.chat_title);

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopGeneration();
            }
        });
        findViewById(R.id.btn_new).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNewChat();
            }
        });
        findViewById(R.id.btn_chats).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(MainActivity.this, ConversationsActivity.class), REQ_CHATS);
            }
        });
        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(MainActivity.this, SettingsActivity.class), REQ_SETTINGS);
            }
        });

        loadCurrentConversation();
    }

    private void loadCurrentConversation() {
        String id = settings.getCurrentConversation();
        conversation = id != null ? store.find(id) : null;
        if (conversation == null) {
            conversation = Conversation.create("", settings.getSystemPrompt());
            settings.setCurrentConversation(conversation.getId());
        }
        refresh();
    }

    private void save() {
        if (conversation != null) {
            store.save(conversation);
        }
    }

    private void refresh() {
        String title = conversation.getTitle();
        chatTitle.setText(title == null || title.trim().isEmpty()
                ? getString(R.string.chat_untitled) : title);
        adapter.setMessages(conversation.getMessages());
        scrollToBottom();
    }

    private void scrollToBottom() {
        msgList.post(new Runnable() {
            @Override
            public void run() {
                int count = adapter.getCount();
                if (count > 0) {
                    msgList.setSelection(count - 1);
                }
            }
        });
    }

    // ------------------------------------------------------------ composition

    private void sendMessage() {
        if (generating) {
            return;
        }
        String text = editInput.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        if (editingUserMessage != null) {
            editingUserMessage.setContent(text);
            removeAfter(editingUserMessage);
            editingUserMessage = null;
        } else {
            Message user = Message.user(text);
            conversation.getMessages().add(user);
            conversation.deriveTitle();
        }
        editInput.setText("");
        save();
        refresh();
        generate();
    }

    private void removeAfter(Message anchor) {
        List<Message> msgs = conversation.getMessages();
        boolean found = false;
        java.util.Iterator<Message> it = msgs.iterator();
        while (it.hasNext()) {
            Message m = it.next();
            if (found) {
                it.remove();
            } else if (m.getId().equals(anchor.getId())) {
                found = true;
            }
        }
    }

    private void generate() {
        if (generating) {
            return;
        }
        final ProviderConfig config = settings.providerConfig();
        if (!config.isConfigured()) {
            Message error = Message.assistant(getString(R.string.error_no_provider), Message.STATUS_ERROR);
            conversation.getMessages().add(error);
            save();
            refresh();
            return;
        }

        generating = true;
        btnSend.setVisibility(View.GONE);
        btnStop.setVisibility(View.VISIBLE);
        msgList.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);

        final Message slot = Message.assistant("", Message.STATUS_SENDING);
        conversation.getMessages().add(slot);
        refresh();

        final String systemPrompt = conversation.getSystemPrompt().isEmpty()
                ? settings.getSystemPrompt() : conversation.getSystemPrompt();

        // History = everything currently in the conversation (the new user
        // message plus prior turns). The slot is not included in the payload.
        final java.util.ArrayList<Message> history =
                new java.util.ArrayList<>(conversation.getMessages());
        history.remove(history.size() - 1);

        provider = new OpenAiCompatibleProvider(config);
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    provider.send(history, systemPrompt, true, listenerFor(slot));
                } finally {
                    worker = null;
                }
            }
        }, "chat-request");
        worker.start();
    }

    private ChatProvider.Listener listenerFor(final Message slot) {
        return new ChatProvider.Listener() {
            @Override
            public void onDelta(final String text) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (slot.getStatus().equals(Message.STATUS_DONE)) {
                            return;
                        }
                        slot.setContent(slot.getContent() + safe(text));
                        slot.setStatus(Message.STATUS_PARTIAL);
                        adapter.notifyDataSetChanged();
                        scrollToBottom();
                    }
                });
            }

            @Override
            public void onComplete(final String fullText) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        String completed = safe(fullText);
                        slot.setContent(completed.isEmpty() ? slot.getContent() : completed);
                        slot.setStatus(Message.STATUS_DONE);
                        finishGeneration();
                    }
                });
            }

            @Override
            public void onError(final ChatException e) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        String msg = safe(e.getMessage());
                        slot.setContent(slot.getContent().isEmpty()
                                ? msg : slot.getContent() + "\n\n" + msg);
                        slot.setStatus(Message.STATUS_ERROR);
                        finishGeneration();
                    }
                });
            }
        };
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private void finishGeneration() {
        generating = false;
        btnStop.setVisibility(View.GONE);
        btnSend.setVisibility(View.VISIBLE);
        msgList.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_NORMAL);
        provider = null;
        adapter.notifyDataSetChanged();
        save();
        refresh();
        scrollToBottom();
    }

    private void stopGeneration() {
        OpenAiCompatibleProvider p = provider;
        if (p != null) {
            p.cancel();
        }
        Thread w = worker;
        if (w != null && w.isAlive()) {
            w.interrupt();
        }
    }

    private void startNewChat() {
        if (generating) {
            stopGeneration();
        }
        conversation = Conversation.create("", settings.getSystemPrompt());
        settings.setCurrentConversation(conversation.getId());
        save();
        refresh();
    }

    // ----------------------------------------------------------- context menu

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (menuInfo instanceof AdapterView.AdapterContextMenuInfo) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            Message m = adapter.getAt(info.position);
            if (m == null) {
                return;
            }
            menu.setHeaderTitle(labelFor(m));
            if (m.isUser()) {
                menu.add(GRP_MSG, ITEM_EDIT_RESEND, 0, R.string.menu_edit_resend);
                menu.add(GRP_MSG, ITEM_COPY, 1, R.string.menu_copy);
            } else if (m.isError()) {
                menu.add(GRP_MSG, ITEM_RETRY, 0, R.string.menu_retry);
                menu.add(GRP_MSG, ITEM_COPY, 1, R.string.menu_copy);
                menu.add(GRP_MSG, ITEM_DELETE, 2, R.string.menu_delete_message);
            } else {
                menu.add(GRP_MSG, ITEM_COPY, 0, R.string.menu_copy);
                menu.add(GRP_MSG, ITEM_SHARE, 1, R.string.menu_share);
                menu.add(GRP_MSG, ITEM_REGENERATE, 2, R.string.menu_regenerate);
                menu.add(GRP_MSG, ITEM_DELETE, 3, R.string.menu_delete_message);
            }
        }
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    private static String labelFor(Message m) {
        if (m == null || m.getContent() == null) {
            return "";
        }
        String s = m.getContent().replace('\n', ' ').trim();
        return s.length() > 28 ? s.substring(0, 28) + "…" : s;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getGroupId() != GRP_MSG
                || !(item.getMenuInfo() instanceof AdapterView.AdapterContextMenuInfo)) {
            return super.onContextItemSelected(item);
        }
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        Message m = adapter.getAt(info.position);
        if (m == null) {
            return true;
        }
        switch (item.getItemId()) {
            case ITEM_COPY:
                copyMessage(m);
                return true;
            case ITEM_SHARE:
                shareMessage(m);
                return true;
            case ITEM_REGENERATE:
            case ITEM_RETRY:
                regenerateFrom(m);
                return true;
            case ITEM_EDIT_RESEND:
                startEditing(m);
                return true;
            case ITEM_DELETE:
                conversation.getMessages().remove(m);
                save();
                refresh();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void copyMessage(Message m) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("message", m.getContent()));
        Toast.makeText(this, R.string.msg_copied, Toast.LENGTH_SHORT).show();
    }

    private void shareMessage(Message m) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, m.getContent());
        startActivity(Intent.createChooser(share, getString(R.string.menu_share)));
    }

    private void regenerateFrom(Message assistantOrError) {
        if (generating || assistantOrError == null) {
            return;
        }
        List<Message> msgs = conversation.getMessages();
        int idx = -1;
        for (int i = 0; i < msgs.size(); i++) {
            if (msgs.get(i).getId().equals(assistantOrError.getId())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            return;
        }
        msgs.remove(idx);
        save();
        refresh();
        generate();
    }

    private void startEditing(Message userMessage) {
        if (generating) {
            return;
        }
        editingUserMessage = userMessage;
        editInput.setText(userMessage.getContent());
        editInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    // ------------------------------------------------------------- callbacks

    @Override
    public void onCopy(Message message) {
        copyMessage(message);
    }

    @Override
    public void onShare(Message message) {
        shareMessage(message);
    }

    @Override
    public void onRegenerate(Message message) {
        regenerateFrom(message);
    }

    @Override
    public void onEditResend(Message message) {
        startEditing(message);
    }

    @Override
    public void onRetry(Message message) {
        regenerateFrom(message);
    }

    @Override
    public void onDelete(Message message) {
        conversation.getMessages().remove(message);
        save();
        refresh();
    }

    // ----------------------------------------------------- results / lifecycle

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CHATS && resultCode == RESULT_OK && data != null) {
            if (data.getBooleanExtra("new", false)) {
                startNewChat();
                return;
            }
            String id = data.getStringExtra("conversation_id");
            Conversation loaded = id != null ? store.find(id) : null;
            if (loaded != null) {
                if (generating) {
                    stopGeneration();
                }
                conversation = loaded;
                settings.setCurrentConversation(conversation.getId());
                refresh();
            }
        } else if (requestCode == REQ_SETTINGS) {
            // Theme/provider may have changed; recreate to re-apply theme.
            Intent intent = getIntent();
            finish();
            startActivity(intent);
        }
    }

    @Override
    public void onBackPressed() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && imm.isActive(editInput)) {
            imm.hideSoftInputFromWindow(editInput.getWindowToken(), 0);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (generating) {
            stopGeneration();
        }
        super.onDestroy();
    }
}