package edu.msu.cse476.haidaris.finance_tracker;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    FirebaseAuth auth;
    Button loginButton;
    TextView signupText;
    EditText emailInput, passwordInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        loginButton   = findViewById(R.id.loginButton);
        signupText    = findViewById(R.id.signupText);
        emailInput    = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);

        // Auto-skip to Dashboard if already logged in and verified
        // This is correct production behavior — if the user already has a
        // valid verified session, skip straight to Dashboard so they don't
        // have to log in every time they open the app.
        //
        // To see the login screen again: tap Logout on the Dashboard,
        // which calls auth.signOut() and clears the cached session.
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null && currentUser.isEmailVerified()) {
            goToDashboard(currentUser.getUid());
            return;
        }

        // Login button
        loginButton.setOnClickListener(v -> {

            String email    = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this,
                        "Please enter email and password.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {

                        if (task.isSuccessful()) {

                            FirebaseUser user = auth.getCurrentUser();

                            if (user != null && user.isEmailVerified()) {
                                goToDashboard(user.getUid());

                            } else {
                                Toast.makeText(this,
                                        "Please verify your email first. Check your inbox.",
                                        Toast.LENGTH_LONG).show();

                                if (user != null) {
                                    user.sendEmailVerification();
                                }

                                auth.signOut();
                            }

                        } else {
                            String error = task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Unknown error";
                            Toast.makeText(this,
                                    "Login failed: " + error,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });

        signupText.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, SignupActivity.class))
        );
    }

    private void goToDashboard(String firebaseUid) {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.putExtra("firebase_uid", firebaseUid);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}