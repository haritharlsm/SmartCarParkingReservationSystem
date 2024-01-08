package com.example.searchlearn;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;



public class DetailViewActivity extends AppCompatActivity {

    private Button reserveNowButton;
    private String storedSpaceId;
    private String storedClickedItem;
    private ListView TableListView;
    private static final String TAG = "ParkingSlots";
    String gateStatus;

    private static final String BROKER = "tcp://test.mosquitto.org:1883";
    private static final String GATECONTROLTOPIC = "gate";

    private static final String PARKINGSTATUSTOPIC = "parkingStatus";

    private static final String clientId = MqttClient.generateClientId();
    private MqttClient mqttClient;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail_view);
        reserveNowButton = findViewById(R.id.reserveNow);
        ProgressBar progressCircle = findViewById(R.id.progressBar7);
        progressCircle.setVisibility(View.VISIBLE);
        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh(){
                refreshParkingData();
                swipeRefreshLayout.setRefreshing(false);
            }
        });
        SharedPreferences sharedPref = getSharedPreferences("myPrefs", MODE_PRIVATE);
        String spaceIds = sharedPref.getString("spaceId", null);
        String clickIds = sharedPref.getString("clickedId", null);

        Button doorOpenButton = findViewById(R.id.doorOpen);
        if (spaceIds != null) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("PrarkingSpaces")
                .document(spaceIds)
                .collection("ParkingSlots")
                .document(clickIds)
                .get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot){
                        if (documentSnapshot.exists()) {
                            boolean isReserved = documentSnapshot.getBoolean("Reserved");
                            if (isReserved) {
                                doorOpenButton.setVisibility(View.VISIBLE);
                                doorOpenButton.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        db.collection("PrarkingSpaces")
                                                .document(spaceIds)
                                                .collection("ParkingSlots")
                                                .document(clickIds)
                                                .update("Reserved", false)  // Update the Reserved field to false
                                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                                    @Override
                                                    public void onSuccess(Void aVoid) {
                                                        // Handle successful update & Clear shared preference value:
                                                        SharedPreferences.Editor editor = sharedPref.edit();
                                                        editor.remove("spaceId");
                                                        editor.remove("clickedId");
                                                        editor.apply();
                                                        Toast.makeText(DetailViewActivity.this, "successfully Door Open", Toast.LENGTH_SHORT).show();
                                                    }
                                                }).addOnFailureListener(new OnFailureListener() {
                                                    @Override
                                                    public void onFailure(@NonNull Exception e){
                                                        Log.w(TAG, "Error updating Reserved field", e);
                                                    }
                                                });
                                        gateStatus = "open";
                                        gateControl(gateStatus);
                                        doorOpenButton.setVisibility(View.GONE);
                                    }
                                });
                                // Handle the case where the slot is reserved
                                Toast.makeText(DetailViewActivity.this, "Slot is reserved", Toast.LENGTH_SHORT).show();
                            }else {
                                SharedPreferences.Editor editor = sharedPref.edit();
                                editor.remove("spaceId");
                                editor.remove("clickedId");
                                editor.apply();
                            }
                        } else {
                            Log.w(TAG, "Document does not exist");
                        }
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Handle errors
                        Log.w(TAG, "Error getting document", e);
                    }
                });
        }
        try {
            Log.w(TAG, "Mqtt try");
            mqttClient = new MqttClient(BROKER, clientId, new MemoryPersistence());
            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true);
            mqttClient.connect(connectOptions);

            mqttClient.subscribe(PARKINGSTATUSTOPIC); // Subscribe to the MQTT topic
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    Log.w(TAG, "MQTT connection lost", cause);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String mqttValue = new String(message.getPayload());  //here passed the values,values get from the payload
                    Log.w(TAG, "MQTT mqttValue "+ mqttValue);
                    String[] resultArray = mqttValue.split(",");
                    Log.w(TAG, "space is "+ resultArray[1]);
                    Log.w(TAG, "slot id  "+ resultArray[2]);
                    if (mqttValue != null) {
                        updateStatusMqtt(resultArray[0], resultArray[1], resultArray[2]); // Replace values spaceid and clickedid
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Mqtt catch");
            e.printStackTrace();
        }
        TableListView = findViewById(R.id.TableListView);
        Intent intent = getIntent();
        if (intent != null) {
            String spaceId = intent.getStringExtra("SPACE_ID");
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("PrarkingSpaces")
                .document(spaceId)
                .collection("ParkingSlots")  // Access the subcollection
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        progressCircle.setVisibility(View.GONE);
                        if (task.isSuccessful()) {
                            List<String> slotName = new ArrayList<>();
                            List<String> slotStatus = new ArrayList<>();
                            List<String> slotIds = new ArrayList<>();

                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String field1Value = document.getString("SlotName");
                                String field2Value = document.getString("Status");
                                String field3Value = document.getString("Id");

                                slotName.add(field1Value);
                                slotStatus.add(field2Value);
                                slotIds.add(field3Value);
                                updateTableListView(slotName,slotStatus,slotIds,spaceId);
                            }
                        } else {
                            Log.d(TAG, "Error getting documents: ", task.getException());
                        }
                    }
                });
        }
    }
    private void refreshParkingData() {
        String spaceId = getIntent().getStringExtra("SPACE_ID");
        final ProgressDialog progressDialog = ProgressDialog.show(this, "", "Refreshing data...", true);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("PrarkingSpaces")
                .document(spaceId)
                .collection("ParkingSlots")
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task){
                        progressDialog.dismiss();
                        if (task.isSuccessful()) {
                            List<String> slotName = new ArrayList<>();
                            List<String> slotStatus = new ArrayList<>();
                            List<String> slotIds = new ArrayList<>();

                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String field1Value = document.getString("SlotName");
                                String field2Value = document.getString("Status");
                                String field3Value = document.getString("Id");

                                slotName.add(field1Value);
                                slotStatus.add(field2Value);
                                slotIds.add(field3Value);
                            }
                            updateTableListView(slotName, slotStatus, slotIds, spaceId);  // Update the UI
                        } else {
                            Log.w(TAG, "Error getting documents: ", task.getException());
                        }
                    }
                });
    }
    private void updateStatusMqtt( String spaceStatus, String spaceId, String clickedItem) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference docRef = db.collection("PrarkingSpaces")
                .document(spaceId)
                .collection("ParkingSlots")
                .document(clickedItem);
                docRef.get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DocumentSnapshot> task){
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        String currentStatus = document.getString("Status");
                        Timestamp firestoreTime = document.getTimestamp("Time");
                        if (spaceStatus.equals("Available") && currentStatus.equals("Pending")) {
                            Log.d("TAG", "No update needed: Space is already pending.");
                            if (firestoreTime != null) {
                                long firestoreTimeInMillis = firestoreTime.toDate().getTime();
                                long timeDifference = (System.currentTimeMillis() - firestoreTimeInMillis) / (1000 * 60);
                                if (timeDifference >= 10) {
                                    docRef.update("Status", "Available")
                                            .addOnSuccessListener(new OnSuccessListener<Void>() {
                                                @Override
                                                public void onSuccess(Void aVoid) {
                                                    Log.d("TAG", "Firestore updated: Status set to Available");
                                                }
                                            })
                                            .addOnFailureListener(new OnFailureListener() {
                                                @Override
                                                public void onFailure(@NonNull Exception e) {
                                                    Log.w("TAG", "Firestore update failed", e);
                                                }
                                            });
                                } else {
                                    Log.d("TAG", "Not updating yet: Time difference less than 10 minutes");
                                }
                            } else {
                                Log.w("TAG", "Firestore Time field is missing");
                            }
                        } else {
                            docRef.update("Status", spaceStatus, "Reserved", false,"Time", FieldValue.delete())
                                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                                        @Override
                                        public void onSuccess(Void aVoid) {
                                            Log.d("TAG", "Firestore updated successfully!");
                                        }
                                    })
                                    .addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e){
                                            Log.w("TAG", "Firestore update failed", e);
                                        }
                                    });
                        }
                    } else {
                        Log.d("TAG", "Document does not exist");
                    }
                } else {
                    Log.w("TAG", "Error getting document", task.getException());
                }
            }
        });
    }

    private void updateTableListView(List<String> slotName,List<String> slotStatus,List<String> slotIds,String spaceId) {
    ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.tablerow, R.id.itemTextView1, slotName) {
        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            View view = super.getView(position, convertView, parent);

            TextView itemTextView1 = view.findViewById(R.id.itemTextView1);
            TextView itemTextView2 = view.findViewById(R.id.itemTextView2);
            CheckBox checkBox = view.findViewById(R.id.checkbox1);

            itemTextView1.setText(getItem(position));
            String statusText = slotStatus.get(position);
            itemTextView2.setTextColor(
                    statusText.equalsIgnoreCase("Available") ? Color.parseColor("#008000") :
                    statusText.equalsIgnoreCase("Pending") ? Color.parseColor("#FFA500") : Color.RED);
            itemTextView2.setText(statusText);

            checkBox.setEnabled(statusText.equalsIgnoreCase("Available"));
            checkBox.setOnClickListener(v -> {
                int clickedPosition = (int) v.getTag();
                String clickedItem = slotIds.get(clickedPosition);

                if (checkBox.isChecked()) { // Check if the checkbox is checked
                    storedSpaceId = spaceId;
                    storedClickedItem = clickedItem;

                    if (reserveNowButton.getVisibility() == View.VISIBLE) {
                        reserveNowButton.setVisibility(View.INVISIBLE);
                    } else {
                        reserveNowButton.setVisibility(View.VISIBLE);
                    }
                } else { // Checkbox is unchecked
                    storedSpaceId = null;
                    storedClickedItem = null;
                    reserveNowButton.setVisibility(View.INVISIBLE); // Hide the button
                }
            });
            reserveNowButton.setOnClickListener(v -> {
                changeStatusValue(storedSpaceId, storedClickedItem);
            });
            checkBox.setTag(position);

            return view;
        }
    };
    TableListView.setAdapter(adapter);
    }
    private void changeStatusValue(String spaceId, String clickedItem) {
        final ProgressDialog progressDialog = ProgressDialog.show(DetailViewActivity.this, "", "Updating status..", true);
        Timestamp timeNow = Timestamp.now();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("PrarkingSpaces")
                .document(spaceId)
                .collection("ParkingSlots")
                .document(clickedItem)
                .update("Status", "Pending",
                        "Time", timeNow,
                        "Reserved", true)  // Update the Status field
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        SharedPreferences sharedPref = getSharedPreferences("myPrefs", MODE_PRIVATE);
                                        SharedPreferences.Editor editor = sharedPref.edit();
                                        editor.putString("spaceId", spaceId);
                                        editor.putString("clickedId", clickedItem);
                                        editor.apply(); // Or editor.commit();
                                        Toast.makeText(DetailViewActivity.this, "successfully Reserved", Toast.LENGTH_SHORT).show();
                                        progressDialog.dismiss();
                                        finish();
                                        startActivity(getIntent());
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Toast.makeText(DetailViewActivity.this, "Error updating status " + e, Toast.LENGTH_SHORT).show();
                                        progressDialog.dismiss();
                                    }
                                });


    }

    private void gateControl(String GateStatus){
        try {
            MqttClient mqttClient = new MqttClient(BROKER, clientId, new MemoryPersistence());
            MqttConnectOptions connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true);

            mqttClient.connect(connectOptions);
            mqttClient.publish(GATECONTROLTOPIC, GateStatus.getBytes(),0, false);
        }catch(Exception ex){
            ex.printStackTrace();
        }

    }
}