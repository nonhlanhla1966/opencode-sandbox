package com.appfactory.aonebuttonhelloworld;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public final class MainActivity extends Activity {

    private int presses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final TextView greeting = findViewById(R.id.greeting);
        Button hello = findViewById(R.id.helloButton);
        hello.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                greeting.setText(Greeting.pressMessage(presses++));
            }
        });
    }
}