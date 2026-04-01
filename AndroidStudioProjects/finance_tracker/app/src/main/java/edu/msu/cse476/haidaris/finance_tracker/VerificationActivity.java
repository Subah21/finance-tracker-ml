package edu.msu.cse476.haidaris.finance_tracker;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * This screen is shown ONLY if needed as a "waiting" screen.
 * Firebase email verification works via a LINK in the email — not a code.
 * The user clicks the link in their email, then comes back and taps
 * "I've verified my email" to continue.
 *
 * NOTE: With the current LoginActivity fix, this screen is no longer
 * needed in the main flow. It's kept here in case you want to use it
 * as an informational screen after signup.
 */
public class VerificationActivity extends AppCompatActivity {

    FirebaseAuth auth;
    Button checkVerifiedButton, resendButton;
    TextView infoText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verification);

        auth = FirebaseAuth.getInstance();

        // If your layout still has codeInput, you can remove it — we don't need it
        // Update your activity_verification.xml to show these instead:
        //   - A text saying "Check your email and click the verification link"
        //   - A button "I've verified my email" → checkVerifiedButton
        //   - A button "Resend email" → resendButton

        checkVerifiedButton = findViewById(R.id.verifyButton);  // reuse existing button ID

        checkVerifiedButton.setText("I've verified my email");

        checkVerifiedButton.setOnClickListener(v -> {

            FirebaseUser user = auth.getCurrentUser();

            if (user != null) {
                // Reload the user to get fresh verification status from Firebase
                user.reload().addOnCompleteListener(task -> {

                    if (user.isEmailVerified()) {
                        // Verified --> go to Dashboard
                        Intent intent = new Intent(this, DashboardActivity.class);
                        intent.putExtra("firebase_uid", user.getUid());
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(this,
                                "Email not verified yet. Check your inbox and click the link.",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
}