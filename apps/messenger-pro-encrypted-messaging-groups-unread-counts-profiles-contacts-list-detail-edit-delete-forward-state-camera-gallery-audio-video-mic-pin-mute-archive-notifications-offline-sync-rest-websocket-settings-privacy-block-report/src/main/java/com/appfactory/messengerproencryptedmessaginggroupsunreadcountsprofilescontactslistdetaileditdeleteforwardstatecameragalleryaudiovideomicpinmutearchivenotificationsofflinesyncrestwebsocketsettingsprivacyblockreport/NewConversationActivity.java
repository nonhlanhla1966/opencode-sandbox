package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Contact;

/** Create a direct or group conversation. */
public final class NewConversationActivity extends BaseActivity {

    private RadioGroup kindGroup;
    private EditText nameInput;
    private EditText phoneInput;
    private EditText groupInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(16), dp(24), dp(16));

        root.addView(label(getString(R.string.title_new_conversation), 20, true));

        kindGroup = new RadioGroup(this);
        kindGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton direct = new RadioButton(this);
        direct.setText(R.string.label_direct);
        direct.setId(ViewIds.next());
        RadioButton group = new RadioButton(this);
        group.setText(R.string.label_group);
        group.setId(ViewIds.next());
        kindGroup.addView(direct);
        kindGroup.addView(group);
        kindGroup.check(direct.getId());
        root.addView(kindGroup);

        root.addView(label(getString(R.string.hint_contact_name), 14, false));
        nameInput = new EditText(this);
        nameInput.setHint(R.string.hint_contact_name);
        root.addView(nameInput);

        root.addView(label(getString(R.string.hint_phone), 14, false));
        phoneInput = new EditText(this);
        phoneInput.setHint(R.string.hint_phone);
        phoneInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        root.addView(phoneInput);

        root.addView(label(getString(R.string.hint_group_name), 14, false));
        groupInput = new EditText(this);
        groupInput.setHint(R.string.hint_group_name);
        root.addView(groupInput);

        Button create = new Button(this);
        create.setText(R.string.action_create);
        create.setOnClickListener(v -> onCreatePressed(direct.isChecked()));
        root.addView(create);

        Button cancel = new Button(this);
        cancel.setText(R.string.action_cancel);
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel);

        setContentView(root);
    }

    private void onCreatePressed(boolean isDirect) {
        if (isDirect) {
            String name = nameInput.getText().toString().trim();
            String phone = phoneInput.getText().toString().trim();
            if (name.isEmpty()) {
                toast(getString(R.string.hint_contact_name) + " required");
                return;
            }
            String contactId = "c-" + System.currentTimeMillis();
            repo().putContact(Contact.create(contactId, name, phone, "", System.currentTimeMillis()));
            repo().addDirectConversation(contactId, name);
        } else {
            String title = groupInput.getText().toString().trim();
            if (title.isEmpty()) {
                toast(getString(R.string.hint_group_name) + " required");
                return;
            }
            repo().addGroupConversation(title);
        }
        app().syncNow();
        setResult(RESULT_OK);
        finish();
    }

    private TextView label(String text, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    private static final class ViewIds {
        private static int next = 0x1000;
        static int next() { return ++next; }
    }
}