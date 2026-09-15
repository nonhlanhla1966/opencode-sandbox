package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;

import java.util.List;

/** Renders the conversation list rows (avatar, title, preview, time, unread). */
public final class ConversationAdapter extends BaseAdapter {

    private final Context context;
    private final java.util.List<Conversation> items;

    public ConversationAdapter(Context context, List<Conversation> items) {
        this.context = context;
        this.items = items;
    }

    public void replace(List<Conversation> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }
    @Override public Conversation getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = View.inflate(context, R.layout.item_conversation, null);
        }
        Conversation c = getItem(position);

        TextView avatar = v.findViewById(R.id.conversation_avatar);
        avatar.setText(Utils.initials(c.title));
        avatar.setBackgroundColor(ThemeManager.avatarColor(c.id));

        TextView title = v.findViewById(R.id.conversation_title);
        title.setText(c.pinned ? c.title + " · " + context.getString(R.string.label_pinned) : c.title);

        TextView preview = v.findViewById(R.id.conversation_preview);
        preview.setText(c.lastPreview == null || c.lastPreview.isEmpty()
                ? context.getString(R.string.state_empty)
                : (c.muted ? "🔇 " : "") + c.lastPreview);

        TextView time = v.findViewById(R.id.conversation_time);
        time.setText(c.lastMessageAt > 0 ? Utils.timeOfDay(c.lastMessageAt) : "");

        TextView unread = v.findViewById(R.id.conversation_unread);
        if (c.unreadCount > 0 && !c.archived) {
            unread.setVisibility(View.VISIBLE);
            unread.setText(String.valueOf(Math.min(99, c.unreadCount)));
        } else {
            unread.setVisibility(View.GONE);
        }

        v.setContentDescription(c.title + (c.unreadCount > 0
                ? ", " + c.unreadCount + " " + context.getString(R.string.label_unread) : ""));
        return v;
    }
}