package com.example.israelcities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.res.AssetManager;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.data.Feature;
import com.google.maps.android.data.Geometry;
import com.google.maps.android.data.geojson.GeoJsonFeature;
import com.google.maps.android.data.geojson.GeoJsonLayer;
import com.google.maps.android.data.geojson.GeoJsonPolygonStyle;

import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private GeoJsonLayer cityLayer;
    private List<GeoJsonFeature> allFeatures = new ArrayList<>();
    private GeoJsonFeature currentQuestionFeature;
    private TextView questionText;
    private Button skipButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        questionText = findViewById(R.id.question_text);
        skipButton = findViewById(R.id.skip_button);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        mapFragment.getMapAsync(this);

        skipButton.setOnClickListener(v -> {
            // If user wants to skip the current question, pick a new one
            pickNewRandomCity();
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Center the map roughly on Israel; zoom level ~ 6–7 shows all cities
        LatLng israelCenter = new LatLng(31.5, 34.75);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(israelCenter, 6.7f));

        // Load the GeoJSON containing Israeli city boundaries, style, and set up click listeners
        try {
            loadGeoJsonLayer();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load map data.", Toast.LENGTH_LONG).show();
        }
    }

    private void loadGeoJsonLayer() {
        try {
            // 1. Construct the layer from the raw resource
            cityLayer = new GeoJsonLayer(mMap, R.raw.israel_cities, getApplicationContext());

            // 3. Style each polygon
            for (GeoJsonFeature feature : cityLayer.getFeatures()) {
                String geomType = feature.getGeometry().getGeometryType();
                if ("Polygon".equals(geomType) || "MultiPolygon".equals(geomType)) {
                    GeoJsonPolygonStyle polygonStyle = new GeoJsonPolygonStyle();
                    // Temporarily use a bold style for debugging
                    polygonStyle.setFillColor(Color.argb(50, 0, 255, 0));   //  translucent green
                    polygonStyle.setStrokeColor(Color.MAGENTA);              //  thick magenta outline
                    polygonStyle.setStrokeWidth(8f);
                    feature.setPolygonStyle(polygonStyle);
                    allFeatures.add(feature);
                }
            }

            // 4. Add the layer to the map (after styling)
            cityLayer.addLayerToMap();

            // 5. Set up click logging + handler
            cityLayer.setOnFeatureClickListener(clickedFeature -> {
                String engName = clickedFeature.getProperty("MUN_ENG");
                handleCityClick(clickedFeature);
            });

            // 6. Start the quiz
            pickNewRandomCity();

        } catch (IOException | JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load GeoJSON.", Toast.LENGTH_LONG).show();
        }
    }


    /** Randomly select one city feature as the “question” and update the UI. */
    private void pickNewRandomCity() {
        if (allFeatures.isEmpty()) return;

        // Reset any previous polygon style (in case we highlighted it)
        if (currentQuestionFeature != null) {
            resetFeatureStyle(currentQuestionFeature);
        }

        // Pick a random feature
        Random rand = new Random();
        int index = rand.nextInt(allFeatures.size());
        currentQuestionFeature = allFeatures.get(index);

        // The city name should be stored as a property, e.g., "name" or "NAME"
        // Adjust this key based on your GeoJSON’s property name.
        String cityName = currentQuestionFeature.getProperty("name");
        if (cityName == null) {
            cityName = currentQuestionFeature.getProperty("NAME_1"); // fallback if property differs
        }
        questionText.setText("Tap on: " + cityName);
    }

    /** Called when the user taps on any polygon feature. */
    private void handleCityClick(Feature clickedFeature) {
        if (!(clickedFeature instanceof GeoJsonFeature)) return;
        GeoJsonFeature tapped = (GeoJsonFeature) clickedFeature;

        String tappedName = tapped.getProperty("name");
        if (tappedName == null) {
            tappedName = tapped.getProperty("NAME_1");
        }
        String targetName = currentQuestionFeature.getProperty("name");
        if (targetName == null) {
            targetName = currentQuestionFeature.getProperty("NAME_1");
        }

        if (tappedName != null && tappedName.equals(targetName)) {
            // Correct answer
            highlightFeature(tapped, Color.argb(100, 0, 255, 0)); // translucent green
            Toast.makeText(this, "Correct! That is " + tappedName, Toast.LENGTH_SHORT).show();
            // Wait a moment, then pick the next one
            tapped.getPolygonStyle().setStrokeColor(Color.GREEN);
            tapped.getPolygonStyle().setStrokeWidth(5);
            mMap.clear(); // remove all polygons
            cityLayer.addLayerToMap(); // re-add layer so highlight shows
            pickNewRandomCity();
        } else {
            // Incorrect answer
            highlightFeature(tapped, Color.argb(100, 255, 0, 0)); // translucent red
            Toast.makeText(this, "Nope! That’s " + tappedName, Toast.LENGTH_SHORT).show();
            tapped.getPolygonStyle().setStrokeColor(Color.RED);
            tapped.getPolygonStyle().setStrokeWidth(5);
            mMap.clear();
            cityLayer.addLayerToMap();
            // Keep same question; user can try again or skip
        }
    }

    /** Highlights a polygon’s fill color temporarily to indicate right/wrong. */
    private void highlightFeature(GeoJsonFeature feature, int fillColor) {
        GeoJsonPolygonStyle style = feature.getPolygonStyle();
        style.setFillColor(fillColor);
        // Re-render layer by clearing and re-adding
        mMap.clear();
        cityLayer.addLayerToMap();
    }

    /** Resets a feature back to default style (transparent fill, thin gray stroke). */
    private void resetFeatureStyle(GeoJsonFeature feature) {
        GeoJsonPolygonStyle style = new GeoJsonPolygonStyle();
        style.setFillColor(Color.argb(0, 0, 0, 0)); // transparent
        style.setStrokeColor(Color.DKGRAY);
        style.setStrokeWidth(2);
        feature.setPolygonStyle(style);
    }
}