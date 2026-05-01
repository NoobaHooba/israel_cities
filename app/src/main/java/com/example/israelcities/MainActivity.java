package com.example.israelcities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

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
import com.google.maps.android.data.geojson.GeoJsonFeature;
import com.google.maps.android.data.geojson.GeoJsonLayer;
import com.google.maps.android.data.geojson.GeoJsonPolygonStyle;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final Set<String> YOSH_LOCALITIES = new HashSet<>(Arrays.asList(
            "אלפי מנשה",
            "אלקנה",
            "אפרת",
            "אריאל",
            "בית אל",
            "בית אריה עופרים",
            "ביתר עילית",
            "גבע בנימין",
            "גבעון החדשה",
            "גבעת זאב",
            "הר אדר",
            "כוכב השחר",
            "כוכב יעקב",
            "כפר אדומים",
            "כפר עציון",
            "מעלה אדומים",
            "מעלה מכמש",
            "מעלה שומרון",
            "מבוא דותן",
            "נווה דניאל",
            "נווה צוף",
            "נוקדים",
            "נילי",
            "נעלה",
            "עלי",
            "עלי זהב",
            "עמנואל",
            "עפרה",
            "עץ אפרים",
            "פסגות",
            "פדואל",
            "קדומים",
            "קריית ארבע",
            "קרני שומרון",
            "שילה",
            "שערי תקווה",
            "תקוע"
    ));

    private GoogleMap mMap;
    private GeoJsonLayer cityLayer;
    private final List<GeoJsonFeature> cityFeatures = new ArrayList<>();
    private final List<GeoJsonFeature> yoshFeatures = new ArrayList<>();
    private GeoJsonFeature currentQuestionFeature;
    private TextView questionText;
    private Button skipButton;
    private SwitchCompat yoshSwitch;
    private final Random random = new Random();
    private final ExecutorService mapLoaderExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        questionText = findViewById(R.id.question_text);
        skipButton = findViewById(R.id.skip_button);
        yoshSwitch = findViewById(R.id.yosh_switch);
        setControlsEnabled(false);
        questionText.setText(R.string.loading_locations);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        skipButton.setOnClickListener(v -> {
            pickNewRandomCity();
        });

        yoshSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> pickNewRandomCity());
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Center the map roughly on Israel; zoom level ~ 6–7 shows all cities
        LatLng israelCenter = new LatLng(31.5, 34.75);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(israelCenter, 6.7f));

        // Load the GeoJSON containing Israeli city boundaries, style, and set up click listeners
        loadGeoJsonLayerAsync();
    }

    private void loadGeoJsonLayerAsync() {
        mapLoaderExecutor.execute(() -> {
            try {
                JSONObject geoJsonObject = loadGeoJsonObject();
                runOnUiThread(() -> {
                    try {
                        initializeGeoJsonLayer(geoJsonObject);
                    } catch (JSONException e) {
                        showMapLoadError(e);
                    }
                });
            } catch (IOException | JSONException e) {
                runOnUiThread(() -> showMapLoadError(e));
            }
        });
    }

    private void initializeGeoJsonLayer(JSONObject geoJsonObject) throws JSONException {
        cityFeatures.clear();
        yoshFeatures.clear();

        cityLayer = new GeoJsonLayer(mMap, geoJsonObject);

        for (GeoJsonFeature feature : cityLayer.getFeatures()) {
            if (feature.getGeometry() == null) {
                continue;
            }

            String geomType = feature.getGeometry().getGeometryType();
            if ("Polygon".equals(geomType) || "MultiPolygon".equals(geomType)) {
                feature.setPolygonStyle(createDefaultPolygonStyle());

                if (shouldIncludeAsCity(feature)) {
                    cityFeatures.add(feature);
                } else if (isYoshFeature(feature)) {
                    yoshFeatures.add(feature);
                }
            }
        }

        cityLayer.addLayerToMap();
        cityLayer.setOnFeatureClickListener(this::handleCityClick);

        setControlsEnabled(true);
        pickNewRandomCity();
    }

    private JSONObject loadGeoJsonObject() throws IOException, JSONException {
        try (InputStream inputStream = getResources().openRawResource(R.raw.israel_cities);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            String json = outputStream.toString(StandardCharsets.UTF_8.name());
            return new JSONObject(json);
        }
    }

    private void showMapLoadError(Exception e) {
        e.printStackTrace();
        setControlsEnabled(false);
        questionText.setText(R.string.failed_to_load_locations);
        Toast.makeText(this, R.string.failed_to_load_geojson, Toast.LENGTH_LONG).show();
    }

    /** Randomly select one city feature as the “question” and update the UI. */
    private void pickNewRandomCity() {
        List<GeoJsonFeature> availableFeatures = getQuestionPool();
        if (availableFeatures.isEmpty()) {
            questionText.setText(getString(R.string.no_cities_available));
            return;
        }

        if (currentQuestionFeature != null) {
            resetFeatureStyle(currentQuestionFeature);
        }

        currentQuestionFeature = availableFeatures.get(random.nextInt(availableFeatures.size()));
        String cityName = resolveFeatureName(currentQuestionFeature);
        questionText.setText(getString(R.string.tap_on_city, cityName));
    }

    /** Called when the user taps on any polygon feature. */
    private void handleCityClick(Feature clickedFeature) {
        if (!(clickedFeature instanceof GeoJsonFeature) || currentQuestionFeature == null) {
            return;
        }

        GeoJsonFeature tapped = (GeoJsonFeature) clickedFeature;
        if (!getQuestionPool().contains(tapped)) {
            return;
        }

        String tappedName = resolveFeatureName(tapped);
        String targetName = resolveFeatureName(currentQuestionFeature);

        if (tappedName != null && tappedName.equals(targetName)) {
            highlightFeature(tapped, Color.argb(110, 76, 175, 80));
            Toast.makeText(this, getString(R.string.correct_answer, tappedName), Toast.LENGTH_SHORT).show();
            tapped.getPolygonStyle().setStrokeColor(Color.GREEN);
            tapped.getPolygonStyle().setStrokeWidth(5);
            pickNewRandomCity();
        } else {
            highlightFeature(tapped, Color.argb(110, 211, 47, 47));
            Toast.makeText(this, getString(R.string.wrong_answer, tappedName), Toast.LENGTH_SHORT).show();
            tapped.getPolygonStyle().setStrokeColor(Color.RED);
            tapped.getPolygonStyle().setStrokeWidth(5);
        }
    }

    /** Highlights a polygon’s fill color temporarily to indicate right/wrong. */
    private void highlightFeature(GeoJsonFeature feature, int fillColor) {
        GeoJsonPolygonStyle style = feature.getPolygonStyle();
        style.setFillColor(fillColor);
    }

    /** Resets a feature back to default style (transparent fill, thin gray stroke). */
    private void resetFeatureStyle(GeoJsonFeature feature) {
        feature.setPolygonStyle(createDefaultPolygonStyle());
    }

    private GeoJsonPolygonStyle createDefaultPolygonStyle() {
        GeoJsonPolygonStyle style = new GeoJsonPolygonStyle();
        style.setFillColor(Color.argb(40, 33, 150, 243));
        style.setStrokeColor(Color.DKGRAY);
        style.setStrokeWidth(2.5f);
        return style;
    }

    private List<GeoJsonFeature> getQuestionPool() {
        List<GeoJsonFeature> questionPool = new ArrayList<>(cityFeatures);
        if (yoshSwitch != null && yoshSwitch.isChecked()) {
            questionPool.addAll(yoshFeatures);
        }
        return questionPool;
    }

    private boolean shouldIncludeAsCity(GeoJsonFeature feature) {
        String type = feature.getProperty("type");
        if ("residential".equalsIgnoreCase(type)) {
            return !isYoshFeature(feature);
        }

        return feature.hasProperty("MUN_ENG") || feature.hasProperty("MUN_HEB");
    }

    private boolean isYoshFeature(GeoJsonFeature feature) {
        String type = feature.getProperty("type");
        if (!"residential".equalsIgnoreCase(type)) {
            return false;
        }

        String name = resolveFeatureName(feature);
        return name != null && YOSH_LOCALITIES.contains(name);
    }

    private String resolveFeatureName(GeoJsonFeature feature) {
        String cityName = feature.getProperty("MUN_ENG");
        if (cityName == null || cityName.trim().isEmpty()) {
            cityName = feature.getProperty("name");
        }
        if (cityName == null || cityName.trim().isEmpty()) {
            cityName = feature.getProperty("NAME_1");
        }
        if (cityName == null || cityName.trim().isEmpty()) {
            cityName = feature.getProperty("MUN_HEB");
        }
        return cityName == null ? getString(R.string.unknown_city) : cityName;
    }

    private void setControlsEnabled(boolean enabled) {
        skipButton.setEnabled(enabled);
        yoshSwitch.setEnabled(enabled);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapLoaderExecutor.shutdownNow();
    }
}
