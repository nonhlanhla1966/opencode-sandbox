package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;

import java.util.List;

/** Renders the message bubble list with status ticks + group sender names. */
public final class MessageAdapter extends BaseAdapter {

    private final Context context;
    private final List<Message> items;
    private final String myId;
    private final Conversation conversation;
    private final java.util.Map<String, String> senderNames;

    public MessageAdapter(Context context, List<Message> items, String myId,
                          Conversation conversation,
                          java.util.Map<String, String> senderNames) {
        this.context = context;
        this.items = items;
        this.myId = myId;
        this.conversation = conversation;
        this.senderNames = senderNames;
    }

    public void replace(List<Message> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }
    @Override public Message getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override public int getViewTypeCount() {
        return 2; // 0 me, 1 other/system
    }

    @Override public int getItemViewType(int position) {
        Message m = getItem(position);
        return m.isFrom(myId) ? 0 : 1;
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        Message m = getItem(position);
        boolean mine = getItemViewType(position) == 0;
        View v = convertView;
        if (v == null) {
            v = View.inflate(context,
                    mine ? R.layout.item_message_me : R.layout.item_message_other, null);
        }

        TextView body = v.findViewById(R.id.message_body);
        body.setText(displayBody(m));

        TextView time = v.findViewById(R.id.message_time);
        time.setText(Utils.timeOfDay(m.createdAt) + (m.isEdited() ? " · "
                + context.getString(R.string.label_edited) : ""));

        if (mine) {
            TextView status = v.findViewById(R.id.message_status);
            status.setText(Utils.statusLabel(m.status.name()));
        } else {
            TextView sender = v.findViewById(R.id.message_sender);
            if (sender != null) {
                if (conversation != null && conversation.isGroup() && m.senderId != null) {
                    String name = senderNames.get(m.senderId);
                    sender.setText(name == null ? m.senderId : name);
                    sender.setVisibility(View.VISIBLE);
                } else {
                    sender.setVisibility(View.GONE);
                }
            }
        }

        v.setContentDescription((mine ? context.getString(R.string.label_you) + ": " : "")
                + displayBody(m));
        return v;
    }

    private String displayBody(Message m) {
        if (m.isDeleted()) return context.getString(R.string.label_deleted);
        if (m.hasAttachment()) {
            String kindLabel = attachmentLabel(m.kind);
            return m.body.isEmpty() ? kindLabel : m.body + "\n[" + kindLabel + "]";
        }
        if (m.isSystem()) return m.body;
        return m.body;
    }

    private String attachmentLabel(Message.Kind kind) {
        switch (kind) {
            case IMAGE: return context.getString(R.string.label_attachment_image);
            case VIDEO: return context.getString(R.string.label_attachment_video);
            case AUDIO: return context.getString(R.string.label_attachment_audio);
            case VOICE_NOTE: return context.getString(R.string.label_attachment_voice);
            default: return context.getString(R.string.label_attachment_document);
        }
    }
}