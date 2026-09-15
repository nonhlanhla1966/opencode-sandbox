package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic.MessageRules;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Attachment;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Contact;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Thread view: send, attach, voice note, reply, edit, delete, forward, react. */
public final class ChatActivity extends BaseActivity {

    private static final int REQ_PICK = 2001;
    private static final int REQ_CAMERA = 2002;

    private Conversation conversation;
    private ListView list;
    private MessageAdapter adapter;
    private EditText input;
    private TextView chatStatus;
    private VoiceRecorder voice;
    private String editTargetId;
    private String replyTargetId;
    private com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data.MessengerRepository.Listener repoListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String conversationId = getIntent().getStringExtra("conversationId");
        if (conversationId == null) {
            finish();
            return;
        }
        conversation = repo().conversation(conversationId);
        if (conversation == null) {
            finish();
            return;
        }
        setContentView(R.layout.activity_chat);

        chatStatus = findViewById(R.id.chat_status);
        list = findViewById(R.id.message_list);
        input = findViewById(R.id.message_input);
        voice = new VoiceRecorder();

        ((TextView) findViewById(R.id.chat_title)).setText(conversation.title);
        adapter = new MessageAdapter(this, new ArrayList<>(),
                repo().myId(), conversation, senderNames());
        list.setAdapter(adapter);

        findViewById(R.id.btn_chat_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_send).setOnClickListener(v -> sendCurrent());
        findViewById(R.id.btn_attach).setOnClickListener(v -> pickAttachment());
        findViewById(R.id.btn_mic).setOnClickListener(v -> toggleVoice());

        list.setOnItemClickListener((parent, view, position, id) -> {
            Message m = adapter.getItem(position);
            if (m.hasAttachment() && !m.isDeleted()) {
                new AlertDialog.Builder(this)
                        .setTitle(attachmentCaption(m))
                        .setMessage(m.attachment.uri + "\n" + m.attachment.mimeType
                                + " · " + m.attachment.sizeBytes + " bytes")
                        .setPositiveButton(R.string.action_view_profile, null)
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
            }
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            showMessageActions(adapter.getItem(position));
            return true;
        });

