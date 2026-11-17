/*
 * Ported from Molly IM (https://github.com/mollyim/mollyim-android): an osmdroid-based stand-in for the
 * Google Maps SDK, so maps work without Google's API key or Play Services.
 * Licensed under the GNU General Public License v3.0 or later.
 */
package com.google.android.gms.maps.model;

public final class LatLng {
  public final double latitude;
  public final double longitude;
  public LatLng(double latitude, double longitude) {
    this.latitude  = latitude;
    this.longitude = longitude;
  }
}
