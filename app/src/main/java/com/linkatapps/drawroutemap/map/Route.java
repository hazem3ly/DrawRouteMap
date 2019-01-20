package com.linkatapps.drawroutemap.map;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

/**
 * Created by Hazem Ali.
 * On 2/5/2018.
 */

public class Route {
    public Distance distance;
    public Duration duration;
    public String endAddress;
    public LatLng endLocation;
    public String startAddress;
    public String startPlace_id;
    public String destinationPlace_id;
    public LatLng startLocation;
    public List<LatLng> points;

}
