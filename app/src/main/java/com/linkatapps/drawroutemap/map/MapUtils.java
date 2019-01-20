package com.linkatapps.drawroutemap.map;

import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Loader;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.Dot;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PatternItem;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.SphericalUtil;
import com.google.maps.android.ui.IconGenerator;
import com.linkatapps.drawroutemap.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * MapUtils Class
 * <p>
 * Helper Class Contains MapUtils Methods
 * <p>
 * Version 1.0
 * <p>
 * Updated Version --
 */
public class MapUtils implements LoaderManager.LoaderCallbacks<ArrayList<Route>> {

    public static final int DASH_STYLE = 0;
    public static final int SOLID_STYLE = 1;
    private static final int ROUTES_LOADER_ID = 123987;
    private static final int GET_ROUTES_LOADER_ID = 123989;
    private static final int CALCULATE_SPEED = 11;
    private static Marker homeMarker = null;
    private String API_KEY;
    private Context context;
    private GoogleMap mMap;
    private Uri routUri;
    private List<Marker> originMarkers;
    private List<Marker> destinationMarkers;
    private List<Polyline> polylinePaths;
    private int routeLineWidth = 10;
    private int routeLineColor = Color.BLUE;
    private int routeLineStyle = SOLID_STYLE;
    private OnRoutesReadyCallback routeReadyListener;
    private OnSpeedCalculation speedCalculationListener;

    public MapUtils(Context context, GoogleMap mMap, String google_maps_key) {
        originMarkers = new ArrayList<>();
        destinationMarkers = new ArrayList<>();
        polylinePaths = new ArrayList<>();
        API_KEY = google_maps_key;
        this.context = context;
        this.mMap = mMap;
    }

