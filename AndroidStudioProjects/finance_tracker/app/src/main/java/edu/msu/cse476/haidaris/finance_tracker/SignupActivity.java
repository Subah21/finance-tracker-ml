package edu.msu.cse476.haidaris.finance_tracker;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
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

public class SignupActivity extends AppCompatActivity {

    FirebaseAuth auth;
    EditText signupEmail, signupPassword;
    Button signupButton;

    // OkHttpClient for calling your backend API
    // NOTE: In a real app you'd use Retrofit, but OkHttp is simpler to add quickly
    // the above comment was generated using claude.
    OkHttpClient httpClient = new OkHttpClient();

    // Backend API deployed on Google Cloud Run (works on any network)
    static final String API_BASE_URL = "https://finance-tracker-api-870049862947.us-central1.run.app";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        auth = FirebaseAuth.getInstance();

        signupEmail    = findViewById(R.id.signupEmail);
        signupPassword = findViewById(R.id.signupPassword);
        signupButton   = findViewById(R.id.signupButton);

        signupButton.setOnClickListener(v -> {

            String email    = signupEmail.getText().toString().trim();
            String password = signupPassword.getText().toString().trim();

            // Basic validation before calling Firebase
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Step 1: Create account in Firebase
            auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {

                        if (task.isSuccessful()) {

                            FirebaseUser user = auth.getCurrentUser();

                            // Step 2: Send verification email
                            user.sendEmailVerification()
                                    .addOnCompleteListener(emailTask -> {

                                        if (emailTask.isSuccessful()) {

                                            // Step 3: Save user to your SQL database
                                            // This runs in the background so the UI stays responsive
                                            saveUserToDatabase(user.getUid(), email);

                                            Toast.makeText(this,
                                                    "Account created! Check your email to verify your account.",
                                                    Toast.LENGTH_LONG).show();

                                            // Step 4: Go to login screen
                                            // User must verify email before they can log in
                                            startActivity(new Intent(this, LoginActivity.class));
                                            finish();

                                        } else {
                                            Toast.makeText(this,
                                                    "Account created but verification email failed. Try logging in.",
                                                    Toast.LENGTH_LONG).show();
                                            startActivity(new Intent(this, LoginActivity.class));
                                            finish();
                                        }
                                    });

                        } else {
                            // Firebase signup failed --> show the reason why
                            String error = task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Unknown error";
                            Toast.makeText(this,
                                    "Signup failed: " + error,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    /**
     * Saves the new user to your SQLite database via backend_API.
     * Called after Firebase account is created successfully.
     * Runs on a background thread and does not block the UI.
     */
    private void saveUserToDatabase(String firebaseUid, String email) {
        try {
            // Builds the JSON body to send to POST /users
            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);
            body.put("email", email);
            body.put("monthly_income", 0.0);  // user sets this later in profile

            RequestBody requestBody = RequestBody.create(
                    body.toString(),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(API_BASE_URL + "/users")
                    .post(requestBody)
                    .build();

            // Async call which runs in the background, won't freeze the UI
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    // User saved to DB successfully
                    response.close();
                }

                @Override
                public void onFailure(Call call, IOException e) {
                    // API call failed which is okay for now since there is more
                    // to work on, so the user can still use the app
                }
            });

        } catch (Exception e) {
            // JSON building failed it shouldn't happen but we still need to handle gracefully if it does
            e.printStackTrace();
        }
    }
}