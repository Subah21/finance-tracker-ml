package edu.msu.cse476.haidaris.finance_tracker;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Handles all GPS logic for the budget mode feature:
 *
 *   Gets the user's current GPS coordinates, Checks if user is near a "big store"
 *   using a simple distance check against known store types.
 *   For the demo we use a 300-meter radius threshold.
 *   When near a store, runs Daniel's OverspendingModel to check risk
 *   and If risk > 50%, it would trigger a notification and calls back to the
 *   Dashboard to show an in-app warning banner.
 *
 */
public class LocationHelper {

    private static final String TAG = "LocationHelper";

    // How often to check location (5 minutes = 300,000 ms)
    private static final long LOCATION_INTERVAL_MS = 30_000;

    // Distance threshold --> within 300m of a store = "near a store"
    private static final float STORE_RADIUS_METERS = 300f;

    // Risk threshold --> above this = send warning
    private static final double RISK_THRESHOLD = 0.5;

    // Known big store / mall types to watch out for (will change them in near future)
    private static final String[] BIG_STORE_KEYWORDS = {
            "walmart", "target", "costco", "meijer", "kroger",
            "mall", "plaza", "shopping", "market", "store",
            "best buy", "ikea", "kohl", "best buy"
    };

    private final Context             context;
    private final OverspendingModel   riskModel;
    private final FusedLocationProviderClient fusedClient;
    private       LocationCallback    locationCallback;
    private       WarningCallback     warningCallback;

    // User's current spending data which is set by DashboardActivity
    private double monthlyIncome    = 0;
    private double financialAid     = 0;
    private double food             = 0;
    private double transportation   = 0;
    private double entertainment    = 0;
    private double personalCare     = 0;
    private double technology       = 0;
    private double healthWellness   = 0;
    private double misc             = 0;

    // Prevents spamming the same notification repeatedly
    private boolean warningAlreadySent = false;

    /**
     * Callback interface — DashboardActivity implements this to show
     * the in-app warning banner when the user is near a store at risk.
     */
    public interface WarningCallback {
        void onNearStoreAtRisk(String storeName, double riskPercent);
        void onLocationUpdated(double lat, double lng);
    }

    public LocationHelper(Context context) {
        this.context      = context;
        this.riskModel    = new OverspendingModel(context);
        this.fusedClient  = LocationServices.getFusedLocationProviderClient(context);
    }

    /**
     * Sets the warning callback which is called when user is near a store at risk.
     */
    public void setWarningCallback(WarningCallback callback) {
        this.warningCallback = callback;
    }

    /**
     * Updates the user's spending data used for risk calculation.
     * Calls this whenever the spending summary is refreshed.
     */
    public void updateSpendingData(double income, double aid, double food,
                                   double transport, double entertainment,
                                   double personalCare, double tech,
                                   double health, double misc) {
        this.monthlyIncome  = income;
        this.financialAid   = aid;
        this.food           = food;
        this.transportation = transport;
        this.entertainment  = entertainment;
        this.personalCare   = personalCare;
        this.technology     = tech;
        this.healthWellness = health;
        this.misc           = misc;
        // Reset warning flag when spending data changes
        this.warningAlreadySent = false;
    }

    /**
     * Starts GPS location updates.
     * Call from DashboardActivity after permission is granted.
     */
    public void start() {
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted — cannot start tracking");
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                LOCATION_INTERVAL_MS
        )
                .setMinUpdateIntervalMillis(LOCATION_INTERVAL_MS / 2)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                Location location = result.getLastLocation();
                if (location == null) return;

                double lat = location.getLatitude();
                double lng = location.getLongitude();

                Log.d(TAG, "Location update: " + lat + ", " + lng);

                // Notify dashboard of location update
                if (warningCallback != null) {
                    warningCallback.onLocationUpdated(lat, lng);
                }

                // Check if near a big store
                checkNearbyStores(location);
            }
        };

        fusedClient.requestLocationUpdates(locationRequest, locationCallback,
                Looper.getMainLooper());
        Log.d(TAG, "Location tracking started");
    }

    /**
     * Stops GPS location updates. Call from onDestroy() in DashboardActivity.
     */
    public void stop() {
        if (locationCallback != null) {
            fusedClient.removeLocationUpdates(locationCallback);
            Log.d(TAG, "Location tracking stopped");
        }
    }

    /**
     * Checks if the user is near a big store using Android's Geocoder
     * to reverse-geocode the coordinates into an address/place name.
     *
     * If near a store AND at overspending risk → send warning.
     */
    private void checkNearbyStores(Location location) {
        // Use Android's Geocoder to get the place name at this location
        android.location.Geocoder geocoder =
                new android.location.Geocoder(context);

        try {
            java.util.List<android.location.Address> addresses =
                    geocoder.getFromLocation(
                            location.getLatitude(),
                            location.getLongitude(),
                            3  // get up to 3 results
                    );

            if (addresses == null || addresses.isEmpty()) return;

            for (android.location.Address address : addresses) {
                String placeName = "";

                // Build a searchable string from all address fields
                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                    placeName += address.getAddressLine(i).toLowerCase() + " ";
                }
                if (address.getFeatureName() != null) {
                    placeName += address.getFeatureName().toLowerCase();
                }

                Log.d(TAG, "Nearby place: " + placeName);

                // Check if any big store keyword matches
                for (String keyword : BIG_STORE_KEYWORDS) {
                    if (placeName.contains(keyword)) {
                        String displayName = address.getFeatureName() != null
                                ? address.getFeatureName()
                                : "a nearby store";

                        Log.d(TAG, "Big store detected: " + displayName);
                        checkRiskAndNotify(displayName);
                        return;
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Geocoder error: " + e.getMessage());
        }
    }

    /**
     * Runs the ML model and sends warning if risk is high enough.
     */
    private void checkRiskAndNotify(String storeName) {
        if (warningAlreadySent) return;

        double riskProbability = riskModel.predictRiskProbability(
                monthlyIncome, financialAid, food, transportation,
                entertainment, personalCare, technology, healthWellness, misc
        );

        double riskPercent = riskProbability * 100;
        Log.d(TAG, "Risk near " + storeName + ": " + riskPercent + "%");

        if (riskProbability >= RISK_THRESHOLD) {
            warningAlreadySent = true;

            // Send push notification
            NotificationHelper.sendWarning(context, storeName, riskPercent);

            // Show in-app warning via callback
            if (warningCallback != null) {
                warningCallback.onNearStoreAtRisk(storeName, riskPercent);
            }

            Log.d(TAG, "Warning sent for " + storeName);
        }
    }

    /**
     * Resets the warning flag so a new warning can be sent.
     * Call this when the user's spending data is refreshed.
     */
    public void resetWarning() {
        warningAlreadySent = false;
    }
}