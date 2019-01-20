package com.linkatapps.drawroutemap.map;

import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Loader;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Hazem Ali.
 * On 2/8/2018.
 */

public class RouteDetail implements LoaderManager.LoaderCallbacks<ArrayList<LocationData>> {

    public static final String FILTER_BY_STREET_NUMBER = "street_number";
    public static final String FILTER_BY_ROUTE = "route";
    public static final String FILTER_BY_ADMINISTRATIVE_AREA_LEVEL_3 = "administrative_area_level_3";
    public static final String FILTER_BY_ADMINISTRATIVE_AREA_LEVEL_2 = "administrative_area_level_2";
    public static final String FILTER_BY_ADMINISTRATIVE_AREA_LEVEL_1 = "administrative_area_level_1";
    public static final String FILTER_BY_COUNTRY = "country";
    private static final int GET_ROUTES_DETAILS_LOADER_ID = 12312;
    private Uri routUri;
    private String API_KEY;
    private Context context;
    private String filter = FILTER_BY_ROUTE;
    private String language = Constant.ENGLISH_KEY;
    private OnRoutesDetailsReady mlistener;

    public RouteDetail(Context context, String google_api_key) {
        this.context = context;
        this.API_KEY = google_api_key;
    }

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

    public void getRouteDetailsBetweenTwoPoints(LoaderManager loaderManager, double startLat, double startLng,
                                                double endLat, double endLng,
                                                String filterBy,
                                                @RouteModes String mode,
                                                @Dunites String distanceUnites,
                                                String language, OnRoutesDetailsReady listener) {

        this.filter = filterBy;
        this.language = language;
        mlistener = listener;

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

        loaderManager.restartLoader(GET_ROUTES_DETAILS_LOADER_ID, null, this).forceLoad();

    }

    @Override
    public Loader<ArrayList<LocationData>> onCreateLoader(int id, Bundle args) {
        return new doInBackgroundTask(context, routUri.toString(), language, filter);
    }

    @Override
    public void onLoadFinished(Loader<ArrayList<LocationData>> loader, ArrayList<LocationData> data) {
        Log.d("", data.size() + "");
        if (mlistener != null) mlistener.onRouteReady(data);
    }

    @Override
    public void onLoaderReset(Loader<ArrayList<LocationData>> loader) {

    }

    public interface OnRoutesDetailsReady {
        void onRouteReady(ArrayList<LocationData> arrayList);
    }

    public static class doInBackgroundTask extends AsyncTaskLoader<ArrayList<LocationData>> {

        private String uri;
        private String language;
        private String filter;
        private String lastLocation = "";

        doInBackgroundTask(Context mContext, String uri, String language, String filter) {
            super(mContext);
            this.uri = uri;
            this.language = language;
            this.filter = filter;
        }

        @Override
        public ArrayList<LocationData> loadInBackground() {
            ArrayList<Route> routes = null;
            ArrayList<LocationData> locationData = null;
            String jsonResult = getJsonFromUrl(uri);
            if (jsonResult != null)
                try {
                    routes = routesJsonParser(jsonResult);
                    locationData = getLocationData(routes);
                } catch (Exception e) {
                    return null;
                }
            return locationData;
        }

        private ArrayList<LocationData> getLocationData(ArrayList<Route> routes) {

            ArrayList<LocationData> locationData = new ArrayList<>();
            if (routes != null && routes.size() > 0) {
                Route route = routes.get(0);
                if (route != null && route.points.size() > 0) {
                    for (LatLng latLng : route.points) {
                        try {
                            LocationData locationData1 = getLatLngData(latLng, language, filter);
                            if (locationData1 != null)
                                locationData.add(locationData1);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            return locationData;
        }

        private LocationData getLatLngData(LatLng latLng, String language, String filter) throws JSONException {
            LocationData locationData = new LocationData();
            locationData.latitude = latLng.latitude;
            locationData.longitude = latLng.longitude;

            JSONObject jsonObject =
                    getLocationInfo(language, locationData.latitude, locationData.longitude);
            if (jsonObject != null) {
                JSONArray results = (JSONArray) jsonObject.get("results");
                // loop among all addresses within this result
                for (int i = 0; i < results.length(); i++) {
                    JSONObject result = results.getJSONObject(i);
                    if (result.has("types")) {
                        JSONArray types = result.getJSONArray("types");
                        String t = types.getString(0);
                        if (t.equals(filter)) {
                            if (result.has("formatted_address")) {
                                if (lastLocation.equals(result.getString("formatted_address")))
                                    return null;
                                lastLocation = result.getString("formatted_address");
                                locationData.fullAddress = result.getString("formatted_address");
                                return locationData;
                            }
                        }
                    }
                }
            }

            return null;
        }

        public JSONObject getLocationInfo(String language, double lat, double lng) {

            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;
            String JsonString = null;
            try {

                URL url = new URL("https://maps.googleapis.com/maps/api/geocode/json?latlng="
                        + lat + "," + lng + "&language=" + language + "&sensor=true&key=AIzaSyDMXoxGSVmVtqFijzHD1teUJyaJ8L61aXA");
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setUseCaches(true);
                urlConnection.connect();
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                JsonString = buffer.toString();
            } catch (IOException e) {
                Log.e("Network Connection ", "Error ", e);
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
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject = new JSONObject(JsonString);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return jsonObject;
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
