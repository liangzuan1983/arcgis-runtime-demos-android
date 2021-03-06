/* Copyright 2016 Esri
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * A copy of the license is available in the repository's
 * https://github.com/Esri/arcgis-runtime-demos-android/blob/master/license.txt
 *
 * For information about licensing your deployed app, see
 * https://developers.arcgis.com/android/guide/license-your-app.htm
 *
 */

package com.esri.runtime.android.materialbasemaps.ui;

import java.util.concurrent.Callable;
import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.SpatialReference;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.view.LocationDisplay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.mapping.view.SpatialReferenceChangedEvent;
import com.esri.arcgisruntime.mapping.view.SpatialReferenceChangedListener;
import com.esri.arcgisruntime.portal.Portal;
import com.esri.arcgisruntime.portal.PortalItem;
import com.esri.runtime.android.materialbasemaps.R;
import com.esri.runtime.android.materialbasemaps.util.TaskExecutor;

/**
 * Activity for Map and floating action bar button.
 */
public class MapActivity extends Activity{

    private MapView mMapView;
    private LocationDisplay mLocationDisplay;
    // define permission to request
    private final String[] reqPermission = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};

    // location tracking
    private boolean mIsLocationTracking;

    private static final String KEY_IS_LOCATION_TRACKING = "IsLocationTracking";

    private RelativeLayout relativeMapLayout;
    private ImageButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.map_activity);
        // layout to add MapView to
        relativeMapLayout = (RelativeLayout) findViewById(R.id.relative);
        // receive portal id and title of the basemap to add to the map
        Intent intent = getIntent();
        String portalUrl = intent.getExtras().getString("portalUrl");
        String itemId = intent.getExtras().getString("portalId");
        String title = intent.getExtras().getString("title");

        ActionBar actionBar = getActionBar();
        assert actionBar != null;
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(title);
        // load the basemap on a background thread
        loadPortalItemIntoMapView(itemId, portalUrl);

        fab = (ImageButton) findViewById(R.id.fab);

        // floating action bar settings
        ViewOutlineProvider viewOutlineProvider = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int size = getResources().getDimensionPixelSize(R.dimen.fab_size);
                outline.setOval(0, 0, size, size);
            }
        };
        fab.setOutlineProvider(viewOutlineProvider);
    }

    public void onClick(View view){
        // Toggle location tracking on or off
        if (mIsLocationTracking) {
            mLocationDisplay.stop();
            mIsLocationTracking = false;
            fab.setImageResource(R.mipmap.ic_action_location_off);
        } else {
            startLocationTracking();
            fab.setImageResource(R.mipmap.ic_action_location_found);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // inflate action bar menu
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // navigate to parent activity
        NavUtils.navigateUpFromSameTask(this);
        // stop location tracking if enabled
        if(mIsLocationTracking){
            mLocationDisplay.stop();
            mIsLocationTracking = false;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(KEY_IS_LOCATION_TRACKING, mIsLocationTracking);
    }

    /**
     * Handle the permissions request response
     */
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mLocationDisplay.startAsync();
        } else {
            // report to user that permission was denied
            Toast.makeText(MapActivity.this,
                    getResources().getString(R.string.location_permission_denied),
                    Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * Creates a new WebMap on a background thread based on the portal item id of the basemap
     * to be used.  Goes back to UI thread to use the WebMap as a new MapView to display in
     * the ViewGroup layout.  Centers and zooms to Seattle area.
     *
     * @param portalItemId represents the basemap to be used as a new webmap
     * @param portalUrl represents the portal url to look up the portalItemId
     */
    private void loadPortalItemIntoMapView(final String portalItemId, final String portalUrl){
        TaskExecutor.getInstance().getThreadPool().submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {

                final Portal portal = new Portal(portalUrl);
                portal.loadAsync();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        portal.addDoneLoadingListener(new Runnable() {
                            @Override
                            public void run() {
                                if(portal.getLoadStatus() == LoadStatus.LOADED){
                                    // create a PortalItem from the Item ID
                                    PortalItem portalItem = new PortalItem(portal, portalItemId);
                                    // create an ArcGISMap from portal item
                                    final ArcGISMap portalMap = new ArcGISMap(portalItem);
                                    mMapView = new MapView(getApplicationContext());

                                    // ensure MapView is loaded to set initial viewpoint
                                    mMapView.addSpatialReferenceChangedListener(new SpatialReferenceChangedListener() {
                                        @Override
                                        public void spatialReferenceChanged(SpatialReferenceChangedEvent spatialReferenceChangedEvent) {
                                            // create point in web mercator
                                            Point initialPoint = new Point(-13617023.6399678998, 6040106.2917761272, SpatialReference.create(3857));
                                            // set initial viewpoint to ~ zoom level 11
                                            mMapView.setViewpointCenterAsync(initialPoint, 288895);
                                        }
                                    });

                                    mMapView.setMap(portalMap);
                                    // Layout Parameters for MapView
                                    ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT);

                                    mMapView.setLayoutParams(lp);
                                    // add MapView to layout
                                    relativeMapLayout.addView(mMapView);
                                }
                            }
                        });
                    }
                });
                return null;
            }
        });
    }

    /**
     * Starts tracking GPS location.
     */
    private void startLocationTracking() {
        mLocationDisplay = mMapView.getLocationDisplay();
        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.RECENTER);
        // For API level 23+ request fine location permission at runtime
        if (ContextCompat.checkSelfPermission(MapActivity.this, reqPermission[0]) == PackageManager.PERMISSION_GRANTED) {
            mLocationDisplay.startAsync();
        } else {
            // request permission
            int requestCode = 2;
            ActivityCompat.requestPermissions(MapActivity.this, reqPermission, requestCode);
        }
        mIsLocationTracking = true;
    }
}
