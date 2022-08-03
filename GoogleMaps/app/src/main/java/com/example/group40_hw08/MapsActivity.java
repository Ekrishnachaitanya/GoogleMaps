/* Assignment: HW08
   File Name: Group40_HW08
   Student Names: Krishna Chaitanya Emmala, Naga Sivaram Mannam
*/
package com.example.group40_hw08;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.group40_hw08.databinding.ActivityMapsBinding;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.gson.Gson;
import com.google.maps.android.PolyUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;
    Place startPlace = null, endPlace = null;
    OkHttpClient client = new OkHttpClient();
    String apiKey = "";
    String TAG = "demo";

    public void buildBounds(LatLngBounds.Builder builder, ArrayList<LatLng> coordinates, Result result){
        for(Route route: result.getRoutes()){
            List<LatLng> decode = PolyUtil.decode(route.getOverview_polyline().getPoints());
            coordinates.addAll(decode);
            for(LatLng latLng: decode)
                builder.include(latLng);
        }
    }

    public void addPolyline(ArrayList<LatLng> coordinates){
        mMap.addPolyline(new PolylineOptions()
                .clickable(true)
                .addAll(coordinates)
                .width(10)
                .color(Color.BLUE)
                .geodesic(true));
    }

    public void addMarker(Result result){
        ArrayList<LatLng> coordinates = new ArrayList<>();
        for(Route route: result.getRoutes()){
            List<LatLng> decode = PolyUtil.decode(route.getOverview_polyline().getPoints());
            coordinates.addAll(decode);
        }
        LatLng start = coordinates.get(0);
        LatLng end = coordinates.get(coordinates.size()-1);
        mMap.addMarker(new MarkerOptions().position(start).title("Start"));
        mMap.addMarker(new MarkerOptions().position(end).title("End"));
    }

    public void getOptimalDistance(){
        HttpUrl.Builder builder = new HttpUrl.Builder();
        HttpUrl url = builder.scheme("https")
                .host("maps.googleapis.com")
                .addPathSegment("maps")
                .addPathSegment("api")
                .addPathSegment("directions")
                .addPathSegment("json")
                .addQueryParameter(getString(R.string.originLabel), getString(R.string.placePrefix)+startPlace.getId())
                .addQueryParameter(getString(R.string.destinationLabel), getString(R.string.placePrefix)+endPlace.getId())
                .addQueryParameter(getString(R.string.keyLabel), getString(R.string.apiKey))
                .build();
        Request request = new Request.Builder()
                .url(url)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.d(TAG, "onFailure: "+e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if(response.isSuccessful()){
                    Gson gson = new Gson();
                    String responseBody = response.body().string();
                    Result result= gson.fromJson(responseBody, Result.class);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ArrayList<LatLng> coordinates = new ArrayList<>();
                            LatLngBounds.Builder builder = new LatLngBounds.Builder();
                            buildBounds(builder, coordinates, result);
                            addPolyline(coordinates);
                            addMarker(result);
                            LatLngBounds bounds = builder.build();
                            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 20));
                        }
                    });
                }
            }
        });
    }

    public void getNearByPlaces(String type){
        if(startPlace == null){
            Toast.makeText(this, getString(R.string.selectStartLocationLabel), Toast.LENGTH_SHORT).show();
            return;
        }
        mMap.clear();
        mMap.addMarker(new MarkerOptions().position(startPlace.getLatLng()).title(startPlace.getName()));
        HttpUrl.Builder builder = new HttpUrl.Builder();
        HttpUrl url = builder.scheme("https")
                .host("maps.googleapis.com")
                .addPathSegment("maps")
                .addPathSegment("api")
                .addPathSegment("place")
                .addPathSegment("nearbysearch")
                .addPathSegment("json")
                .addQueryParameter(getString(R.string.locationLabel), startPlace.getLatLng().latitude+","+startPlace.getLatLng().longitude)
                .addQueryParameter(getString(R.string.typeLabel), type)
                .addQueryParameter(getString(R.string.radiusLabel), 50000+"")
                .addQueryParameter(getString(R.string.keyLabel), apiKey)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.d(TAG, "onFailure: "+e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                Log.d(TAG, "onResponse: "+response);
                if(response.isSuccessful()){
                    String responseBody = response.body().string();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            LatLngBounds.Builder builder = new LatLngBounds.Builder();
                            builder.include(startPlace.getLatLng());
                            Log.d(TAG, "run: "+responseBody);
                            try {
                                JSONObject parent = new JSONObject(responseBody);
                                JSONArray resultsArray = parent.getJSONArray("results");
                                if(resultsArray.length()>0){
                                    for(int i=0; i<resultsArray.length(); i++){
                                        JSONObject jsonObject = resultsArray.getJSONObject(i);
                                        JSONObject locationObj = jsonObject.getJSONObject("geometry").getJSONObject("location");
                                        String latitude = locationObj.getString("lat");
                                        String longitude = locationObj.getString("lng");
                                        String name = jsonObject.getString("name");
                                        LatLng latLng = new LatLng(Double.parseDouble(latitude), Double.parseDouble(longitude));
                                        builder.include(latLng);
                                        MarkerOptions markerOptions = new MarkerOptions();
                                        markerOptions.title(name);
                                        markerOptions.position(latLng);
                                        mMap.addMarker(markerOptions);
                                    }
                                    LatLngBounds bounds = builder.build();
                                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 20));
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        apiKey = getString(R.string.apiKey);
        if(!Places.isInitialized()){
            Places.initialize(getApplicationContext(), apiKey);
        }

        PlacesClient placesClient = Places.createClient(this);

        binding.restaurantTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getNearByPlaces(getString(R.string.restaurantLabel));
            }
        });

        binding.gasStationTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getNearByPlaces(getString(R.string.gasStationLabel));
            }
        });

        AutocompleteSupportFragment autocompleteSupportFragment = (AutocompleteSupportFragment) getSupportFragmentManager().findFragmentById(R.id.autocomplete_fragment);
        autocompleteSupportFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG));
        autocompleteSupportFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onError(@NonNull Status status) {

            }

            @Override
            public void onPlaceSelected(@NonNull Place place) {
                startPlace = place;
                mMap.clear();
                if(endPlace != null){
                    getOptimalDistance();
                }else{
                    mMap.addMarker(new MarkerOptions().position(place.getLatLng()).title(place.getName()));
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLng(), 15f));
                }
            }
        });

        AutocompleteSupportFragment autocompleteSupportFragment2 = (AutocompleteSupportFragment) getSupportFragmentManager().findFragmentById(R.id.autocomplete_fragment2);
        autocompleteSupportFragment2.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG));
        autocompleteSupportFragment2.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onError(@NonNull Status status) {

            }

            @Override
            public void onPlaceSelected(@NonNull Place place) {
                endPlace = place;
                mMap.clear();
                if(startPlace != null){
                    getOptimalDistance();
                }else{
                    mMap.addMarker(new MarkerOptions().position(place.getLatLng()).title(place.getName()));
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLng(), 15f));
                }
            }
        });
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
    }
}