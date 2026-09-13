package com.appfactory.tipcalculator;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MainActivity extends Activity {

    private static final int[] TIP_RATES = {12, 15, 18, 22};

    private final Map<Integer, Button> tipButtons = new LinkedHashMap<>();
    private EditText billInput;
    private Button peopleMinus;
    private Button peoplePlus;
    private TextView peopleCount;
    private TextView tipResult;
    private TextView totalResult;
    private TextView perPersonResult;

    private int selectedRate = TipCalculator.DEFAULT_RATE;
    private int people = TipCalculator.MIN_PEOPLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        billInput = findViewById(R.id.billInput);
        peopleMinus = findViewById(R.id.peopleMinus);
        peoplePlus = findViewById(R.id.peoplePlus);
        peopleCount = findViewById(R.id.peopleCount);
        tipResult = findViewById(R.id.tipResult);
        totalResult = findViewById(R.id.totalResult);
        perPersonResult = findViewById(R.id.perPersonResult);

        tipButtons.put(12, findViewById(R.id.tipButton12));
        tipButtons.put(15, findViewById(R.id.tipButton15));
        tipButtons.put(18, findViewById(R.id.tipButton18));
        tipButtons.put(22, findViewById(R.id.tipButton22));

        for (final int rate : TIP_RATES) {
            Button b = tipButtons.get(rate);
            b.setText(getString(R.string.tip_percent_format, rate));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedRate = rate;
                    refreshRateUi();
                    recompute();
                }
            });
        }

        peopleMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (people > TipCalculator.MIN_PEOPLE) {
                    people--;
                    refreshPeopleUi();
                    recompute();
                }
            }
        });

        peoplePlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (people < TipCalculator.MAX_PEOPLE) {
                    people++;
                    refreshPeopleUi();
                    recompute();
                }
            }
        });

        billInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                recompute();
            }
        });

        Button about = findViewById(R.id.aboutButton);
        about.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
            }
        });

        refreshRateUi();
        refreshPeopleUi();
        recompute();
    }

    private void refreshRateUi() {
        for (Map.Entry<Integer, Button> entry : tipButtons.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == selectedRate);
        }
    }

    private void refreshPeopleUi() {
        peopleCount.setText(getString(R.string.people_count_format, people));
        peopleMinus.setEnabled(people > TipCalculator.MIN_PEOPLE);
        peoplePlus.setEnabled(people < TipCalculator.MAX_PEOPLE);
    }

    private void recompute() {
        BigDecimal bill = TipCalculator.parseBill(billInput.getText().toString());
        BigDecimal tip = TipCalculator.tip(bill, selectedRate);
        BigDecimal total = TipCalculator.total(bill, selectedRate);
        BigDecimal perPerson = TipCalculator.perPerson(bill, selectedRate, people);

        tipResult.setText(getString(R.string.tip_result_label) + ": "
                + TipCalculator.money(tip));
        totalResult.setText(getString(R.string.total_result_label) + ": "
                + TipCalculator.money(total));
        perPersonResult.setText(getString(R.string.per_person_label) + ": "
                + TipCalculator.money(perPerson));
    }
}