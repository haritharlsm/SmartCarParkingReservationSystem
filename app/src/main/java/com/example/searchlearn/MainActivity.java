package com.example.searchlearn;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QuerySnapshot;
import android.Manifest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "ParkingSpaceDetails";
    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    private Button searchButton;
    private ListView dataListView;
    FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        searchButton = findViewById(R.id.searchButton);
        dataListView = findViewById(R.id.dataListView);

    }
    protected void onResume() {
        super.onResume();
        fetchdata();
    }
    public void onSearchButtonClick(View view) {
        // Toggle visibility of the ListView
        if (dataListView.getVisibility() == View.VISIBLE) {
            dataListView.setVisibility(View.GONE);
        } else {
            dataListView.setVisibility(View.VISIBLE);
            fetchdata();
        }
    }
public void fetchdata(){
    ProgressBar progressCircle = findViewById(R.id.progressBar2);
    progressCircle.setVisibility(View.VISIBLE);

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},
                MY_PERMISSIONS_REQUEST_LOCATION);
    } else {
        // Permissions already granted, proceed with location retrieval
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        double mobilelatitude = location.getLatitude();
                        double mobilelongitude = location.getLongitude();
                        Log.d(TAG, "latitude : "+mobilelatitude+ " longitude "+mobilelongitude);

                        db.collection("PrarkingSpaces")
                                .get()
                                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                    @Override
                                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                        progressCircle.setVisibility(View.GONE);
                                        if (task.isSuccessful()) {
                                            List<String> spaceNames = new ArrayList<>();
                                            List<String> spaceIds = new ArrayList<>();
                                            List<DistanceInfo> distances = new ArrayList<>();

                                            for (DocumentSnapshot document : task.getResult()) {
                                                String spaceName = document.getString("SpaceName");
                                                String spaceId = document.getString("Id");
                                                GeoPoint location = document.getGeoPoint("Location");
                                                Log.d(TAG, "spaceName of location: " + spaceName);
                                                Log.d(TAG, "inside all location: " + location);
                                                spaceNames.add(spaceName);
                                                spaceIds.add(spaceId);

                                                if (location != null) {
                                                    double latitude = location.getLatitude();
                                                    double longitude = location.getLongitude();

                                                    String apikey ="AIzaSyCLCo_IFnlvaAyP9GaLHTKutMQQg2MXscQ";
                                                    String distanceUrl = "https://maps.googleapis.com/maps/api/distancematrix/json?origins=" + mobilelatitude + "," + mobilelongitude + "&destinations=" + latitude + "," + longitude + "&key=" + apikey;
                                                    Log.d(TAG, "APi url : "+distanceUrl);

                                                    RequestQueue queue = Volley.newRequestQueue(MainActivity.this);
                                                    StringRequest stringRequest = new StringRequest(Request.Method.GET, distanceUrl,
                                                            response -> {
                                                                try {
                                                                    JSONObject jsonObject = new JSONObject(response);
                                                                    JSONArray rows = jsonObject.getJSONArray("rows");
                                                                    JSONObject elements = rows.getJSONObject(0).getJSONArray("elements").getJSONObject(0);
                                                                    JSONObject distance = elements.getJSONObject("distance");
                                                                    String text = distance.getString("text");
                                                                    Log.d(TAG, "text: " + text);
                                                                    Log.d(TAG, "Adding DistanceInfo - Space Name: " + spaceName + ", Distance: " + text+"spaceid"+spaceId);
                                                                    distances.add(new DistanceInfo(spaceName, text,spaceId));

                                                                    // Check if both spaceNames and distances lists have the same size
                                                                    if (spaceNames.size() == distances.size()) {
                                                                        updateListView(distances);
                                                                    }

//                                                                    updateListView(spaceNames, spaceIds, distances);
                                                                } catch (JSONException e) {
                                                                    e.printStackTrace();
                                                                    Log.e(TAG, "Error parsing JSON response: ", e);
                                                                }
                                                            }, error -> {
                                                        Log.e(TAG, "Network error: ", error);
                                                    });
                                                    queue.add(stringRequest);
                                                } else {
                                                    Log.e(TAG, "Location field missing in document: " + document.getId());
                                                }
                                            }
                                        } else {
                                            Log.d(TAG, "Error getting documents: ", task.getException());
                                        }
                                    }
                                });
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "Get location error: ", e);
                });
    }
}
    private void updateListView(List<DistanceInfo> distances) {// Assuming distances is a List<DistanceInfo>
        Collections.sort(distances, new Comparator<DistanceInfo>() {
            @Override
            public int compare(DistanceInfo distanceInfo1, DistanceInfo distanceInfo2) {
                // Assuming distance is a string representing a number (e.g., "590 km")
                // You may need to extract the numeric value and compare it
                String distance1 = distanceInfo1.getDistance().replaceAll("[^\\d.]", "");
                String distance2 = distanceInfo2.getDistance().replaceAll("[^\\d.]", "");

                // Convert distances to double and compare
                return Double.compare(Double.parseDouble(distance1), Double.parseDouble(distance2));
            }
        });

            ArrayAdapter<DistanceInfo> adapter = new ArrayAdapter<DistanceInfo>(this, R.layout.list_item_layout, R.id.itemTextView, distances) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);

                    // Set the item text for the TextView
                    DistanceInfo distanceInfo = getItem(position);

                    // Set the item text for the TextView
                    TextView itemTextView = view.findViewById(R.id.itemTextView);
                    itemTextView.setText(distanceInfo.getSpaceName());

                    TextView distanceTextView = view.findViewById(R.id.distance1);
                    distanceTextView.setText(distanceInfo.getDistance());

                    String spaceId = distanceInfo.getSpaceId();

                    FirebaseFirestore db = FirebaseFirestore.getInstance();
                    db.collection("PrarkingSpaces")
                            .document(spaceId)
                            .collection("ParkingSlots")
                            .whereEqualTo("Status", "Available")
                            .get()
                            .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                                @Override
                                public void onSuccess(QuerySnapshot querySnapshot) {
                                    int availableSlotCount = querySnapshot.size();
                                    Log.e(TAG, "Get location error: "+availableSlotCount);
                                    TextView slotCountTextView = view.findViewById(R.id.countava);
                                    slotCountTextView.setText("Available Slots: " + availableSlotCount);
                                }
                            });
                    Button openButton = view.findViewById(R.id.openButton);
                    openButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            onOpenButtonClick(v, position, distances);
                        }
                    });
                    return view;
                }
            };
            dataListView.setAdapter(adapter);
    }

    public void onOpenButtonClick(View view, int position,List<DistanceInfo> distances) {
        DistanceInfo clickedDistance = distances.get(position);
        String clickedSpaceId = clickedDistance.getSpaceId();
        Intent intent = new Intent(this, DetailViewActivity.class);
        intent.putExtra("SPACE_ID", clickedSpaceId);
        startActivity(intent);
    }
}
