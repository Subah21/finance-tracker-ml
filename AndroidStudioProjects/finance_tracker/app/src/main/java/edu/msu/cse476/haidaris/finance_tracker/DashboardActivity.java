package edu.msu.cse476.haidaris.finance_tracker;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;

public class DashboardActivity extends AppCompatActivity
        implements LocationHelper.WarningCallback {

    private static final int LOCATION_PERMISSION_CODE = 100;
    private static final int NOTIFICATION_PERMISSION_CODE = 101;

    private String         firebaseUid;
    private LocationHelper locationHelper;

    // Warning banner which shown when user is near a store at risk
    private TextView warningBanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        firebaseUid = getIntent().getStringExtra("firebase_uid");

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Set up ViewPager2 with 3 tabs
        ViewPager2 viewPager = findViewById(R.id.viewPager);
        viewPager.setAdapter(new DashboardPagerAdapter(this));

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("Overview");     break;
                case 1: tab.setText("Transactions"); break;
                case 2: tab.setText("Budget");       break;
            }
        }).attach();

        // Warning banner
        warningBanner = findViewById(R.id.warningBanner);

        // Set up notification channel
        NotificationHelper.createChannel(this);

        // Set up location helper
        locationHelper = new LocationHelper(this);
        locationHelper.setWarningCallback(this);

        // Request permissions
        requestLocationPermission();
    }

    // Location permission

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_CODE);
        } else {
            // Permission already granted --> start tracking
            startLocationTracking();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationTracking();
            }
            // If denied then GPS just won't run, rest of app works fine
        }
    }

    private void startLocationTracking() {
        locationHelper.start();
    }

    // LocationHelper.WarningCallback

    /**
     * Called when user is near a big store AND at overspending risk.
     * Shows the in-app warning banner.
     */
    @Override
    public void onNearStoreAtRisk(String storeName, double riskPercent) {
        runOnUiThread(() -> {
            if (warningBanner != null) {
                warningBanner.setText(String.format(
                        "Warning: You're near %s with %.0f%% overspending risk!",
                        storeName, riskPercent
                ));
                warningBanner.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * Called every time GPS gets a new location fix.
     * We can use this to update a location display if needed. (need to change)
     */
    @Override
    public void onLocationUpdated(double lat, double lng) {
        // Optional: update a location display on the dashboard
        // For now just logs — the warning handles the important UI
    }

    /**
     * Called by OverviewFragment when spending data is loaded.
     * Updates the LocationHelper with fresh spending data for risk checks.
     */
    public void updateLocationSpendingData(double income, double aid,
                                           double food, double transport,
                                           double entertainment, double personalCare,
                                           double tech, double health, double misc) {
        if (locationHelper != null) {
            locationHelper.updateSpendingData(income, aid, food, transport,
                    entertainment, personalCare, tech, health, misc);
        }
    }

    // Lifecycle

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationHelper != null) {
            locationHelper.stop();
        }
    }

    // Toolbar menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            FirebaseAuth.getInstance().signOut();
            if (locationHelper != null) locationHelper.stop();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Getters

    public String getFirebaseUid() {
        return firebaseUid;
    }

    // ViewPager2 Adapter

    private static class DashboardPagerAdapter extends FragmentStateAdapter {

        public DashboardPagerAdapter(FragmentActivity activity) {
            super(activity);
        }

        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return new OverviewFragment();
                case 1: return new TransactionsFragment();
                case 2: return new BudgetFragment();
                default: return new OverviewFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }
}