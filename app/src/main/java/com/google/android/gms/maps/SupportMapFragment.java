/*
 * Ported from Molly IM (https://github.com/mollyim/mollyim-android): an osmdroid-based stand-in for the
 * Google Maps SDK, so maps work without Google's API key or Play Services.
 * Licensed under the GNU General Public License v3.0 or later.
 */
package com.google.android.gms.maps;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SupportMapFragment extends Fragment {
  private MapView mapView;

  public void getMapAsync(OnMapReadyCallback callback) {
    if (mapView == null) return;
    mapView.getMapAsync(callback);
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState)
  {
    mapView = new MapView(inflater.getContext());
    mapView.onCreate(savedInstanceState);
    return mapView;
  }

  @Override
  public void onStart() {
    super.onStart();
    mapView.onStart();
  }

  @Override
  public void onResume() {
    super.onResume();
    mapView.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    mapView.onPause();
  }

  @Override
  public void onStop() {
    super.onStop();
    mapView.onStop();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    mapView.onDestroy();
    mapView = null;
  }
}
