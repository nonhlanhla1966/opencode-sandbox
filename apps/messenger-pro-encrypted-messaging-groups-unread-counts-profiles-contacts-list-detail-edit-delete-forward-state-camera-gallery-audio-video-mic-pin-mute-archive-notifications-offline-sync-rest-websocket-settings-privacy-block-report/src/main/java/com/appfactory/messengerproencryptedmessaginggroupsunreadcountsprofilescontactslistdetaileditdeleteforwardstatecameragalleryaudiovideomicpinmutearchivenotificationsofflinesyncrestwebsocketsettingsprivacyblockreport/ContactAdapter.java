package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Contact;

import java.util.List;

/** Contact list rows. */
public final class ContactAdapter extends BaseAdapter {

    private final Context context;
    private final List<Contact> items;

    public ContactAdapter(Context context, List<Contact> items) {
        this.context = context;
        this.items = items;
    }

    public void replace(List<Contact> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }
    @Override public Contact getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = View.inflate(context, R.layout.item_contact_row, null);
        }
        Contact c = getItem(position);

        TextView avatar = v.findViewById(R.id.contact_avatar);
        avatar.setText(Utils.initials(c.displayName));
        avatar.setBackgroundColor(ThemeManager.avatarColor(c.id));

        TextView name = v.findViewById(R.id.contact_name);
        name.setText(c.displayName + (c.blocked ? " · " +
                context.getString(R.string.label_blocked) : ""));

        TextView phone = v.findViewById(R.id.contact_phone);
        phone.setText(c.phone);
        return v;
    }
}