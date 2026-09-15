package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.TextView;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;
import com.appfactory.modules.network.Network;

import java.util.ArrayList;

/** Conversation list with unread counts, offline banner, pin/mute/archive. */
public final class MainActivity extends BaseActivity {

    private ListView list;
    private TextView banner;
    private TextView empty;
    private ConversationAdapter adapter;
    private Network.Listener netListener;
    private com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data.MessengerRepository.Listener repoListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        banner = findViewById(R.id.status_banner);
        list = findViewById(R.id.conversation_list);
        adapter = new ConversationAdapter(this, new ArrayList<>());
        list.setAdapter(adapter);

        empty = new TextView(this);
        empty.setText(R.string.state_empty);
        empty.setGravity(android.view.Gravity.CENTER);
        empty.setPadding(dp(24), dp(48), dp(24), dp(24));
        ((android.widget.FrameLayout) findViewById(R.id.main_root)).addView(empty,
                new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        empty.setVisibility(android.view.View.GONE);

        findViewById(R.id.btn_new_chat).setOnClickListener(v ->
                startActivity(new Intent(this, NewConversationActivity.class)));
        findViewById(R.id.btn_search).setOnClickListener(v ->
                startActivity(new Intent(this, SearchActivity.class)));
        findViewById(R.id.btn_archive).setOnClickListener(v ->
                startActivity(new Intent(this, ArchiveActivity.class)));
        findViewById(R.id.btn_contacts).setOnClickListener(v ->
                startActivity(new Intent(this, ContactsActivity.class)));
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.main_title).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        list.setOnItemClickListener((parent, view, position, id) -> {
            Conversation c = adapter.getItem(position);
            openChat(c.id);
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            final Conversation c = adapter.getItem(position);
            showConversationActions(c);
            return true;
        });

        netListener = new Network.Listener() {
            @Override public void onStatusChanged(Network.Status previous, Network.Status current) {
                runOnUiThread(() -> refresh());
            }
        };
        app().network().addListener(netListener);
        repoListener = this::onRepoChanged;
        repo().addListener(repoListener);
        refresh();
    }

    private void onRepoChanged() {
        runOnUiThread(this::refresh);
    }

    private void openChat(String conversationId) {
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra("conversationId", conversationId);
        startActivity(i);
    }

    private void showConversationActions(final Conversation c) {
        String[] options;
        if (c.archived) {
            options = new String[]{
                    getString(R.string.action_unarchive), getString(R.string.action_cancel)};
        } else {
            options = new String[]{
                    c.pinned ? getString(R.string.action_unpin) : getString(R.string.action_pin),
                    c.muted ? getString(R.string.action_unmute) : getString(R.string.action_mute),
                    getString(R.string.action_archive_conversation),
                    getString(R.string.action_cancel)};
        }
        new AlertDialog.Builder(this)
                .setTitle(c.title)
                .setItems(options, (d, which) -> {
                    if (c.archived) {
                        if (which == 0) repo().setArchived(c.id, false);
                    } else if (which == 0) {
                        repo().setPinned(c.id, !c.pinned);
                    } else if (which == 1) {
                        repo().setMuted(c.id, !c.muted);
                    } else if (which == 2) {
                        repo().setArchived(c.id, true);
                    }
                })
                .show();
    }

    private void refresh() {
        java.util.List<Conversation> inbox = repo().inboxSorted();
        if (!inbox.isEmpty() && repo().unreadTotal() > 0) {
            setTitle(getString(R.string.app_name) + " (" + repo().unreadTotal() + ")");
        } else {
            setTitle(getString(R.string.app_name));
        }
        adapter.replace(inbox);

        boolean online = app().network().isOnline();
        if (online) {
            banner.setText(R.string.state_online);
            banner.setVisibility(android.view.View.GONE);
        } else {
            banner.setText(R.string.state_offline);
            banner.setVisibility(android.view.View.VISIBLE);
        }
        empty.setVisibility(inbox.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        app().refreshNetworkState();
        refresh();
        app().syncNow();
    }

    @Override
    protected void onDestroy() {
        if (netListener != null) app().network().removeListener(netListener);
        if (repoListener != null) repo().removeListener(repoListener);
        super.onDestroy();
    }
}