package edu.msu.cse476.haidaris.finance_tracker;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final String DEMO_UID   = "demo_user_12345";
    private static final String DEMO_EMAIL = "demo@financetracker.test";

    FirebaseAuth auth;
    Button loginButton, demoButton;
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
            ensureUserInBackendAndGo(currentUser.getUid(), currentUser.getEmail());
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
                                ensureUserInBackendAndGo(user.getUid(), user.getEmail());

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

        // Demo button — skips Firebase, ensures the demo user exists in the
        // backend, then jumps straight to Dashboard with the seeded data.
        demoButton = findViewById(R.id.demoButton);
        demoButton.setOnClickListener(v -> ensureDemoUserAndGo());
    }

    /**
     * Creates the demo user in the backend (or returns the existing one),
     * then navigates to the Dashboard without needing Firebase auth.
     */
    private void ensureDemoUserAndGo() {
        demoButton.setEnabled(false);
        demoButton.setText("Loading...");

        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid", DEMO_UID);
            body.put("email", DEMO_EMAIL);
            body.put("username", "DemoStudent");
            body.put("monthly_income", 1500.0);

            Request request = new Request.Builder()
                    .url(ApiClient.getBaseUrl() + "/users")
                    .post(RequestBody.create(body.toString(),
                            MediaType.parse("application/json")))
                    .build();

            new OkHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Demo user creation failed", e);
                    runOnUiThread(() -> {
                        demoButton.setEnabled(true);
                        demoButton.setText("Try Demo");
                        Toast.makeText(LoginActivity.this,
                                "Cannot reach server. Is the backend running?",
                                Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            goToDashboard(DEMO_UID);
                        } else {
                            demoButton.setEnabled(true);
                            demoButton.setText("Try Demo");
                            Toast.makeText(LoginActivity.this,
                                    "Demo setup failed (" + response.code() + ")",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error building demo request", e);
            demoButton.setEnabled(true);
            demoButton.setText("Try Demo");
        }
    }

    /**
     * Ensures the user exists in the backend database before navigating
     * to the Dashboard. POST /users returns the existing user if already
     * registered, so this is safe to call on every login.
     */
    private void ensureUserInBackendAndGo(String uid, String email) {
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid", uid);
            body.put("email", email != null ? email : "");
            body.put("monthly_income", 0.0);

            Request request = new Request.Builder()
                    .url(ApiClient.getBaseUrl() + "/users")
                    .post(RequestBody.create(body.toString(),
                            MediaType.parse("application/json")))
                    .build();

            new OkHttpClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    // Server unreachable — go to dashboard anyway,
                    // fragments will show their own error states
                    Log.e(TAG, "Backend sync failed", e);
                    runOnUiThread(() -> goToDashboard(uid));
                }

                @Override
                public void onResponse(Call call, Response response) {
                    response.close();
                    runOnUiThread(() -> goToDashboard(uid));
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error syncing user", e);
            goToDashboard(uid);
        }
    }

    private void goToDashboard(String firebaseUid) {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.putExtra("firebase_uid", firebaseUid);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}