package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Contact;

/** Contact directory with add + profile navigation. */
public final class ContactsActivity extends BaseActivity {

    private ListView list;
    private ContactAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Button add = new Button(this);
        add.setText(R.string.action_new_chat);
        add.setOnClickListener(v -> showAddDialog());

        adapter = new ContactAdapter(this, new java.util.ArrayList<>());
        list = new ListView(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Contact c = adapter.getItem(position);
            Intent i = new Intent(this, ContactProfileActivity.class);
            i.putExtra("contactId", c.id);
            startActivity(i);
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(add);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        refresh();
    }

    private void refresh() {
        adapter.replace(repo().contacts());
    }

    private void showAddDialog() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(24), dp(8), dp(24), dp(4));

        final EditText name = new EditText(this);
        name.setHint(R.string.hint_contact_name);
        final EditText phone = new EditText(this);
        phone.setHint(R.string.hint_phone);
        phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        final EditText about = new EditText(this);
        about.setHint(R.string.label_about_hint);

        row.addView(name);
        row.addView(phone);
        row.addView(about);

        new AlertDialog.Builder(this)
                .setTitle(R.string.title_contacts)
                .setView(row)
                .setPositiveButton(R.string.action_create, (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) {
                        toast(getString(R.string.hint_contact_name) + " required");
                        return;
                    }
                    repo().putContact(Contact.create("c-" + System.currentTimeMillis(),
                            n, phone.getText().toString().trim(), about.getText().toString().trim(),
                            System.currentTimeMillis()));
                    refresh();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }
}