        repoListener = this::onRepoChanged;
        repo().addListener(repoListener);
        refresh();
    }

    private void onRepoChanged() {
        runOnUiThread(this::refresh);
    }

    private Map<String, String> senderNames() {
        Map<String, String> names = new HashMap<>();
        for (Contact c : repo().contacts()) names.put(c.id, c.displayName);
        return names;
    }

    private void refresh() {
        List<Message> tail = repo().thread(conversation.id).latest(ThreadWindowSize());
        adapter.replace(tail);
        String status = conversation.isGroup()
                ? getString(R.string.label_group) : getString(R.string.label_direct);
        if (conversation.muted) status += " · " + getString(R.string.label_muted);
        if (conversation.blocked) status += " · " + getString(R.string.label_blocked);
        chatStatus.setText(status);
        Message last = tail.isEmpty() ? null : tail.get(tail.size() - 1);
        if (last != null) repo().markRead(conversation.id, last.id);
    }

    private int ThreadWindowSize() {
        return com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic.ThreadWindow.PAGE_SIZE;
    }

    private void sendCurrent() {
        String body = input.getText().toString().trim();
        if (body.isEmpty()) return;
        if (editTargetId != null) {
            repo().edit(editTargetId, body);
            editTargetId = null;
        } else if (replyTargetId != null) {
            repo().reply(conversation.id, replyTargetId, body);
            replyTargetId = null;
        } else {
            repo().sendText(conversation.id, body);
        }
        input.setText("");
        input.setHint(getString(R.string.hint_message));
        app().syncNow();
        refresh();
    }

    private void pickAttachment() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_attach)
                .setItems(new String[]{getString(R.string.label_take_photo),
                                getString(R.string.label_pick_file)},
                        (d, which) -> {
                            if (which == 0) capturePhoto();
                            else openPicker();
                        })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void openPicker() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "image/*", "video/*", "audio/*", "application/pdf"});
        startActivityForResult(Intent.createChooser(i, getString(R.string.action_attach)), REQ_PICK);
    }

    private void capturePhoto() {
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(i, REQ_CAMERA);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == REQ_PICK && data != null && data.getData() != null) {
            Attachment a = AttachmentFiles.importFromUri(this, data.getData());
            if (a != null) {
                repo().sendAttachment(conversation.id, "", a);
                app().syncNow();
                refresh();
            } else {
                toast(getString(R.string.state_error));
            }
        } else if (requestCode == REQ_CAMERA) {
            Object d = data == null ? null : data.getExtras().get("data");
            if (d instanceof Bitmap) {
                Attachment a = AttachmentFiles.saveBitmap(this, (Bitmap) d);
                if (a != null) {
                    repo().sendAttachment(conversation.id, "", a);
                    app().syncNow();
                    refresh();
                }
            }
        }
    }

    private void toggleVoice() {
        if (voice.recording()) {
            Attachment a = voice.stopAndAttachment();
            TextView mic = findViewById(R.id.btn_mic);
            mic.setText(getString(R.string.action_mic));
            if (a != null && a.sizeBytes > 0) {
                repo().sendAttachment(conversation.id, "", a);
                app().syncNow();
                refresh();
            }
        } else {
            requestAudioPermission();
            File target = AttachmentFiles.newVoiceTarget(this);
            boolean ok = voice.start(target);
            if (ok) {
                TextView mic = findViewById(R.id.btn_mic);
                mic.setText("\u25CF " + getString(R.string.label_recording));
            } else {
                toast(getString(R.string.state_error));
            }
        }
    }

    private void showMessageActions(final Message m) {
        final List<String> actions = new ArrayList<>();
        final List<Runnable> run = new ArrayList<>();

        if (MessageRules.canReply(m)) {
            actions.add(getString(R.string.action_reply));
            run.add(() -> {
                replyTargetId = m.id;
                editTargetId = null;
                input.setHint(getString(R.string.label_reply) + ": "
                        + MessageRules.replyPreview(m));
                input.requestFocus();
            });
        }
        if (MessageRules.canEdit(m, repo().myId(), System.currentTimeMillis())) {
            actions.add(getString(R.string.action_edit));
            run.add(() -> {
                editTargetId = m.id;
                replyTargetId = null;
                input.setText(m.body);
                input.setSelection(m.body.length());
                input.requestFocus();
            });
        }
        if (MessageRules.canDelete(m, repo().myId(), conversation.isGroup())) {
            actions.add(getString(R.string.action_delete));
            run.add(() -> new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.action_delete) + "?")
                    .setPositiveButton(R.string.action_delete,
                            (d, w) -> {
                                repo().delete(m.id);
                                app().syncNow();
                            })
                    .setNegativeButton(R.string.action_cancel, null)
                    .show());
        }
        if (MessageRules.canForward(m)) {
            actions.add(getString(R.string.action_forward));
            run.add(() -> showForwardPicker(m));
        }
        if (MessageRules.canReact(m)) {
            actions.add(getString(R.string.action_react));
            run.add(() -> showReactionPicker(m));
        }

        if (actions.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle(MessageRules.replyPreview(m))
                .setItems(actions.toArray(new String[0]),
                        (d, which) -> run.get(which).run())
                .show();
    }

    private void showReactionPicker(final Message m) {
        List<String> list = MessageRules.quickReactions();
        new AlertDialog.Builder(this)
                .setTitle(R.string.label_react_choose)
                .setItems(list.toArray(new String[0]),
                        (d, which) -> {
                            repo().react(m.id, list.get(which), repo().myId());
                            app().syncNow();
                        })
                .show();
    }

    private void showForwardPicker(final Message m) {
        List<Conversation> targets = new ArrayList<>();
        for (Conversation c : repo().conversations()) {
            if (!c.id.equals(conversation.id)) targets.add(c);
        }
        final String[] labels = new String[targets.size()];
        for (int i = 0; i < targets.size(); i++) labels[i] = targets.get(i).title;
        new AlertDialog.Builder(this)
                .setTitle(R.string.label_forward_to)
                .setItems(labels, (d, which) -> {
                    repo().forwardTo(m.id, targets.get(which).id);
                    app().syncNow();
                })
                .show();
    }

    private String attachmentCaption(Message m) {
        if (m.body != null && !m.body.isEmpty()) return m.body;
        switch (m.kind) {
            case IMAGE: return getString(R.string.label_attachment_image);
            case VIDEO: return getString(R.string.label_attachment_video);
            case AUDIO: return getString(R.string.label_attachment_audio);
            case VOICE_NOTE: return getString(R.string.label_attachment_voice);
            default: return getString(R.string.label_attachment_document);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onPause() {
        if (voice != null) voice.cancel();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (repoListener != null) repo().removeListener(repoListener);
        super.onDestroy();
    }
}