package com.appfactory.opencodechatbot.ui;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.appfactory.opencodechatbot.R;
import com.appfactory.opencodechatbot.model.Message;
import com.appfactory.opencodechatbot.util.Markdown;
import com.appfactory.opencodechatbot.util.MarkdownSpanner;

import java.util.List;

/** ListView adapter for chat messages (two row types: user / assistant). */
public final class MessagesAdapter extends BaseAdapter {

    public interface OnActionListener {
        void onCopy(Message message);

        void onShare(Message message);

        void onRegenerate(Message message);

        void onEditResend(Message message);

        void onRetry(Message message);

        void onDelete(Message message);
    }

    private static final int TYPE_USER = 0;
    private static final int TYPE_ASSISTANT = 1;

    private final LayoutInflater inflater;
    private final MarkdownSpanner spanner;
    private final OnActionListener listener;
    private final int textPrimary;
    private List<Message> messages;

    public MessagesAdapter(Context context, MarkdownSpanner spanner, OnActionListener listener) {
        this.inflater = LayoutInflater.from(context);
        this.spanner = spanner;
        this.listener = listener;
        this.textPrimary = com.appfactory.opencodechatbot.util.AppTheme.resolveColor(
                context, com.appfactory.opencodechatbot.R.attr.chatTextPrimary);
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    public Message getAt(int position) {
        if (messages == null || position < 0 || position >= messages.size()) {
            return null;
        }
        return messages.get(position);
    }

    @Override
    public int getCount() {
        return messages == null ? 0 : messages.size();
    }

    @Override
    public Object getItem(int position) {
        return getAt(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        Message m = getAt(position);
        return (m != null && m.isUser()) ? TYPE_USER : TYPE_ASSISTANT;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Message message = getAt(position);
        if (message == null) {
            return convertView;
        }
        final int type = getItemViewType(position);

        if (type == TYPE_USER) {
            View row = convertView;
            if (row == null) {
                row = inflater.inflate(R.layout.row_message_user, parent, false);
                row.setLongClickable(true);
            }
            TextView text = row.findViewById(R.id.msg_text);
            text.setText(message.getContent());
            row.setTag(message.getId());
            return row;
        }

        // assistant / error
        View row = convertView;
        if (row == null) {
            row = inflater.inflate(R.layout.row_message_assistant, parent, false);
            row.setLongClickable(true);
        }
        TextView text = row.findViewById(R.id.msg_text);
        ProgressBar progress = row.findViewById(R.id.msg_progress);

        boolean streaming = message.isGenerating();
        boolean error = message.isError();

        if (error) {
            text.setText(message.getContent());
            text.setTextColor(0xFFE5484D);
        } else if (streaming) {
            // plain text while streaming; markdown is rendered once complete.
            text.setText(message.getContent());
            text.setTextColor(textPrimary);
        } else {
            text.setText(spanner.render(Markdown.parse(message.getContent())));
            text.setTextColor(textPrimary);
        }
        text.setMovementMethod(LinkMovementMethod.getInstance());
        progress.setVisibility(streaming ? View.VISIBLE : View.GONE);
        row.setTag(message.getId());
        return row;
    }
}