    /**
     * decode polyLine String to give the points latlng values
     *
     * @param poly
     * @return
     */
    private static List<LatLng> decodePolyLine(final String poly) {
        int len = poly.length();
        int index = 0;
        List<LatLng> decoded = new ArrayList<LatLng>();
        try {
            int lat = 0;
            int lng = 0;

            while (index < len) {
                int b;
                int shift = 0;
                int result = 0;
                do {
                    b = poly.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                lat += dlat;

                shift = 0;
                result = 0;
                do {
                    b = poly.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                lng += dlng;

                decoded.add(new LatLng(
                        lat / 100000d, lng / 100000d
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return decoded;
    }

    /**
     * network task to get api response from url
     *
     * @param searchUrl
     * @return
     */
    static String getJsonFromUrl(String searchUrl) {

        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;
        String JsonString;

        try {
            URL url = new URL(searchUrl);
            Log.d("url", String.valueOf(url));
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setUseCaches(true);
            urlConnection.connect();
            InputStream inputStream = urlConnection.getInputStream();
            StringBuilder buffer = new StringBuilder();
            if (inputStream == null) {
                return null;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line).append("\n");
            }

            if (buffer.length() == 0) {
                // Stream was empty.  No point in parsing.
                return null;
            }
            JsonString = buffer.toString();
        } catch (IOException e) {
            return null;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e("Error", " closing stream", e);
                }
            }
        }

        return JsonString;
    }

    /**
     * draw circle on the map surrounding the given point
     *
     * @param center      center point
     * @param radios      circle radios
     * @param strokeWidth circle stroke width
     * @param strokeColor circle stroke color
     * @param fillColor   circle fill color
     * @return created circle
     */
    public Circle drawCircle(LatLng center, double radios, float strokeWidth,
                             int strokeColor, int fillColor) {
        Circle circle = null;
        if (mMap != null) {
            circle = mMap.addCircle(new CircleOptions()
                    .center(center)
                    .radius(radios)
                    .strokeWidth(strokeWidth)
                    .strokeColor(strokeColor)
                    .fillColor(fillColor));
//            circle.get
        }
        return circle;
    }

    /**
     * zoom to given location with given value
     *
     * @param latLng
     * @param zoomValue
     */
    public void zoomToLocation(LatLng latLng, float zoomValue, boolean animate) {
        if (mMap != null) {
            if (animate) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoomValue));
            } else
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoomValue));
        }
    }

    /**
     * zoom to given location with given value
     *
     * @param zoomValue
     */
    public void zoomToHomeLocation(float zoomValue, boolean animate) {
        if (mMap != null && homeMarker != null) {
            if (animate) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(homeMarker.getPosition(), zoomValue));
            } else
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(homeMarker.getPosition(), zoomValue));
        }
    }

    /**
     * add marker on the map with gaven location and title
     *
     * @param latLng      marker location
     * @param markerTitle marker title
     * @return created marker
     */
    public Marker addMarker(LatLng latLng, String markerTitle) {
        if (mMap != null) {
            return mMap.addMarker(new MarkerOptions().position(latLng).title(markerTitle));
        }
        return null;
    }

    public Marker addMarker(LatLng latLng, String markerTitle, boolean draggable) {
        if (mMap != null) {
            return mMap.addMarker(new MarkerOptions().position(latLng).title(markerTitle).draggable(draggable));
        }
        return null;
    }

    public Marker addMarker(LatLng latLng, String markerTitle, @DrawableRes int iconRes) {
        if (mMap != null) {
            return mMap.addMarker(new MarkerOptions().position(latLng).title(markerTitle)
                    .icon(BitmapDescriptorFactory.fromResource(iconRes)));
        }
        return null;
    }

    public Marker addMarker(LatLng latLng, String markerTitle, @DrawableRes int iconRes, boolean draggable) {
        if (mMap != null) {
            return mMap.addMarker(new MarkerOptions().position(latLng).title(markerTitle)
                    .icon(BitmapDescriptorFactory.fromResource(iconRes)).draggable(draggable));
        }
        return null;
    }

    public Marker addHomeMarker(LatLng latLng, String markerTitle) {
        if (mMap != null) {
            if (homeMarker != null) homeMarker.remove();
            homeMarker = mMap.addMarker(new MarkerOptions().position(latLng).title(markerTitle));
            return homeMarker;
        }
        return null;
    }

    public Marker addHomeMarker(LatLng latLng, String markerTitle, boolean draggable) {
        if (mMap != null) {
            if (homeMarker != null) homeMarker.remove();
            homeMarker = mMap.addMarker(new MarkerOptions().position(latLng).title(markerTitle).draggable(draggable));
            return homeMarker;
        }
        return null;
    }

    public Marker addHomeMarker(LatLng latLng, String markerTitle, @DrawableRes int iconRes) {
        if (mMap != null) {
            if (homeMarker != null) homeMarker.remove();
            homeMarker = mMap.addMarker(new MarkerOptions().position(latLng).title(markerTitle)
                    .icon(BitmapDescriptorFactory.fromResource(iconRes)));
            return homeMarker;
        }
        return null;
    }

    public Marker addHomeMarker(LatLng latLng, String markerTitle, @DrawableRes int iconRes, boolean draggable) {
        if (mMap != null) {
            if (homeMarker != null) homeMarker.remove();
            homeMarker = mMap.addMarker(new MarkerOptions().position(latLng).title(markerTitle)
                    .icon(BitmapDescriptorFactory.fromResource(iconRes)).draggable(draggable));
            return homeMarker;
        }
        return null;
    }

    public void drawLine(double startLat, double startLng, double endLat, double endLng
            , int lineColor, float lineWidth, int style) {

        LatLng start = new LatLng(startLat, startLng);
        LatLng end = new LatLng(endLat, endLng);

        PolylineOptions polylineOptions = new PolylineOptions().
                geodesic(true).
                color(lineColor).
                width(lineWidth);
        List<PatternItem> pattern = Arrays.asList(new Dot(), new Gap(10));

        polylineOptions.add(start).add(end);
        Polyline polyline = mMap.addPolyline(polylineOptions);
        switch (style) {
            case DASH_STYLE:
                polyline.setPattern(pattern);
                break;
            default:
        }
    }

    public String calculateDistanceBetweenTwoPoints(double startLat, double startLng, double endLat, double endLng) {
        LatLng start = new LatLng(startLat, startLng);
        LatLng end = new LatLng(endLat, endLng);


        double radios = SphericalUtil.computeDistanceBetween(start, end);
        return (radios) >= 1000 ? ((int) (radios) / 1000) + "K" : ((int) (radios) + "M");
    }

    public double getDistanceBetweenTwoPoints(double startLat, double startLng, double endLat, double endLng) {
        LatLng start = new LatLng(startLat, startLng);
        LatLng end = new LatLng(endLat, endLng);

        return SphericalUtil.computeDistanceBetween(start, end);
    }

    public void drawDistanceBetweenTwoPoints(double startLat, double startLng, double endLat, double endLng,
                                             boolean focus, float strokeWidth, int strokeColor, int fillColor) {

        LatLng start = new LatLng(startLat, startLng);
        LatLng end = new LatLng(endLat, endLng);

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(start).include(end);
        LatLngBounds latLngBounds = builder.build();
        LatLng center1 = latLngBounds.getCenter();

        double radios = SphericalUtil.computeDistanceBetween(start, end) / 2;
        String distance = (radios * 2) > 1000 ? ((int) (radios * 2) / 1000) + "K" : ((int) (radios * 2) + "M");

        IconGenerator iconFactory = new IconGenerator(context);
        iconFactory.setColor(Color.CYAN);
        iconFactory.setRotation(280);
        iconFactory.setContentRotation(90);
        MarkerOptions markerOptions = new MarkerOptions().
                icon(BitmapDescriptorFactory.fromBitmap(iconFactory.makeIcon(distance))).
                position(center1).
                anchor(iconFactory.getAnchorU(), iconFactory.getAnchorV());

        mMap.addMarker(markerOptions);
        addMarker(start, "startPoint");
        addMarker(end, "endPoint");
        drawLine(startLat, startLng, endLat, endLng, strokeColor, strokeWidth, MapUtils.SOLID_STYLE);

        if (focus) {
            drawCenterCircle(startLat, startLng, endLat, endLng, strokeWidth, strokeColor, fillColor);
        }
    }

    private void drawCenterCircle(double startLat, double startLng, double endLat, double endLng,
                                  float strokeWidth, int strokeColor, int fillColor) {

        LatLng start = new LatLng(startLat, startLng);
        LatLng end = new LatLng(endLat, endLng);

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(start).include(end);
        LatLngBounds latLngBounds = builder.build();
        LatLng center1 = latLngBounds.getCenter();

        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds, 0));


        double radios = SphericalUtil.computeDistanceBetween(start, end) / 2;


        addCircle(center1, radios, strokeWidth, strokeColor, fillColor);

    }

    private void addCircle(LatLng center, double radios, float strokeWidth,
                           int strokeColor, int fillColor) {
        mMap.addCircle(new CircleOptions()
                .center(center)
                .radius(radios)
                .strokeWidth(strokeWidth)
                .strokeColor(strokeColor)
                .fillColor(fillColor));
    }

    public void setMapType(int type) {
        if (mMap != null) {
            mMap.setMapType(type);
        }
    }

    /**
     * draw route from start location to destination location
     *
     * @param loaderManager
     * @param mode
     * @param distanceUnites
     * @param language
     */
    public void drawRoute(LoaderManager loaderManager,
                          double startLat, double startLng, double endLat, double endLng,
                          @RouteModes String mode,
                          @Dunites String distanceUnites,
                          String language, int lineWidth, int lineColor, int lineStyle) {

        this.routeLineWidth = lineWidth;
        this.routeLineColor = lineColor;
        this.routeLineStyle = lineStyle;

        LatLng startLocation = new LatLng(startLat, startLng);
        LatLng destinationLocation = new LatLng(endLat, endLng);

        routUri = Uri.parse(Constant.ROUTES_FROM_LOCATION).buildUpon()
                .appendQueryParameter(Constant.DIRECTION_STERT, String.valueOf(startLocation.latitude
                        + "," + startLocation.longitude))
                .appendQueryParameter(Constant.DIRECTION_DISTENATION_END, String.valueOf(destinationLocation.latitude
                        + "," + destinationLocation.longitude))
                .appendQueryParameter(Constant.MODE_PARAMS, mode)
                .appendQueryParameter(Constant.UNITS_PARAMS, distanceUnites)
                .appendQueryParameter(Constant.LANGUAGE_PARAM, language)
                .appendQueryParameter(Constant.API_KEY_PARAM, API_KEY)
                .build();

        loaderManager.restartLoader(ROUTES_LOADER_ID, null, this).forceLoad();

    }

    public void drawRoute(LoaderManager loaderManager,
                          double startLat, double startLng, double endLat, double endLng,
                          @RouteModes String mode,
                          @Dunites String distanceUnites,
                          String language, int lineWidth, int lineColor, int lineStyle,
                          OnRoutesReadyCallback onRoutesReadyCallback) {
        this.routeReadyListener = onRoutesReadyCallback;

        this.routeLineWidth = lineWidth;
        this.routeLineColor = lineColor;
        this.routeLineStyle = lineStyle;

        LatLng startLocation = new LatLng(startLat, startLng);
        LatLng destinationLocation = new LatLng(endLat, endLng);

        routUri = Uri.parse(Constant.ROUTES_FROM_LOCATION).buildUpon()
                .appendQueryParameter(Constant.DIRECTION_STERT, String.valueOf(startLocation.latitude
                        + "," + startLocation.longitude))
                .appendQueryParameter(Constant.DIRECTION_DISTENATION_END, String.valueOf(destinationLocation.latitude
                        + "," + destinationLocation.longitude))
                .appendQueryParameter(Constant.MODE_PARAMS, mode)
                .appendQueryParameter(Constant.UNITS_PARAMS, distanceUnites)
                .appendQueryParameter(Constant.LANGUAGE_PARAM, language)
                .appendQueryParameter(Constant.API_KEY_PARAM, API_KEY)
                .build();

        loaderManager.restartLoader(ROUTES_LOADER_ID, null, this).forceLoad();

    }

    public void getRoute(LoaderManager loaderManager,
                         double startLat, double startLng, double endLat, double endLng,
                         @RouteModes String mode,
                         @Dunites String distanceUnites,
                         String language, OnRoutesReadyCallback onRoutesReadyCallback) {

        this.routeReadyListener = onRoutesReadyCallback;

        LatLng startLocation = new LatLng(startLat, startLng);
        LatLng destinationLocation = new LatLng(endLat, endLng);

        routUri = Uri.parse(Constant.ROUTES_FROM_LOCATION).buildUpon()
                .appendQueryParameter(Constant.DIRECTION_STERT, String.valueOf(startLocation.latitude
                        + "," + startLocation.longitude))
                .appendQueryParameter(Constant.DIRECTION_DISTENATION_END, String.valueOf(destinationLocation.latitude
                        + "," + destinationLocation.longitude))
                .appendQueryParameter(Constant.MODE_PARAMS, mode)
                .appendQueryParameter(Constant.UNITS_PARAMS, distanceUnites)
                .appendQueryParameter(Constant.LANGUAGE_PARAM, language)
                .appendQueryParameter(Constant.API_KEY_PARAM, API_KEY)
                .build();

        loaderManager.restartLoader(GET_ROUTES_LOADER_ID, null, this).forceLoad();

    }

    public void getSpeedBetweenTwoLocations(LoaderManager loaderManager,
                                            double startLat, double startLng, double endLat, double endLng,
                                            @RouteModes String mode,
                                            @Dunites String distanceUnites,
                                            String language, OnSpeedCalculation speedCalculationListener) {
        this.speedCalculationListener = speedCalculationListener;

        LatLng startLocation = new LatLng(startLat, startLng);
        LatLng destinationLocation = new LatLng(endLat, endLng);

        routUri = Uri.parse(Constant.ROUTES_FROM_LOCATION).buildUpon()
                .appendQueryParameter(Constant.DIRECTION_STERT, String.valueOf(startLocation.latitude
                        + "," + startLocation.longitude))
                .appendQueryParameter(Constant.DIRECTION_DISTENATION_END, String.valueOf(destinationLocation.latitude
                        + "," + destinationLocation.longitude))
                .appendQueryParameter(Constant.MODE_PARAMS, mode)
                .appendQueryParameter(Constant.UNITS_PARAMS, distanceUnites)
                .appendQueryParameter(Constant.LANGUAGE_PARAM, language)
                .appendQueryParameter(Constant.API_KEY_PARAM, API_KEY)
                .build();

        loaderManager.restartLoader(CALCULATE_SPEED, null, this).forceLoad();

    }

    @Override
    public Loader<ArrayList<Route>> onCreateLoader(int id, Bundle args) {
        return new doInBackgroundTask(context, routUri.toString());
    }

    @Override
    public void onLoadFinished(Loader<ArrayList<Route>> loader, ArrayList<Route> routes) {

        switch (loader.getId()) {
            case GET_ROUTES_LOADER_ID:
                if (routeReadyListener != null) routeReadyListener.onRoutesReady(routes);
                break;
            case ROUTES_LOADER_ID:
                drawRoutes(routes);
                if (routeReadyListener != null) routeReadyListener.onRoutesReady(routes);
                break;
            case CALCULATE_SPEED:
                calculateSpeed(routes);
                break;
        }


    }

    private void calculateSpeed(ArrayList<Route> routes) {
        if (routes == null || routes.size() <= 0) {
            if (speedCalculationListener != null)
                speedCalculationListener.OnSpeedCalculated(0.0);
            return;
        }

        Route route = routes.get(0);
        double time = route.duration.value;
        double distance = route.distance.value;
        if (time <= 0 || distance <= 0) {
            if (speedCalculationListener != null)
                speedCalculationListener.OnSpeedCalculated(0.0);
            return;
        }
        if (speedCalculationListener != null)
            speedCalculationListener.OnSpeedCalculated(distance / time);
    }

    private void drawRoutes(ArrayList<Route> routes) {
        removeOldLines();

        if (routes != null && mMap != null) {
            for (Route route : routes) {

                PolylineOptions polylineOptions = new PolylineOptions().
                        geodesic(true).
                        color(routeLineColor).
                        width(routeLineWidth);

                polylinePaths = new ArrayList<>();
                originMarkers = new ArrayList<>();
                destinationMarkers = new ArrayList<>();

                polylineOptions.add(route.startLocation);

                for (int i = 0; i < route.points.size(); i++) {
                    polylineOptions.add(route.points.get(i));
                }

                polylineOptions.add(route.endLocation);
                List<PatternItem> pattern = Arrays.asList(new Dot(), new Gap(10));

                Polyline polyline = mMap.addPolyline(polylineOptions);
                if (routeLineStyle == DASH_STYLE) polyline.setPattern(pattern);
                polylinePaths.add(polyline);
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<ArrayList<Route>> loader) {

    }

    /**
     * remove old lines from map
     */
    private void removeOldLines() {
        if (originMarkers != null) {
            for (Marker marker : originMarkers) {
                marker.remove();
            }
            originMarkers = new ArrayList<>();
        }

        if (destinationMarkers != null) {
            for (Marker marker : destinationMarkers) {
                marker.remove();
            }
            destinationMarkers = new ArrayList<>();
        }

        if (polylinePaths != null) {
            for (Polyline polyline : polylinePaths) {
                polyline.remove();
            }
            polylinePaths = new ArrayList<>();
        }
    }

    /**
     * remove given marker
     *
     * @param marker
     */
    public void removeMarker(Marker marker) {
        if (marker != null) marker.remove();
    }

    public void removeListOfMarkers(ArrayList<Marker> markers) {
        if (markers != null && markers.size() > 0) {
            for (Marker marker : markers)
                if (marker != null) marker.remove();
        }
    }

    /**
     * clear map
     */
    public void clearMap() {
        if (mMap != null) mMap.clear();
    }

    public boolean setMapStyle(int style) {
        return mMap != null && mMap.setMapStyle(new MapStyleOptions(context.getResources().getString(style)));
    }

    public void zoomInCircle(LatLng center, double radios) {
        if (mMap != null) {
            LatLngBounds latLngBounds = getCircleBounds(center, radios);
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds, 0));
        }

    }

    private LatLngBounds getCircleBounds(LatLng center, double radiusInMeters) {
        double distanceFromCenterToCorner = radiusInMeters * Math.sqrt(2.0);
        LatLng southwestCorner =
                SphericalUtil.computeOffset(center, distanceFromCenterToCorner, 225.0);
        LatLng northeastCorner =
                SphericalUtil.computeOffset(center, distanceFromCenterToCorner, 45.0);
        return new LatLngBounds(southwestCorner, northeastCorner);
    }

    public void removeCircle(Circle circle) {
        if (circle != null) {
            circle.remove();
        }
    }

    public void change3DMode(boolean is3D) {
        if (mMap == null) return;
        CameraPosition camPos = CameraPosition
                .builder()
                .target(mMap.getCameraPosition().target)
                .bearing(mMap.getCameraPosition().bearing)
                .zoom(mMap.getCameraPosition().zoom)
                .tilt(is3D ? 90 : 0)
                .build();
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(camPos));
    }

    public interface OnSpeedCalculation {
        void OnSpeedCalculated(double speedInMeterBySec);
    }


    public interface OnRoutesReadyCallback {
        void onRoutesReady(ArrayList<Route> route);
    }

    public static class doInBackgroundTask extends AsyncTaskLoader<ArrayList<Route>> {

        private String uri;

        doInBackgroundTask(Context mContext, String uri) {
            super(mContext);
            this.uri = uri;
        }

        @Override
        public ArrayList<Route> loadInBackground() {
            ArrayList<Route> routes = null;
            String jsonResult = getJsonFromUrl(uri);
            if (jsonResult != null)
                try {
                    routes = routesJsonParser(jsonResult);
                } catch (Exception e) {
                    return null;
                }
            return routes;
        }

        private ArrayList<Route> routesJsonParser(String jsonResult) throws Exception {
            String status;
            ArrayList<Route> routes;

            String startPlaceID = "";
            String destinPlaceID = "";

            Distance distance = null;
            Duration duration = null;

            String end_address = "", start_address = "";
            LatLng start_location = null, end_location = null;

            List<LatLng> routsPoints = new ArrayList<>();

            JSONObject response = new JSONObject(jsonResult);
            routes = new ArrayList<>();
            if (response.has("status")) {
                status = response.getString("status");
                if (status.equals("OK")) {

                    if (response.has("geocoded_waypoints")) {
                        JSONArray geocoded_waypoints = response.getJSONArray("geocoded_waypoints");
                        if (geocoded_waypoints != null && geocoded_waypoints.length() == 2) {

                            JSONObject start = geocoded_waypoints.getJSONObject(0);
                            if (start.has("geocoder_status")) {
                                if (start.getString("geocoder_status").equals("OK")) {
                                    if (start.has("place_id"))
                                        startPlaceID = start.getString("place_id");
                                }
                            }
                            JSONObject destination = geocoded_waypoints.getJSONObject(1);
                            if (destination.has("geocoder_status")) {
                                if (destination.getString("geocoder_status").equals("OK")) {
                                    if (destination.has("place_id"))
                                        destinPlaceID = destination.getString("place_id");
                                }
                            }


                        }
                    }

                    if (response.has("routes")) {
                        JSONArray routesArray = response.getJSONArray("routes");
                        for (int i = 0; i < routesArray.length(); i++) {
                            JSONObject jsonRoute = routesArray.getJSONObject(i);

                            if (jsonRoute.has("legs")) {
                                JSONArray jsonLegsArray = jsonRoute.getJSONArray("legs");
                                for (int x = 0; x < jsonLegsArray.length(); x++) {
                                    JSONObject legObj = jsonLegsArray.getJSONObject(x);
                                    if (legObj.has("distance")) {
                                        JSONObject distanceObj = legObj.getJSONObject("distance");
                                        distance = new Distance(distanceObj.getString("text")
                                                , distanceObj.getDouble("value"));
                                    }


                                    if (legObj.has("duration")) {
                                        JSONObject durationObj = legObj.getJSONObject("duration");
                                        duration = new Duration(durationObj.getString("text")
                                                , durationObj.getDouble("value"));
                                    }


                                    if (legObj.has("end_address")) {
                                        end_address = legObj.getString("end_address");
                                    }

                                    if (legObj.has("end_location")) {
                                        JSONObject end = legObj.getJSONObject("end_location");
                                        end_location = new LatLng(end.getDouble("lat"),
                                                end.getDouble("lng"));
                                    }


                                    if (legObj.has("start_address")) {
                                        start_address = legObj.getString("start_address");
                                    }


                                    if (legObj.has("start_location")) {
                                        JSONObject start = legObj.getJSONObject("start_location");
                                        start_location = new LatLng(start.getDouble("lat"),
                                                start.getDouble("lng"));
                                    }


                                }
                            }

                            if (jsonRoute.has("overview_polyline")) {
                                JSONObject plyLineObj = jsonRoute.getJSONObject("overview_polyline");
                                if (plyLineObj.has("points")) {
                                    routsPoints = decodePolyLine(plyLineObj.getString("points"));
                                }
                            }

                            Route route = new Route();
                            route.destinationPlace_id = destinPlaceID;
                            route.startPlace_id = startPlaceID;
                            route.distance = distance;
                            route.duration = duration;
                            route.startAddress = start_address;
                            route.endAddress = end_address;
                            route.startLocation = start_location;
                            route.endLocation = end_location;
                            route.points = routsPoints;
                            routes.add(route);
                        }

                    }


                }
            }
            return routes;
        }
    }


}
