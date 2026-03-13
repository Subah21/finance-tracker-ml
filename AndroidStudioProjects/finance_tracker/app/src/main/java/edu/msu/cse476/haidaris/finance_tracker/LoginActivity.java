package edu.msu.cse476.haidaris.finance_tracker;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    Button loginButton;
    TextView signupText;

    EditText emailInput;
    EditText passwordInput;

    FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        loginButton = findViewById(R.id.loginButton);
        signupText = findViewById(R.id.signupText);

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);

        loginButton.setOnClickListener(v -> {

            String email = emailInput.getText().toString();
            String password = passwordInput.getText().toString();

            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {

                        if(task.isSuccessful()){

                            startActivity(new Intent(this, DashboardActivity.class));

                        } else {

                            Toast.makeText(this,"Login Failed",Toast.LENGTH_LONG).show();

                        }

                    });

        });

        signupText.setOnClickListener(v -> {

            Intent intent = new Intent(LoginActivity.this, VerificationActivity.class);
            startActivity(intent);

        });
    }
}