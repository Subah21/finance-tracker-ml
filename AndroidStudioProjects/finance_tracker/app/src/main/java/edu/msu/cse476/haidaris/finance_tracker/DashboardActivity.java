package edu.msu.cse476.haidaris.finance_tracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;

public class DashboardActivity extends AppCompatActivity {

    private String firebaseUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Get Firebase UID passed from LoginActivity
        firebaseUid = getIntent().getStringExtra("firebase_uid");

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Set up ViewPager2 with 3 fragments
        ViewPager2 viewPager = findViewById(R.id.viewPager);
        viewPager.setAdapter(new DashboardPagerAdapter(this));

        // Link TabLayout to ViewPager2
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("Overview");     break;
                case 1: tab.setText("Transactions"); break;
                case 2: tab.setText("Budget");       break;
            }
        }).attach();
    }

    // Toolbar menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu which adds the Logout to the top-right overflow menu
        getMenuInflater().inflate(R.menu.menu_dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            // Sign out from Firebase and clears the cached session
            FirebaseAuth.getInstance().signOut();

            // Go back to LoginActivity, clear the back stack
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Getter for fragments

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
                case 0:  return new OverviewFragment();
                case 1:  return new TransactionsFragment();
                case 2:  return new BudgetFragment();
                default: return new OverviewFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }
}