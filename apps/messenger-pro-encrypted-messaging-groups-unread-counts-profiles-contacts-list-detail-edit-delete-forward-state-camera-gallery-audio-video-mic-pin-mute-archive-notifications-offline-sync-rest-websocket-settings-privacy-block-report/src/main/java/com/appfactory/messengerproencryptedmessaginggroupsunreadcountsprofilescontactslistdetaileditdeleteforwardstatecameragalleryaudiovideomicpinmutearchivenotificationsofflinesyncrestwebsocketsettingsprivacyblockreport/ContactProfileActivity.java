package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Contact;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;

/** Contact profile: message, block/unblock, report. */
public final class ContactProfileActivity extends BaseActivity {

    private Contact contact;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String contactId = getIntent().getStringExtra("contactId");
        if (contactId == null) {
            finish();
            return;
        }
        contact = repo().contact(contactId);
        if (contact == null) {
            finish();
            return;
        }
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(16), dp(24), dp(16));

        TextView name = new TextView(this);
        name.setText(contact.displayName + (contact.blocked
                ? " · " + getString(R.string.label_blocked) : ""));
        name.setTextSize(24);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(name);

        TextView phone = new TextView(this);
        phone.setText(contact.phone);
        phone.setTextSize(15);
        root.addView(phone);

        TextView about = new TextView(this);
        about.setText(contact.about.isEmpty()
                ? getString(R.string.label_no_about) : contact.about);
        about.setTextSize(14);
        about.setPadding(0, dp(10), 0, dp(10));
        root.addView(about);

        Button message = new Button(this);
        message.setText(R.string.action_new_chat);
        message.setOnClickListener(v -> openChat());
        root.addView(message);

        Button block = new Button(this);
        block.setText(contact.blocked ? R.string.action_unblock : R.string.action_block);
        block.setOnClickListener(v -> toggleBlock());
        root.addView(block);

        Button report = new Button(this);
        report.setText(R.string.action_report);
        report.setEnabled(!contact.blocked);
        report.setOnClickListener(v -> showReportDialog());
        root.addView(report);

        Button back = new Button(this);
        back.setText(R.string.action_back);
        back.setOnClickListener(v -> finish());
        root.addView(back);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void openChat() {
        for (Conversation c : repo().conversations()) {
            if (!c.isGroup() && c.peerId.equals(contact.id)) {
                Intent i = new Intent(this, ChatActivity.class);
                i.putExtra("conversationId", c.id);
                startActivity(i);
                return;
            }
        }
        Conversation fresh = repo().addDirectConversation(contact.id, contact.displayName);
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra("conversationId", fresh.id);
        startActivity(i);
    }

    private void toggleBlock() {
        boolean blocked = !contact.blocked;
        repo().putContact(contact.blocked(blocked));
        for (Conversation c : repo().conversations()) {
            if (!c.isGroup() && c.peerId.equals(contact.id)) repo().setBlocked(c.id, blocked);
        }
        contact = repo().contact(contact.id);
        render();
    }

    private void showReportDialog() {
        final EditText reason = new EditText(this);
        reason.setHint(R.string.label_report_reason);
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_report)
                .setView(reason)
                .setPositiveButton(R.string.action_send, (d, w) -> {
                    repo().enqueueReport(contact.id, reason.getText().toString().trim());
                    app().syncNow();
                    toast(getString(R.string.label_reported));
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}