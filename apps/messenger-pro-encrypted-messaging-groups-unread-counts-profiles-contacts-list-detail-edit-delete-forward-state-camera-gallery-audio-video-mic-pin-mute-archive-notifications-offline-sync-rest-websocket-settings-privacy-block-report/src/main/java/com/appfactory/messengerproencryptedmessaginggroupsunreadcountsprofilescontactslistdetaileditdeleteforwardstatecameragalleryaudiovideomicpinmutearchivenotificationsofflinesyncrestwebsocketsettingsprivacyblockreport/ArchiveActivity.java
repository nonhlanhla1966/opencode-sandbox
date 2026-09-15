package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;

import java.util.ArrayList;

/** Archived conversations: open from list, long-press to unarchive. */
public final class ArchiveActivity extends BaseActivity {

    private ConversationAdapter adapter;
    private TextView empty;
    private com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data.MessengerRepository.Listener repoListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        adapter = new ConversationAdapter(this, new ArrayList<>());
        ListView list = new ListView(this);
        list.setAdapter(adapter);

        list.setOnItemClickListener((parent, view, position, id) -> {
            Conversation c = adapter.getItem(position);
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra("conversationId", c.id);
            startActivity(i);
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            final Conversation c = adapter.getItem(position);
            new AlertDialog.Builder(this)
                    .setTitle(c.title)
                    .setItems(new String[]{getString(R.string.action_unarchive),
                                    getString(R.string.action_cancel)},
                            (d, which) -> {
                                if (which == 0) repo().setArchived(c.id, false);
                            })
                    .show();
            return true;
        });

        empty = new TextView(this);
        empty.setText(R.string.state_empty);
        empty.setPadding(dp(16), dp(24), dp(16), dp(16));
        empty.setGravity(android.view.Gravity.CENTER);

        FrameLayout root = new FrameLayout(this);
        root.addView(list, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(empty, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        repoListener = this::onRepoChanged;
        repo().addListener(repoListener);
        refresh();
    }

    private void onRepoChanged() {
        runOnUiThread(this::refresh);
    }

    private void refresh() {
        adapter.replace(repo().archivedSorted());
        empty.setVisibility(adapter.getCount() == 0 ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        if (repoListener != null) repo().removeListener(repoListener);
        super.onDestroy();
    }
}