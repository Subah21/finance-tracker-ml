package edu.msu.cse476.haidaris.finance_tracker;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

public class VerificationActivity extends AppCompatActivity {

    EditText codeInput;
    Button verifyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verification);

        codeInput = findViewById(R.id.codeInput);
        verifyButton = findViewById(R.id.verifyButton);

        verifyButton.setOnClickListener(v -> {

            String code = codeInput.getText().toString();

            if(code.length() == 6){

                Intent intent = new Intent(
                        VerificationActivity.this,
                        DashboardActivity.class
                );

                startActivity(intent);
            }

        });

    }
}