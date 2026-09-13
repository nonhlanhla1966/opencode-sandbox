package com.appfactory.hello;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public final class MainActivity extends Activity {

    private int counter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final TextView heading = findViewById(R.id.heading);
        final TextView counterView = findViewById(R.id.counter);
        heading.setText(Greeting.greeting("AppFactory"));

        Button press = findViewById(R.id.pressButton);
        press.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                counter++;
                counterView.setText(Greeting.counterLabel(counter));
            }
        });

        Button about = findViewById(R.id.aboutButton);
        about.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
            }
        });
    }
}