package com.mandala.net.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.geojson.Point
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mandala.net.CyberTheme
import com.mandala.net.service.MockLocationManager
import com.mandala.net.service.MockLocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL
import java.net.URLEncoder

// MapLibre native map controller
class MapLibreController {
    var maplibreMap: org.maplibre.android.maps.MapLibreMap? = null
    var style: org.maplibre.android.maps.Style? = null
    var context: android.content.Context? = null

    var activePinLatLng: org.maplibre.android.geometry.LatLng? = null
    var waypointPoints = mutableListOf<org.maplibre.android.geometry.LatLng>()
    var plannedRoutePoints = mutableListOf<org.maplibre.android.geometry.LatLng>()
    
    class TrailSegment(
        val start: org.maplibre.android.geometry.LatLng,
        val end: org.maplibre.android.geometry.LatLng,
        val color: String
    )
    val trailSegments = mutableListOf<TrailSegment>()
    
    fun init(map: org.maplibre.android.maps.MapLibreMap, ctx: android.content.Context, initialStyle: String) {
        this.maplibreMap = map
        this.context = ctx
        val currentStyle = map.style
        if (currentStyle != null && com.mandala.net.service.MockLocationManager.currentStyleName == initialStyle) {
            this.style = currentStyle
            setupSourcesAndLayers()
            updateActivePin()
            updateWaypoints()
            updatePlannedRouteLine()
            updateTrail()
        } else {
            setStyle(initialStyle)
        }
    }
    
    fun setStyle(styleName: String) {
        val map = maplibreMap ?: return
        if (map.style != null && com.mandala.net.service.MockLocationManager.currentStyleName == styleName) {
            this.style = map.style
            setupSourcesAndLayers()
            updateActivePin()
            updateWaypoints()
            updatePlannedRouteLine()
            updateTrail()
            return
        }
        val styleJson = getMaplibreStyleJson(styleName)
        map.setStyle(org.maplibre.android.maps.Style.Builder().fromJson(styleJson)) { loadedStyle ->
            this.style = loadedStyle
            com.mandala.net.service.MockLocationManager.currentStyleName = styleName
            setupSourcesAndLayers()
            updateActivePin()
            updateWaypoints()
            updatePlannedRouteLine()
            updateTrail()
        }
    }
    
    private fun setupSourcesAndLayers() {
        val currentStyle = style ?: return
        val ctx = context ?: return
        
        // 1. Waypoint Line Source & Layer (Bottom line - dimmed planned route)
        if (currentStyle.getSource("waypoint-path-source") == null) {
            val emptyLine = org.maplibre.geojson.LineString.fromLngLats(emptyList())
            currentStyle.addSource(org.maplibre.android.style.sources.GeoJsonSource("waypoint-path-source", org.maplibre.geojson.Feature.fromGeometry(emptyLine)))
            
            val lineLayer = org.maplibre.android.style.layers.LineLayer("waypoint-path-layer", "waypoint-path-source").apply {
                setProperties(
                    org.maplibre.android.style.layers.PropertyFactory.lineColor(android.graphics.Color.parseColor("#4000F0FF")),
                    org.maplibre.android.style.layers.PropertyFactory.lineWidth(6f),
                    org.maplibre.android.style.layers.PropertyFactory.lineJoin(org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND),
                    org.maplibre.android.style.layers.PropertyFactory.lineCap(org.maplibre.android.style.layers.Property.LINE_CAP_ROUND)
                )
            }
            currentStyle.addLayer(lineLayer)
        }
        
        // 2. Trail Line Source & Layer (Traversed colored line)
        if (currentStyle.getSource("trail-source") == null) {
            currentStyle.addSource(org.maplibre.android.style.sources.GeoJsonSource("trail-source", org.maplibre.geojson.FeatureCollection.fromFeatures(emptyList())))
            
            val trailLayer = org.maplibre.android.style.layers.LineLayer("trail-layer", "trail-source").apply {
                setProperties(
                    org.maplibre.android.style.layers.PropertyFactory.lineColor(org.maplibre.android.style.expressions.Expression.get("color")),
                    org.maplibre.android.style.layers.PropertyFactory.lineWidth(6f),
                    org.maplibre.android.style.layers.PropertyFactory.lineJoin(org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND),
                    org.maplibre.android.style.layers.PropertyFactory.lineCap(org.maplibre.android.style.layers.Property.LINE_CAP_ROUND)
                )
            }
            currentStyle.addLayer(trailLayer)
        }

        // 3. Waypoints Symbol Source & Layer (Waypoint markers)
        if (currentStyle.getSource("waypoints-source") == null) {
            currentStyle.addSource(org.maplibre.android.style.sources.GeoJsonSource("waypoints-source", org.maplibre.geojson.FeatureCollection.fromFeatures(emptyList())))
            
            val wpBitmap = getWaypointPinBitmap(ctx)
            currentStyle.addImage("waypoint-pin-image", wpBitmap)
            
            val wpLayer = org.maplibre.android.style.layers.SymbolLayer("waypoints-layer", "waypoints-source").apply {
                setProperties(
                    org.maplibre.android.style.layers.PropertyFactory.iconImage("waypoint-pin-image"),
                    org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                    org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement(true),
                    org.maplibre.android.style.layers.PropertyFactory.iconAnchor(org.maplibre.android.style.layers.Property.ICON_ANCHOR_BOTTOM)
                )
            }
            currentStyle.addLayer(wpLayer)
        }
        
        // 4. Active Pin Source & Layer (Current simulation position marker - Top)
        if (currentStyle.getSource("active-pin-source") == null) {
            val startPos = activePinLatLng ?: org.maplibre.android.geometry.LatLng(-6.2088, 106.8456)
            val point = org.maplibre.geojson.Point.fromLngLat(startPos.longitude, startPos.latitude)
            currentStyle.addSource(org.maplibre.android.style.sources.GeoJsonSource("active-pin-source", org.maplibre.geojson.Feature.fromGeometry(point)))
            
            val activeBitmap = getActivePinBitmap(ctx)
            currentStyle.addImage("active-pin-image", activeBitmap)
            
            val activeLayer = org.maplibre.android.style.layers.SymbolLayer("active-pin-layer", "active-pin-source").apply {
                setProperties(
                    org.maplibre.android.style.layers.PropertyFactory.iconImage("active-pin-image"),
                    org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                    org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement(true),
                    org.maplibre.android.style.layers.PropertyFactory.iconAnchor(org.maplibre.android.style.layers.Property.ICON_ANCHOR_BOTTOM)
                )
            }
            currentStyle.addLayer(activeLayer)
        }
    }
    
    fun updateActivePin(lat: Double, lng: Double) {
        activePinLatLng = org.maplibre.android.geometry.LatLng(lat, lng)
        updateActivePin()
    }
    
    private fun updateActivePin() {
        val currentStyle = style ?: return
        val pos = activePinLatLng ?: return
        val source = currentStyle.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>("active-pin-source")
        val point = org.maplibre.geojson.Point.fromLngLat(pos.longitude, pos.latitude)
        source?.setGeoJson(org.maplibre.geojson.Feature.fromGeometry(point))
    }
    
    fun drawWaypoints(waypoints: List<com.mandala.net.ui.LocationBookmark>) {
        waypointPoints = waypoints.map { org.maplibre.android.geometry.LatLng(it.lat, it.lng) }.toMutableList()
        // Reset OSRM route points so we revert to straight lines until updated
        plannedRoutePoints.clear()
        updateWaypoints()
        updatePlannedRouteLine()
    }
    
    fun drawPlannedRoute(points: List<Pair<Double, Double>>) {
        plannedRoutePoints = points.map { org.maplibre.android.geometry.LatLng(it.first, it.second) }.toMutableList()
        updatePlannedRouteLine()
    }
    
    private fun updateWaypoints() {
        val currentStyle = style ?: return
        
        // Update markers source
        val wpSource = currentStyle.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>("waypoints-source")
        val features = waypointPoints.map {
            org.maplibre.geojson.Feature.fromGeometry(org.maplibre.geojson.Point.fromLngLat(it.longitude, it.latitude))
        }
        wpSource?.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(features))
    }
    
    private fun updatePlannedRouteLine() {
        val currentStyle = style ?: return
        val lineSource = currentStyle.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>("waypoint-path-source")
        val pointsToUse = if (plannedRoutePoints.isNotEmpty()) plannedRoutePoints else waypointPoints
        
        if (pointsToUse.size >= 2) {
            val pts = pointsToUse.map { org.maplibre.geojson.Point.fromLngLat(it.longitude, it.latitude) }
            val line = org.maplibre.geojson.LineString.fromLngLats(pts)
            lineSource?.setGeoJson(org.maplibre.geojson.Feature.fromGeometry(line))
        } else {
            val emptyLine = org.maplibre.geojson.LineString.fromLngLats(emptyList())
            lineSource?.setGeoJson(org.maplibre.geojson.Feature.fromGeometry(emptyLine))
        }
    }
    
    fun clearTrail() {
        trailSegments.clear()
        updateTrail()
    }
    
    fun addTrailPoint(lat: Double, lng: Double, color: String) {
        val newPt = org.maplibre.android.geometry.LatLng(lat, lng)
        if (trailSegments.isNotEmpty()) {
            val lastSegment = trailSegments.last()
            val segment = TrailSegment(lastSegment.end, newPt, color)
            trailSegments.add(segment)
        } else {
            val startPt = activePinLatLng ?: newPt
            val segment = TrailSegment(startPt, newPt, color)
            trailSegments.add(segment)
        }
        updateTrail()
    }
    
    private fun updateTrail() {
        val currentStyle = style ?: return
        val source = currentStyle.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>("trail-source")
        
        val features = trailSegments.map { segment ->
            val pts = listOf(
                org.maplibre.geojson.Point.fromLngLat(segment.start.longitude, segment.start.latitude),
                org.maplibre.geojson.Point.fromLngLat(segment.end.longitude, segment.end.latitude)
            )
            val line = org.maplibre.geojson.LineString.fromLngLats(pts)
            val feature = org.maplibre.geojson.Feature.fromGeometry(line)
            feature.addStringProperty("color", segment.color)
            feature
        }
        source?.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(features))
    }
    
    fun animateCameraTo(lat: Double, lng: Double, zoom: Double = -1.0) {
        val map = maplibreMap ?: return
        val targetZoom = if (zoom > 0) zoom else map.cameraPosition.zoom
        val cameraUpdate = org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
            org.maplibre.android.geometry.LatLng(lat, lng),
            targetZoom.coerceAtLeast(12.0)
        )
        map.animateCamera(cameraUpdate, 1200)
    }
}

@androidx.compose.runtime.Composable
fun rememberMapViewWithLifecycle(): org.maplibre.android.maps.MapView {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Use applicationContext for MapView to avoid Activity lifecycle coupling
    val appContext = context.applicationContext
    
    val mapView = androidx.compose.runtime.remember {
        val cached = MockLocationManager.cachedMapView as? org.maplibre.android.maps.MapView
        if (cached != null) {
            // Detach from old parent if it was previously attached to another ViewGroup
            try {
                (cached.parent as? android.view.ViewGroup)?.removeView(cached)
            } catch (_: Exception) {}
            cached
        } else {
            org.maplibre.android.maps.MapView(appContext).also {
                MockLocationManager.cachedMapView = it
                it.onCreate(android.os.Bundle())
            }
        }
    }
    
    val lifecycleObserver = androidx.compose.runtime.remember(mapView) {
        androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> mapView.onStart()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapView.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
    }
    
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle, mapView) {
        lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
        }
    }
    
    return mapView
}

fun getActivePinBitmap(context: android.content.Context): android.graphics.Bitmap {
    val width = 72
    val height = 92
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    
    paint.color = android.graphics.Color.parseColor("#400096FF")
    canvas.drawCircle(width / 2f, height - 12f, 12f, paint)
    
    val path = android.graphics.Path()
    val centerX = width / 2f
    val centerY = height / 2.5f
    val radius = 22f
    
    path.moveTo(centerX, height - 12f)
    path.cubicTo(centerX - radius * 1.5f, centerY + radius, centerX - radius, centerY - radius, centerX, centerY - radius)
    path.cubicTo(centerX + radius, centerY - radius, centerX + radius * 1.5f, centerY + radius, centerX, height - 12f)
    path.close()
    
    val gradient = android.graphics.LinearGradient(
        centerX, centerY - radius, centerX, height - 12f,
        android.graphics.Color.parseColor("#00d2ff"),
        android.graphics.Color.parseColor("#0066ff"),
        android.graphics.Shader.TileMode.CLAMP
    )
    paint.shader = gradient
    canvas.drawPath(path, paint)
    
    paint.shader = null
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 3f
    paint.color = android.graphics.Color.WHITE
    canvas.drawPath(path, paint)
    
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(centerX, centerY, 8f, paint)
    
    return bitmap
}

fun getWaypointPinBitmap(context: android.content.Context): android.graphics.Bitmap {
    val width = 48
    val height = 64
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    
    paint.color = android.graphics.Color.WHITE
    paint.strokeWidth = 4f
    paint.strokeCap = android.graphics.Paint.Cap.ROUND
    canvas.drawLine(width / 2f, height / 2f, width / 2f, height - 4f, paint)
    
    val centerX = width / 2f
    val centerY = height / 4f + 4f
    val radius = 14f
    
    val gradient = android.graphics.RadialGradient(
        centerX - 4f, centerY - 4f, radius,
        android.graphics.Color.parseColor("#ff6b6b"),
        android.graphics.Color.parseColor("#e60000"),
        android.graphics.Shader.TileMode.CLAMP
    )
    paint.shader = gradient
    paint.style = android.graphics.Paint.Style.FILL
    canvas.drawCircle(centerX, centerY, radius, paint)
    
    paint.shader = null
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 3f
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(centerX, centerY, radius, paint)
    
    return bitmap
}

fun getMaplibreStyleJson(styleName: String): String {
    val tilesUrl = when (styleName) {
        "google_satellite" -> "https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}"
        "google_road" -> "https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}"
        "carto_dark" -> "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"
        else -> "https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}"
    }
    
    val maxZoom = if (styleName.startsWith("google")) 20 else 19
    
    return """
    {
      "version": 8,
      "sources": {
        "raster-tiles": {
          "type": "raster",
          "tiles": ["$tilesUrl"],
          "tileSize": 256,
          "maxzoom": $maxZoom
        }
      },
      "layers": [
        {
          "id": "raster-layer",
          "type": "raster",
          "source": "raster-tiles",
          "minzoom": 0,
          "maxzoom": $maxZoom
        }
      ]
    }
    """.trimIndent()
}

suspend fun fetchOsrmRoutePoints(waypoints: List<LocationBookmark>): List<Pair<Double, Double>> {
    return withContext(Dispatchers.IO) {
        val result = mutableListOf<Pair<Double, Double>>()
        try {
            val coordinatesStr = waypoints.joinToString(";") { "${it.lng},${it.lat}" }
            val urlStr = "https://router.project-osrm.org/route/v1/driving/$coordinatesStr?overview=full&geometries=geojson"
            val url = URL(urlStr)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "MandalaNetMockLocation/1.0 (Android)")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = org.json.JSONObject(response)
                val routes = jsonObject.optJSONArray("routes")
                if (routes != null && routes.length() > 0) {
                    val geometry = routes.getJSONObject(0).optJSONObject("geometry")
                    if (geometry != null && geometry.getString("type") == "LineString") {
                        val coordsArray = geometry.getJSONArray("coordinates")
                        for (i in 0 until coordsArray.length()) {
                            val coord = coordsArray.getJSONArray(i)
                            result.add(Pair(coord.getDouble(1), coord.getDouble(0)))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }
}

fun getDeviceActualLocation(context: Context, onLocationReceived: (Double, Double) -> Unit) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    var bestLocation: android.location.Location? = null
    try {
        val providers = locationManager.getProviders(true)
        for (provider in providers) {
            val l = locationManager.getLastKnownLocation(provider) ?: continue
            if (bestLocation == null || l.time > bestLocation.time) {
                bestLocation = l
            }
        }
        
        if (bestLocation != null) {
            onLocationReceived(bestLocation.latitude, bestLocation.longitude)
        }
        
        val provider = when {
            locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) -> android.location.LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) -> android.location.LocationManager.NETWORK_PROVIDER
            else -> null
        }
        
        if (provider != null) {
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    onLocationReceived(location.latitude, location.longitude)
                    locationManager.removeUpdates(this)
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            locationManager.requestLocationUpdates(
                provider,
                0L,
                0f,
                listener,
                android.os.Looper.getMainLooper()
            )
        } else if (bestLocation == null) {
            Toast.makeText(context, "Nyalakan GPS untuk mendapatkan lokasi terkini", Toast.LENGTH_SHORT).show()
        }
    } catch (e: SecurityException) {
        Toast.makeText(context, "Izin lokasi hardware ditolak.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        if (bestLocation == null) {
            Toast.makeText(context, "Gagal mendapatkan lokasi GPS: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

fun requestGpsEnablement(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>
) {
    val activity = context as? android.app.Activity ?: return
    val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
        5000L
    ).build()
    
    val builder = com.google.android.gms.location.LocationSettingsRequest.Builder()
        .addLocationRequest(locationRequest)
        .setAlwaysShow(true)
        
    val client = com.google.android.gms.location.LocationServices.getSettingsClient(activity)
    client.checkLocationSettings(builder.build())
        .addOnFailureListener { exception ->
            if (exception is com.google.android.gms.common.api.ResolvableApiException) {
                try {
                    val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(exception.resolution.intentSender).build()
                    launcher.launch(intentSenderRequest)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
}

data class LocationBookmark(val label: String, val lat: Double, val lng: Double)

data class LocationHistory(val lat: Double, val lng: Double, val timestamp: Long)

data class RoutePreset(val name: String, val waypoints: List<LocationBookmark>)

fun formatTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

fun isEmulator(context: android.content.Context): Boolean {
    val buildModel = android.os.Build.MODEL ?: ""
    val buildProduct = android.os.Build.PRODUCT ?: ""
    val buildHardware = android.os.Build.HARDWARE ?: ""
    val fingerprint = android.os.Build.FINGERPRINT ?: ""
    val brand = android.os.Build.BRAND ?: ""
    val device = android.os.Build.DEVICE ?: ""
    val manufacturer = android.os.Build.MANUFACTURER ?: ""

    val looksLikeEmulator = (fingerprint.startsWith("generic")
            || fingerprint.startsWith("unknown")
            || buildModel.contains("google_sdk")
            || buildModel.contains("Emulator")
            || buildModel.contains("Android SDK built for x86")
            || manufacturer.contains("Genymotion")
            || buildHardware.contains("goldfish")
            || buildHardware.contains("ranchu")
            || buildHardware.contains("nox")
            || buildProduct.contains("sdk")
            || buildProduct.contains("google_sdk")
            || buildProduct.contains("sdk_x86")
            || buildProduct.contains("vbox86p")
            || buildProduct.contains("emulator")
            || (brand.startsWith("generic") && device.startsWith("generic"))
            || "google_sdk" == buildProduct)
            
    if (looksLikeEmulator) return true
    
    val tm = context.getSystemService(android.content.Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
    val networkOperator = tm?.networkOperatorName
    if (networkOperator != null && (networkOperator.lowercase() == "android" || networkOperator.lowercase() == "google")) {
        return true
    }
    
    return false
}

fun getBrandSpecificInstructions(): String {
    val manufacturer = android.os.Build.MANUFACTURER.uppercase()
    return when {
        manufacturer.contains("SAMSUNG") -> 
            "Petunjuk Khusus HP Samsung:\n1. Di Opsi Developer, cari kelompok menu 'Debugging'.\n2. Ketuk 'Aplikasi lokasi palsu' (Mock Location App).\n3. Pilih 'Mandala Net' agar lokasi virtual Anda aktif."
        manufacturer.contains("XIAOMI") || manufacturer.contains("REDMI") || manufacturer.contains("POCO") -> 
            "Petunjuk Khusus HP Xiaomi / Poco:\n1. Di Opsi Developer, gulir ke paling bawah.\n2. Cari menu 'Pilih aplikasi lokasi palsu' (Select Mock Location App).\n3. Ketuk dan pilih 'Mandala Net'. Jika lokasi melompat, nonaktifkan 'Optimisasi MIUI'."
        manufacturer.contains("OPPO") || manufacturer.contains("REALME") || manufacturer.contains("ONEPLUS") -> 
            "Petunjuk Khusus HP Oppo / Realme:\n1. Buka Opsi Developer.\n2. Gulir ke bawah hingga menemukan bagian 'Pilih aplikasi lokasi palsu'.\n3. Pilih 'Mandala Net'."
        manufacturer.contains("VIVO") -> 
            "Petunjuk Khusus HP Vivo:\n1. Masuk ke Opsi Developer.\n2. Cari opsi 'Pilih aplikasi lokasi palsu' di bagian Debugging.\n3. Ketuk dan pilih 'Mandala Net'."
        else -> 
            "Petunjuk Umum Perangkat Asli:\n1. Di Opsi Developer, cari 'Pilih aplikasi lokasi palsu' / 'Select mock location app'.\n2. Ketuk menu tersebut dan pilih 'Mandala Net'."
    }
}

fun isDeveloperOptionsEnabled(context: android.content.Context): Boolean {
    return try {
        android.provider.Settings.Global.getInt(
            context.contentResolver,
            android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) != 0
    } catch (e: Exception) {
        false
    }
}

fun openDeveloperSettingsCompat(context: android.content.Context) {
    val isDevEnabled = isDeveloperOptionsEnabled(context)
    val isEmulatorDevice = isEmulator(context)
    val deviceTypeLabel = if (isEmulatorDevice) "Emulator" else "HP Asli (${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL})"

    if (!isDevEnabled) {
        // If developer options are not active yet, direct to About Phone (Device Info Settings) so they can tap Build Number 7 times
        android.widget.Toast.makeText(
            context,
            "Developer Mode belum aktif! Silakan ketuk 'Nomor Versi' / 'Build Number' sebanyak 7 kali di menu ini untuk mengaktifkannya.",
            android.widget.Toast.LENGTH_LONG
        ).show()
        
        val intentsToTry = listOf(
            // Try Software Information directly (e.g. Samsung)
            android.content.Intent().apply {
                setClassName("com.android.settings", "com.android.settings.Settings\$SoftwareInformationSettingsActivity")
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            // Try Device Info Settings directly (e.g. Xiaomi, Oppo, Vivo, Google)
            android.content.Intent().apply {
                setClassName("com.android.settings", "com.android.settings.Settings\$DeviceInfoSettingsActivity")
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            // Standard Device Info Settings intent
            android.content.Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            // Generic Settings intent
            android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
        
        var launched = false
        for (intent in intentsToTry) {
            try {
                context.startActivity(intent)
                launched = true
                break
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (!launched) {
            android.widget.Toast.makeText(context, "Gagal membuka Pengaturan secara otomatis. Silakan buka Pengaturan HP Anda secara manual.", android.widget.Toast.LENGTH_LONG).show()
        }
        return
    }

    val extrasBundle = android.os.Bundle().apply {
        putString(":settings:fragment_args_key", "mock_location_app")
        putString(":settings:show_fragment_args", "mock_location_app")
        putString("extra_fragment_key", "mock_location_app")
        putString("show_fragment_args_key", "mock_location_app")
        putString("android:view_id", "mock_location_app")
    }

    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtras(extrasBundle)
        putExtra(":settings:show_fragment_args", extrasBundle)
        putExtra(":settings:fragment_args_key", "mock_location_app")
        putExtra(":settings:show_fragment_args_key", "mock_location_app")
        putExtra("extra_fragment_key", "mock_location_app")
        putExtra("show_fragment_args_key", "mock_location_app")
        putExtra("android:view_id", "mock_location_app")
        putExtra("android:view_id_resource_name", "mock_location_app")
    }

    val intentsToTry = listOf(
        intent,
        android.content.Intent().apply {
            setClassName("com.android.settings", "com.android.settings.Settings\$DevelopmentSettingsDashboardActivity")
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtras(extrasBundle)
            putExtra(":settings:show_fragment_args", extrasBundle)
            putExtra(":settings:fragment_args_key", "mock_location_app")
            putExtra("show_fragment_args_key", "mock_location_app")
        },
        android.content.Intent().apply {
            setClassName("com.android.settings", "com.android.settings.Settings\$DevelopmentSettingsActivity")
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtras(extrasBundle)
        },
        android.content.Intent().apply {
            setClassName("com.android.settings", "com.android.settings.DevelopmentSettings")
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtras(extrasBundle)
        },
        android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    )

    var launched = false
    for ((index, intentToTry) in intentsToTry.withIndex()) {
        try {
            context.startActivity(intentToTry)
            launched = true
            val toastMsg = when {
                index < 4 -> {
                    if (isEmulatorDevice) {
                        "Terdeteksi $deviceTypeLabel. Membuka Opsi Developer & auto-scroll ke 'Pilih aplikasi lokasi palsu' (Mock Location App)."
                    } else {
                        "Terdeteksi $deviceTypeLabel.\nMembuka Opsi Developer. Silakan gulir ke bawah dan pilih Mandala Net sebagai aplikasi lokasi palsu."
                    }
                }
                else -> "Membuka Pengaturan. Cari menu 'Opsi Developer / Opsi Pengembang' secara manual."
            }
            android.widget.Toast.makeText(context, toastMsg, android.widget.Toast.LENGTH_LONG).show()
            break
        } catch (e: Exception) {
            android.util.Log.e("MockLocationScreen", "Failed to launch Developer Settings intent at index $index", e)
        }
    }

    if (!launched) {
        android.widget.Toast.makeText(context, "Gagal membuka Pengaturan secara otomatis. Silakan buka Pengaturan HP Anda secara manual.", android.widget.Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockLocationScreen(viewModel: com.mandala.net.MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isDark = CyberTheme.isDark || CyberTheme.isAmoled

    val bgMain = androidx.compose.material3.MaterialTheme.colorScheme.background
    val bgSidebar = androidx.compose.material3.MaterialTheme.colorScheme.surface
    val bgCard = CyberTheme.SignalCardBg
    val bgCardInner = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    val bgCardSelected = CyberTheme.SignalCardBorder
    val bgInput = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    val bgFloatingPill = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val borderStrokeColor = CyberTheme.SignalCardBorder
    val textOnPrimaryAccent = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
    val textOnSuccessGreen = Color.White

    val currentLat by MockLocationManager.latitude.collectAsState()
    val currentLng by MockLocationManager.longitude.collectAsState()
    val isServiceActive by MockLocationManager.isActive.collectAsState()
    val showJoystick by MockLocationManager.showJoystick.collectAsState()
    val currentSpeed by MockLocationManager.speedKmh.collectAsState()
    val mockLocationError by MockLocationManager.mockLocationError.collectAsState()

    // MapLibre native map controller
    val maplibreController = remember { MapLibreController() }

    // Geocoding states
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<LocationBookmark>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Battery optimization check
    var isBatteryOptimizing by remember { mutableStateOf(false) }

    val checkBatteryOptimizations = {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                isBatteryOptimizing = !pm.isIgnoringBatteryOptimizations(context.packageName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(Unit) {
        checkBatteryOptimizations()
    }

    // Manual coordinate inputs
    var latInput by remember { mutableStateOf(currentLat.toString()) }
    var lngInput by remember { mutableStateOf(currentLng.toString()) }

    // Route Simulation states backed by MockLocationManager for screen recreation persistence
    val routeWaypoints by MockLocationManager.userWaypoints.collectAsState()
    var simulationSpeed by remember { mutableStateOf(50f) } // km/h
    var isSimulatingRoute by remember { mutableStateOf(false) }
    
    val routeTotalDistanceMeters by MockLocationManager.routeTotalDistanceMeters.collectAsState()
    val routeRemainingDistanceMeters by MockLocationManager.routeRemainingDistanceMeters.collectAsState()
    val routeRemainingSeconds by MockLocationManager.routeRemainingSeconds.collectAsState()
    val routePointsList by MockLocationManager.routePointsList.collectAsState()
    val routeTotalDurationSeconds by remember {
        derivedStateOf {
            val speedMs = simulationSpeed * (1000f / 3600f)
            if (speedMs > 0) (routeTotalDistanceMeters / speedMs).toInt() else 0
        }
    }

    val routeWaypointsKey by remember {
        derivedStateOf {
            routeWaypoints.joinToString(separator = ";") { "${it.lat},${it.lng},${it.label}" }
        }
    }

    LaunchedEffect(routeWaypointsKey) {
        // Draw waypoints and guide line immediately (straight line fallback first)
        maplibreController.drawWaypoints(routeWaypoints)
        
        if (routeWaypoints.size >= 2) {
            try {
                // Background fetch OSRM route points to align to streets
                val cleanWaypoints = routeWaypoints.toList()
                val routePoints = fetchOsrmRoutePoints(cleanWaypoints)
                if (routePoints.isNotEmpty()) {
                    MockLocationManager.routePointsList.value = routePoints
                    maplibreController.drawPlannedRoute(routePoints)
                    
                    var total = 0f
                    val distanceMeters = FloatArray(1)
                    for (idx in 0 until routePoints.size - 1) {
                        val p1 = routePoints[idx]
                        val p2 = routePoints[idx + 1]
                        android.location.Location.distanceBetween(p1.first, p1.second, p2.first, p2.second, distanceMeters)
                        total += distanceMeters[0]
                    }
                    MockLocationManager.routeTotalDistanceMeters.value = total
                    MockLocationManager.routeRemainingDistanceMeters.value = total
                } else {
                    // Straight line OSRM fallback
                    val straightPoints = cleanWaypoints.map { Pair(it.lat, it.lng) }
                    MockLocationManager.routePointsList.value = straightPoints
                    maplibreController.drawPlannedRoute(straightPoints)
                    
                    var total = 0f
                    val distanceMeters = FloatArray(1)
                    for (idx in 0 until straightPoints.size - 1) {
                        val p1 = straightPoints[idx]
                        val p2 = straightPoints[idx + 1]
                        android.location.Location.distanceBetween(p1.first, p1.second, p2.first, p2.second, distanceMeters)
                        total += distanceMeters[0]
                    }
                    MockLocationManager.routeTotalDistanceMeters.value = total
                    MockLocationManager.routeRemainingDistanceMeters.value = total
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            MockLocationManager.routeTotalDistanceMeters.value = 0f
            MockLocationManager.routeRemainingDistanceMeters.value = 0f
            MockLocationManager.routePointsList.value = emptyList()
        }
    }

    // Sync input fields with current coordinates when they change (due to map click or joystick move)
    LaunchedEffect(currentLat, currentLng) {
        latInput = String.format("%.6f", currentLat).replace(",", ".")
        lngInput = String.format("%.6f", currentLng).replace(",", ".")
    }

    // Map center coordinates states
    var mapCenterLat by remember { mutableStateOf(currentLat) }
    var mapCenterLng by remember { mutableStateOf(currentLng) }

    // Sync map center with current coordinates when they change initially
    LaunchedEffect(currentLat, currentLng) {
        mapCenterLat = currentLat
        mapCenterLng = currentLng
    }

    // Sidebar expand state
    var isSidebarExpanded by remember { mutableStateOf(false) }

    // Tile style state (default "google_road")
    var selectedTileStyle by remember { mutableStateOf("google_road") }

    // Mock Location developer options state check
    var isMockAllowed by remember { mutableStateOf(false) }

    val gpsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            getDeviceActualLocation(context) { lat, lng ->
                MockLocationManager.latitude.value = lat
                MockLocationManager.longitude.value = lng
                mapCenterLat = lat
                mapCenterLng = lng
                maplibreController.animateCameraTo(lat, lng)
            }
        }
    }

    // Initial Geolocation runtime permission launcher
    val initialPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                getDeviceActualLocation(context) { lat, lng ->
                    MockLocationManager.latitude.value = lat
                    MockLocationManager.longitude.value = lng
                    mapCenterLat = lat
                    mapCenterLng = lng
                    maplibreController.animateCameraTo(lat, lng)
                }
            } else {
                requestGpsEnablement(context, gpsLauncher)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                getDeviceActualLocation(context) { lat, lng ->
                    MockLocationManager.latitude.value = lat
                    MockLocationManager.longitude.value = lng
                    mapCenterLat = lat
                    mapCenterLng = lng
                    maplibreController.animateCameraTo(lat, lng)
                }
            } else {
                requestGpsEnablement(context, gpsLauncher)
            }
        } else {
            initialPermissionLauncher.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
        
        while (true) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val allowed = try {
                locationManager.addTestProvider(
                    android.location.LocationManager.GPS_PROVIDER,
                    false, false, false, false, true, true, true, 
                    android.location.provider.ProviderProperties.POWER_USAGE_LOW, 
                    android.location.provider.ProviderProperties.ACCURACY_FINE
                )
                locationManager.removeTestProvider(android.location.LocationManager.GPS_PROVIDER)
                true
            } catch (e: SecurityException) {
                false
            } catch (e: Exception) {
                false
            }
            isMockAllowed = allowed
            
            // Caching check frequency: delay 15 seconds if active to save resources, 2 seconds if blocked
            if (allowed) {
                kotlinx.coroutines.delay(15000)
            } else {
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    // Save Location Bookmark states
    var bookmarkNameInput by remember { mutableStateOf("") }
    var showSaveBookmarkDialog by remember { mutableStateOf(false) }
    val sharedPrefs = remember { context.getSharedPreferences("mock_location_bookmarks", Context.MODE_PRIVATE) }
    val bookmarks = remember { mutableStateListOf<LocationBookmark>() }

    // Route Preset States
    var presetNameInput by remember { mutableStateOf("") }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showLoadPresetDialog by remember { mutableStateOf(false) }
    val presetsPrefs = remember { context.getSharedPreferences("mock_location_presets", Context.MODE_PRIVATE) }
    val routePresets = remember { mutableStateListOf<RoutePreset>() }

    // History Log States
    val historyPrefs = remember { context.getSharedPreferences("mock_location_history_v1", Context.MODE_PRIVATE) }
    val historyList = remember { mutableStateListOf<LocationHistory>() }
    var selectedSidebarTab by remember { mutableStateOf(0) }

    // Route Simulation Effect
    LaunchedEffect(isSimulatingRoute) {
        if (isSimulatingRoute && routeWaypoints.size >= 2) {
            try {
                maplibreController.clearTrail()
                
                // Cached route points from MockLocationManager
                val routePoints = MockLocationManager.routePointsList.value
                
                // Delegate to Service for reliable background processing
                MockLocationManager.routeWaypoints.value = routePoints
                MockLocationManager.isSimulatingRoute.value = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            MockLocationManager.isSimulatingRoute.value = false
            MockLocationManager.routeWaypoints.value = emptyList()
        }
    }

    val addLocationToHistory = { lat: Double, lng: Double ->
        val now = System.currentTimeMillis()
        val isDuplicate = historyList.firstOrNull()?.let { last ->
            Math.abs(last.lat - lat) < 0.0001 && Math.abs(last.lng - lng) < 0.0001
        } ?: false
        
        if (!isDuplicate) {
            val newElement = LocationHistory(lat, lng, now)
            historyList.add(0, newElement)
            while (historyList.size > 10) {
                historyList.removeAt(historyList.size - 1)
            }
            val serialized = historyList.joinToString(";") { "${it.lat},${it.lng},${it.timestamp}" }
            historyPrefs.edit().putString("history_items", serialized).apply()
        }
    }

    // Geolocation runtime permission launcher & centering helper
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                getDeviceActualLocation(context) { lat, lng ->
                    MockLocationManager.latitude.value = lat
                    MockLocationManager.longitude.value = lng
                    maplibreController.animateCameraTo(lat, lng)
                    addLocationToHistory(lat, lng)
                    Toast.makeText(context, "Berhasil menyelaraskan lokasi dengan GPS hardware", Toast.LENGTH_SHORT).show()
                }
            } else {
                requestGpsEnablement(context, gpsLauncher)
            }
        } else {
            Toast.makeText(context, "Izin lokasi diperlukan untuk mendapatkan lokasi hardware", Toast.LENGTH_SHORT).show()
        }
    }

    val centerOnHardwareLocation = {
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (hasFine || hasCoarse) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                getDeviceActualLocation(context) { lat, lng ->
                    MockLocationManager.latitude.value = lat
                    MockLocationManager.longitude.value = lng
                    maplibreController.animateCameraTo(lat, lng)
                    addLocationToHistory(lat, lng)
                    Toast.makeText(context, "Berhasil menyelaraskan lokasi dengan GPS hardware", Toast.LENGTH_SHORT).show()
                }
            } else {
                requestGpsEnablement(context, gpsLauncher)
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Load initial bookmarks and history
    LaunchedEffect(Unit) {
        val allPrefs = sharedPrefs.all
        bookmarks.clear()
        allPrefs.forEach { (key, value) ->
            if (value is String) {
                val parts = value.split(",")
                if (parts.size == 2) {
                    val lat = parts[0].toDoubleOrNull()
                    val lng = parts[1].toDoubleOrNull()
                    if (lat != null && lng != null) {
                        bookmarks.add(LocationBookmark(key, lat, lng))
                    }
                }
            }
        }
        
        val allPresets = presetsPrefs.all
        routePresets.clear()
        allPresets.forEach { (key, value) ->
            if (value is String) {
                // format: lat1,lng1,label1;lat2,lng2,label2;...
                val wps = value.split(";").filter { it.isNotEmpty() }.mapNotNull {
                    val parts = it.split(",")
                    if (parts.size >= 3) {
                        val lat = parts[0].toDoubleOrNull()
                        val lng = parts[1].toDoubleOrNull()
                        val label = parts.drop(2).joinToString(",")
                        if (lat != null && lng != null) LocationBookmark(label, lat, lng) else null
                    } else null
                }
                if (wps.isNotEmpty()) {
                    routePresets.add(RoutePreset(key, wps))
                }
            }
        }

        val savedHistory = historyPrefs.getString("history_items", "") ?: ""
        if (savedHistory.isNotEmpty()) {
            val items = savedHistory.split(";").mapNotNull { itemStr ->
                val parts = itemStr.split(",")
                if (parts.size >= 3) {
                    val lat = parts[0].toDoubleOrNull()
                    val lng = parts[1].toDoubleOrNull()
                    val time = parts[2].toLongOrNull()
                    if (lat != null && lng != null && time != null) {
                        LocationHistory(lat, lng, time)
                    } else null
                } else null
            }
            historyList.clear()
            historyList.addAll(items)
        }
    }

    // Direct OSM Nominatim geocode request

    // Reverse Geocoding to get human readable address from coordinates
    fun performReverseGeocode(lat: Double, lng: Double) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val urlString = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lng&format=json"
                val connection = URL(urlString).openConnection()
                connection.setRequestProperty("User-Agent", "MandalaNetApp/1.0 (ahmadpsgl5@gmail.com)")
                
                val responseText = connection.getInputStream().bufferedReader().use { it.readText() }
                val obj = org.json.JSONObject(responseText)
                val displayName = obj.optString("display_name", "")
                if (displayName.isNotEmpty()) {
                    // Shorten name to first 3 elements (e.g. road, district, city)
                    val shortName = displayName.split(",").take(3).joinToString(",")
                    withContext(Dispatchers.Main) {
                        searchQuery = shortName.trim()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MockLocation", "Reverse geocode failed: ${e.message}")
            }
        }
    }

    fun performSearch(query: String) {
        if (query.trim().isEmpty()) return
        isSearching = true
        
        // Try parsing coordinates directly first (e.g. "-6.200000, 106.816666" or "-6.200000 106.816666")
        val cleanedQuery = query.trim().replace(",", " ").replace(";", " ")
        val parts = cleanedQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            val first = parts[0].toDoubleOrNull()
            val second = parts[1].toDoubleOrNull()
            if (first != null && second != null && first >= -90.0 && first <= 90.0 && second >= -180.0 && second <= 180.0) {
                MockLocationManager.isSimulatingRoute.value = false
                isSimulatingRoute = false
                MockLocationManager.latitude.value = first
                MockLocationManager.longitude.value = second
                searchQuery = String.format(java.util.Locale.US, "%.6f, %.6f", first, second)
                performReverseGeocode(first, second)
                if (isServiceActive) addLocationToHistory(first, second)
                searchResults = emptyList()
                isSearching = false
                return
            }
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                
                val nominatimSearch = {
                    val urlString = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=1&addressdetails=1&accept-language=id,en"
                    val connection = URL(urlString).openConnection()
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    
                    val responseText = connection.getInputStream().bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(responseText)
                    if (jsonArray.length() > 0) {
                        val obj = jsonArray.getJSONObject(0)
                        val displayName = obj.optString("display_name", "Tempat Tanpa Nama")
                        val shortName = displayName.split(",").map { it.trim() }.take(6).joinToString(", ")
                        val lat = obj.optDouble("lat", 0.0)
                        val lng = obj.optDouble("lon", 0.0)
                        LocationBookmark(shortName, lat, lng)
                    } else null
                }

                val googlePlacesSearch = {
                    val apiKey = com.mandala.net.BuildConfig.GOOGLE_MAPS_API_KEY
                    val urlString = "https://maps.googleapis.com/maps/api/place/textsearch/json?query=$encodedQuery&key=$apiKey"
                    val connection = URL(urlString).openConnection()
                    val responseText = connection.getInputStream().bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(responseText)
                    val results = jsonObject.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val obj = results.getJSONObject(0)
                        val name = obj.optString("name", "Tempat Tanpa Nama")
                        val formattedAddress = obj.optString("formatted_address", "")
                        val label = if (formattedAddress.isNotEmpty()) "$name, $formattedAddress" else name
                        val location = obj.getJSONObject("geometry").getJSONObject("location")
                        val lat = location.optDouble("lat", 0.0)
                        val lng = location.optDouble("lng", 0.0)
                        LocationBookmark(label, lat, lng)
                    } else null
                }
                
                val bestResult = try {
                    val apiKey = com.mandala.net.BuildConfig.GOOGLE_MAPS_API_KEY
                    if (apiKey.isNotEmpty() && apiKey != "YOUR_API_KEY_HERE" && apiKey != "dummy") {
                        googlePlacesSearch() ?: nominatimSearch()
                    } else {
                        nominatimSearch()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MockLocation", "Google Places API failed: ${e.message}", e)
                    nominatimSearch()
                }
                
                withContext(Dispatchers.Main) {
                    isSearching = false
                    if (bestResult != null) {
                        MockLocationManager.isSimulatingRoute.value = false
                        isSimulatingRoute = false
                        MockLocationManager.latitude.value = bestResult.lat
                        MockLocationManager.longitude.value = bestResult.lng
                        searchQuery = String.format(java.util.Locale.US, "%.6f, %.6f", bestResult.lat, bestResult.lng)
                        performReverseGeocode(bestResult.lat, bestResult.lng)
                        maplibreController.animateCameraTo(bestResult.lat, bestResult.lng)
                        if (isServiceActive) {
                            addLocationToHistory(bestResult.lat, bestResult.lng)
                        }
                        searchResults = emptyList()
                        Toast.makeText(context, "Terbang ke: ${bestResult.label}", Toast.LENGTH_SHORT).show()
                    } else {
                        searchResults = emptyList()
                        Toast.makeText(context, "Lokasi tidak ditemukan", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSearching = false
                    Toast.makeText(context, "Error mencari lokasi: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Toggle mock service
    fun toggleService() {
        checkBatteryOptimizations()
        if (isServiceActive) {
            val stopIntent = Intent(context, MockLocationService::class.java).apply {
                action = "ACTION_STOP"
            }
            context.stopService(stopIntent)
            Toast.makeText(context, "Mock Location Dinonaktifkan", Toast.LENGTH_SHORT).show()
        } else {
            // Check overlays draw permission first if joystick wants to be shown
            if (showJoystick && !Settings.canDrawOverlays(context)) {
                Toast.makeText(context, "Buka pengaturan dan berikan izin overlay!", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
                return
            }

            val startIntent = Intent(context, MockLocationService::class.java).apply {
                action = "ACTION_START"
                putExtra("EXTRA_LAT", currentLat)
                putExtra("EXTRA_LNG", currentLng)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
            addLocationToHistory(currentLat, currentLng)
            Toast.makeText(context, "Mock Location Aktif!", Toast.LENGTH_SHORT).show()
        }
    }



    val isSimulatingRouteManager by MockLocationManager.isSimulatingRoute.collectAsState()
    
    // Sync local simulating state with manager state
    LaunchedEffect(isSimulatingRouteManager) {
        isSimulatingRoute = isSimulatingRouteManager
    }

    // Dynamic map sync trigger: update marker, trail, and remaining metrics when coordinates change
    LaunchedEffect(currentLat, currentLng, isSimulatingRoute) {
        if (isSimulatingRoute) {
            val speedColorHex = when {
                simulationSpeed < 20f -> "#4CAF50"
                simulationSpeed < 60f -> "#FFB300"
                simulationSpeed < 100f -> "#E53935"
                else -> "#8E24AA"
            }
            maplibreController.updateActivePin(currentLat, currentLng)
            maplibreController.addTrailPoint(currentLat, currentLng, speedColorHex)
            
            val routePoints = routePointsList
            if (routePoints.isNotEmpty()) {
                var closestIdx = 0
                var minDistance = Double.MAX_VALUE
                for (idx in routePoints.indices) {
                    val pt = routePoints[idx]
                    val dLat = pt.first - currentLat
                    val dLng = pt.second - currentLng
                    val distSq = dLat * dLat + dLng * dLng
                    if (distSq < minDistance) {
                        minDistance = distSq
                        closestIdx = idx
                    }
                }
                
                var remainingDist = 0f
                val distanceMeters = FloatArray(1)
                
                if (closestIdx < routePoints.size - 1) {
                    val nextPt = routePoints[closestIdx + 1]
                    android.location.Location.distanceBetween(currentLat, currentLng, nextPt.first, nextPt.second, distanceMeters)
                    remainingDist += distanceMeters[0]
                    
                    for (idx in (closestIdx + 1) until (routePoints.size - 1)) {
                        val p1 = routePoints[idx]
                        val p2 = routePoints[idx + 1]
                        android.location.Location.distanceBetween(p1.first, p1.second, p2.first, p2.second, distanceMeters)
                        remainingDist += distanceMeters[0]
                    }
                }
                
                MockLocationManager.routeRemainingDistanceMeters.value = remainingDist
                val currentSpeedMs = simulationSpeed * (1000f / 3600f)
                MockLocationManager.routeRemainingSeconds.value = if (currentSpeedMs > 0) (remainingDist / currentSpeedMs).toInt() else 0
            }
        } else {
            maplibreController.updateActivePin(currentLat, currentLng)
        }
    }

    // Tile style dynamic update trigger
    LaunchedEffect(selectedTileStyle) {
        maplibreController.setStyle(selectedTileStyle)
    }

    mockLocationError?.let { errorMsg ->
        val pagerState = rememberPagerState(pageCount = { 3 })
        AlertDialog(
            onDismissRequest = { MockLocationManager.mockLocationError.value = null },
            title = {
                Text("Setup Opsi Developer", fontWeight = FontWeight.Bold, color = CyberTheme.PrimaryAccent)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) { page ->
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val title = when (page) {
                                0 -> "Langkah 1: Aktifkan Opsi Developer"
                                1 -> "Langkah 2: Masuk Menu Opsi Developer"
                                else -> "Langkah 3: Pilih Aplikasi Lokasi Palsu"
                            }
                            val desc = when (page) {
                                0 -> "Buka Pengaturan > Tentang Telepon, lalu ketuk 'Nomor Versi' / 'Build Number' sebanyak 7 kali hingga developer mode aktif."
                                1 -> "Setelah aktif, masuk ke menu Opsi Developer (Developer Options) di Pengaturan > Sistem / Setelan Tambahan."
                                else -> "Cari menu 'Pilih aplikasi lokasi palsu' (Mock Location App), lalu pilih 'Mandala Net' agar lokasi virtual aktif."
                            }
                            Text(
                                text = title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = CyberTheme.TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = desc,
                                fontSize = 11.5.sp,
                                color = CyberTheme.TextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Pager indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0..2) {
                            val isSelected = pagerState.currentPage == i
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) CyberTheme.PrimaryAccent else CyberTheme.TextSecondary.copy(alpha = 0.4f))
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        MockLocationManager.mockLocationError.value = null
                        openDeveloperSettingsCompat(context)
                    }
                ) {
                    Text("Buka Pengaturan", color = CyberTheme.PrimaryAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { MockLocationManager.mockLocationError.value = null }) {
                    Text("Tutup", color = CyberTheme.TextSecondary)
                }
            }
        )
    }

    // Save Location dialog
    if (showSaveBookmarkDialog) {
        AlertDialog(
            onDismissRequest = { showSaveBookmarkDialog = false },
            title = {
                Text(
                    "Simpan Favorit",
                    fontWeight = FontWeight.Bold,
                    color = CyberTheme.PrimaryAccent
                )
            },
            text = {
                Column {
                    Text(
                        "Masukkan nama penanda untuk koordinat ini:",
                        fontSize = 13.sp,
                        color = CyberTheme.TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = bookmarkNameInput,
                        onValueChange = { bookmarkNameInput = it },
                        placeholder = { Text("Nama Lokasi (misal: Rumah, Kantor)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CyberTheme.TextPrimary,
                            unfocusedTextColor = CyberTheme.TextPrimary,
                            focusedBorderColor = CyberTheme.PrimaryAccent,
                            unfocusedBorderColor = CyberTheme.TextSecondary.copy(alpha = 0.5f),
                            focusedLabelColor = CyberTheme.PrimaryAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bookmark_name_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Lat: ${String.format("%.5f", currentLat)}\nLng: ${String.format("%.5f", currentLng)}",
                        fontSize = 11.sp,
                        color = CyberTheme.TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = if (bookmarkNameInput.trim().isEmpty()) {
                            "Lokasi Saya ${bookmarks.size + 1}"
                        } else {
                            bookmarkNameInput.trim()
                        }
                        sharedPrefs.edit().putString(name, "$currentLat,$currentLng").apply()
                        val existingIndex = bookmarks.indexOfFirst { it.label == name }
                        if (existingIndex >= 0) {
                            bookmarks[existingIndex] = LocationBookmark(name, currentLat, currentLng)
                        } else {
                            bookmarks.add(LocationBookmark(name, currentLat, currentLng))
                        }
                        Toast.makeText(context, "Favorit disimpan: $name", Toast.LENGTH_SHORT).show()
                        bookmarkNameInput = ""
                        showSaveBookmarkDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberTheme.PrimaryAccent)
                ) {
                    Text("Simpan", color = textOnPrimaryAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveBookmarkDialog = false }) {
                    Text("Batal", color = CyberTheme.TextSecondary)
                }
            },
            containerColor = bgSidebar,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, borderStrokeColor, RoundedCornerShape(16.dp))
        )
    }

    // Save Route Preset Dialog
    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = {
                Text(
                    "Simpan Preset Rute",
                    fontWeight = FontWeight.Bold,
                    color = CyberTheme.PrimaryAccent
                )
            },
            text = {
                Column {
                    Text(
                        "Masukkan nama untuk rute ini:",
                        fontSize = 13.sp,
                        color = CyberTheme.TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        placeholder = { Text("Nama Preset (misal: Rute Kerja)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CyberTheme.TextPrimary,
                            unfocusedTextColor = CyberTheme.TextPrimary,
                            focusedBorderColor = CyberTheme.PrimaryAccent,
                            unfocusedBorderColor = CyberTheme.TextSecondary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("preset_name_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Total ${routeWaypoints.size} waypoint",
                        fontSize = 11.sp,
                        color = CyberTheme.TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = if (presetNameInput.trim().isEmpty()) {
                            "Preset ${routePresets.size + 1}"
                        } else {
                            presetNameInput.trim()
                        }
                        val waypointsStr = routeWaypoints.joinToString(";") { "${it.lat},${it.lng},${it.label}" }
                        presetsPrefs.edit().putString(name, waypointsStr).apply()
                        
                        val existingIndex = routePresets.indexOfFirst { it.name == name }
                        if (existingIndex >= 0) {
                            routePresets[existingIndex] = RoutePreset(name, routeWaypoints.toList())
                        } else {
                            routePresets.add(RoutePreset(name, routeWaypoints.toList()))
                        }
                        Toast.makeText(context, "Preset disimpan: $name", Toast.LENGTH_SHORT).show()
                        presetNameInput = ""
                        showSavePresetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberTheme.PrimaryAccent)
                ) {
                    Text("Simpan", color = textOnPrimaryAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text("Batal", color = CyberTheme.TextSecondary)
                }
            },
            containerColor = bgSidebar,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, borderStrokeColor, RoundedCornerShape(16.dp))
        )
    }

    // Load Route Preset Dialog
    if (showLoadPresetDialog) {
        AlertDialog(
            onDismissRequest = { showLoadPresetDialog = false },
            title = {
                Text(
                    "Muat Preset Rute",
                    fontWeight = FontWeight.Bold,
                    color = CyberTheme.PrimaryAccent
                )
            },
            text = {
                if (routePresets.isEmpty()) {
                    Text("Belum ada preset tersimpan.", color = CyberTheme.TextSecondary, fontSize = 13.sp)
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                    ) {
                        items(routePresets.size) { index ->
                            val preset = routePresets[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(bgCardInner, RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isSimulatingRoute) {
                                            Toast.makeText(context, "Hentikan simulasi terlebih dahulu", Toast.LENGTH_SHORT).show()
                                        } else {
                                            MockLocationManager.userWaypoints.value = preset.waypoints
                                            showLoadPresetDialog = false
                                            Toast.makeText(context, "Preset dimuat: ${preset.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(preset.name, color = CyberTheme.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("${preset.waypoints.size} titik waypoint", color = CyberTheme.TextSecondary, fontSize = 11.sp)
                                }
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Hapus",
                                    tint = CyberTheme.ErrorRed,
                                    modifier = Modifier.size(20.dp).clickable {
                                        presetsPrefs.edit().remove(preset.name).apply()
                                        routePresets.removeAt(index)
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLoadPresetDialog = false }) {
                    Text("Tutup", color = CyberTheme.PrimaryAccent)
                }
            },
            containerColor = bgSidebar,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, borderStrokeColor, RoundedCornerShape(16.dp))
        )
    }

    // Modern Overlay Responsive Layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgMain)
    ) {
        
        // BACKDROP CLICK TO CLOSE SIDEBAR
        AnimatedVisibility(
            visible = isSidebarExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { isSidebarExpanded = false }
            )
        }

        // COLLAPSIBLE SIDEBAR MENU
        AnimatedVisibility(
            visible = isSidebarExpanded,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
            modifier = Modifier.zIndex(10f).align(Alignment.CenterStart)
        ) {
            Row(modifier = Modifier.fillMaxHeight()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp)
                        .background(bgSidebar)
                        .drawBehind {
                            drawLine(
                                color = borderStrokeColor,
                                start = Offset(this.size.width, 0f),
                                end = Offset(this.size.width, this.size.height),
                                strokeWidth = 2f
                            )
                        }
                ) {
                // Scrollable container for sidebar content
                val sidebarScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(sidebarScrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    
                    // HEADER WITH LIVE DEVELOPER STATUS BADGE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "MANDALA NET",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CyberTheme.PrimaryAccent,
                                    letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (isMockAllowed) CyberTheme.SuccessGreen else CyberTheme.ErrorRed, CircleShape)
                                )
                            }
                            Text(
                                text = "SPOOFER GPS",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = CyberTheme.TextPrimary
                            )
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val badgeColor = if (isMockAllowed) CyberTheme.SuccessGreen else CyberTheme.ErrorRed
                            val badgeText = if (isMockAllowed) "READY" else "BLOCKED"
                            Box(
                                modifier = Modifier
                                    .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = badgeColor
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = CyberTheme.PrimaryAccent.copy(alpha = 0.1f))

                    // TAB SELECTOR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgCardInner, RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("KONTROL", "RUTE GPS", "RIWAYAT").forEachIndexed { index, label ->
                            val isSelected = selectedSidebarTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) CyberTheme.PrimaryAccent else Color.Transparent)
                                    .clickable { selectedSidebarTab = index }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    color = if (isSelected) CyberTheme.TextPrimary else CyberTheme.TextSecondary
                                )
                            }
                        }
                    }

                    if (selectedSidebarTab == 0) {
                        // 1. SEARCH ADDRESS SECTION (Nominatim)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = bgCard),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, borderStrokeColor),
                            modifier = Modifier.fillMaxWidth().animateContentSize()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "CARI ALAMAT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberTheme.TextSecondary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Nama kota, jalan...", fontSize = 11.sp, color = CyberTheme.TextSecondary) },
                                        leadingIcon = { Icon(Icons.Default.Search, "Search", tint = CyberTheme.PrimaryAccent, modifier = Modifier.size(16.dp)) },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { searchQuery = ""; searchResults = emptyList() }) {
                                                    Icon(Icons.Default.Close, "Clear", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { performSearch(searchQuery) }),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = bgInput,
                                            unfocusedContainerColor = bgInput,
                                            focusedIndicatorColor = CyberTheme.PrimaryAccent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            focusedTextColor = CyberTheme.TextPrimary,
                                            unfocusedTextColor = CyberTheme.TextPrimary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp)
                                            .testTag("map_search_input"),
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = { performSearch(searchQuery) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberTheme.PrimaryAccent),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier.height(56.dp)
                                    ) {
                                        if (isSearching) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = textOnPrimaryAccent, strokeWidth = 2.dp)
                                        } else {
                                            Text("Cari", fontWeight = FontWeight.Bold, color = textOnPrimaryAccent, fontSize = 11.sp)
                                        }
                                    }
                                }

                                // Search Results dropdown
                                if (searchResults.isNotEmpty()) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = bgCardSelected),
                                        border = BorderStroke(1.dp, borderStrokeColor),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                            .heightIn(max = 240.dp)
                                    ) {
                                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                            items(searchResults) { loc ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            MockLocationManager.latitude.value = loc.lat
                                                            MockLocationManager.longitude.value = loc.lng
                                                            addLocationToHistory(loc.lat, loc.lng)
                                                            searchResults = emptyList()
                                                            searchQuery = ""
                                                            Toast.makeText(context, "Terbang ke: ${loc.label}", Toast.LENGTH_SHORT).show()
                                                        }
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.LocationOn, "Pin", tint = CyberTheme.PrimaryAccent, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        val parts = loc.label.split(",")
                                                        val title = parts.firstOrNull()?.trim() ?: "Lokasi"
                                                        val subtitle = parts.drop(1).joinToString(", ").trim()
                                                        Text(
                                                            text = title,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = CyberTheme.TextPrimary,
                                                            maxLines = 1,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                        )
                                                        if (subtitle.isNotEmpty()) {
                                                            Text(
                                                                text = subtitle,
                                                                fontSize = 9.sp,
                                                                color = CyberTheme.TextSecondary,
                                                                maxLines = 2,
                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. GO TO COORDINATES SECTION (Manual input)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = bgCard),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, borderStrokeColor),
                            modifier = Modifier.fillMaxWidth().animateContentSize()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "INPUT MOCK GPS COORDINATES",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberTheme.TextSecondary,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = latInput,
                                        onValueChange = { latInput = it },
                                        label = { Text("Latitude", fontSize = 9.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = CyberTheme.TextPrimary,
                                            unfocusedTextColor = CyberTheme.TextPrimary,
                                            focusedBorderColor = CyberTheme.PrimaryAccent,
                                            unfocusedBorderColor = borderStrokeColor,
                                            focusedLabelColor = CyberTheme.PrimaryAccent,
                                            unfocusedLabelColor = CyberTheme.TextSecondary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("lat_input_field"),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = lngInput,
                                        onValueChange = { lngInput = it },
                                        label = { Text("Longitude", fontSize = 9.sp) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = CyberTheme.TextPrimary,
                                            unfocusedTextColor = CyberTheme.TextPrimary,
                                            focusedBorderColor = CyberTheme.PrimaryAccent,
                                            unfocusedBorderColor = borderStrokeColor,
                                            focusedLabelColor = CyberTheme.PrimaryAccent,
                                            unfocusedLabelColor = CyberTheme.TextSecondary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("lng_input_field"),
                                        singleLine = true
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        val latDouble = latInput.toDoubleOrNull()
                                        val lngDouble = lngInput.toDoubleOrNull()
                                        if (latDouble != null && lngDouble != null) {
                                            if (latDouble >= -90.0 && latDouble <= 90.0 && lngDouble >= -180.0 && lngDouble <= 180.0) {
                                                MockLocationManager.latitude.value = latDouble
                                                MockLocationManager.longitude.value = lngDouble
                                                addLocationToHistory(latDouble, lngDouble)
                                                
                                                maplibreController.updateActivePin(latDouble, lngDouble)
                                                maplibreController.animateCameraTo(latDouble, lngDouble, 15.0)
                                                
                                                // Auto-update search query & reverse geocode
                                                searchQuery = String.format(java.util.Locale.US, "%.6f, %.6f", latDouble, lngDouble)
                                                performReverseGeocode(latDouble, lngDouble)
                                                
                                                Toast.makeText(context, "Location Simulated Successfully!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Invalid range: Lat (-90 to 90), Lng (-180 to 180)", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Please enter valid decimal coordinates!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberTheme.PrimaryAccent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .testTag("go_coordinates_button")
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MyLocation,
                                            contentDescription = "Set Location",
                                            tint = textOnPrimaryAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Set Location", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textOnPrimaryAccent)
                                    }
                                }
                            }
                        }

                        // 3. STATUS & QUICK SPOOF SWITCH CARD
                        Card(
                            colors = CardDefaults.cardColors(containerColor = bgCard),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, borderStrokeColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("STATUS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberTheme.TextSecondary)
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isServiceActive) CyberTheme.SuccessGreen.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f),
                                                CircleShape
                                            )
                                            .border(
                                                1.dp,
                                                if (isServiceActive) CyberTheme.SuccessGreen else Color.Red,
                                                CircleShape
                                            )
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isServiceActive) "AKTIF" else "MATI",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isServiceActive) CyberTheme.SuccessGreen else Color.Red
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Large Service Toggle Button
                                Button(
                                    onClick = { toggleService() },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isServiceActive) CyberTheme.ErrorRed else CyberTheme.SuccessGreen
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .testTag("spoof_toggle_button")
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isServiceActive) Icons.Default.LocationOff else Icons.Default.LocationOn,
                                            contentDescription = "Trigger Service",
                                            tint = if (isServiceActive) Color.White else textOnSuccessGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isServiceActive) "HENTIKAN SPOOF" else "MULAI SPOOFING",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (isServiceActive) Color.White else textOnSuccessGreen
                                        )
                                    }
                                }
                            }
                        }

                        // 4. MAP STYLE SELECTOR
                        Card(
                            colors = CardDefaults.cardColors(containerColor = bgCard),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, borderStrokeColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("TAMPILAN PETA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberTheme.TextSecondary)
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                val styles = listOf(
                                    "google_satellite" to "Satelit",
                                    "google_road" to "Standard",
                                    "carto_dark" to "Gelap"
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    styles.forEach { (styleId, label) ->
                                        val active = selectedTileStyle == styleId
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (active) CyberTheme.PrimaryAccent else bgCardInner)
                                                .clickable { selectedTileStyle = styleId }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (active) textOnPrimaryAccent else CyberTheme.TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                    // BATTERY OPTIMIZATION CONTROLLER CARD
                    Card(
                        colors = CardDefaults.cardColors(containerColor = bgCard),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isBatteryOptimizing) CyberTheme.ErrorRed.copy(alpha = 0.4f) else CyberTheme.SuccessGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("PENGATURAN BATERAI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberTheme.TextSecondary)
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (!isBatteryOptimizing) CyberTheme.SuccessGreen.copy(alpha = 0.15f) else CyberTheme.ErrorRed.copy(alpha = 0.15f),
                                            CircleShape
                                        )
                                        .border(
                                            1.dp,
                                            if (!isBatteryOptimizing) CyberTheme.SuccessGreen else CyberTheme.ErrorRed,
                                            CircleShape
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (!isBatteryOptimizing) "SUDAH DIKECUALIKAN" else "BELUM DIKECUALIKAN",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (!isBatteryOptimizing) CyberTheme.SuccessGreen else CyberTheme.ErrorRed
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Mencegah sistem mematikan simulasi GPS di latar belakang saat layar mati atau HP hemat baterai.",
                                fontSize = 9.sp,
                                lineHeight = 13.sp,
                                color = CyberTheme.TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        try {
                                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = android.net.Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            try {
                                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                context.startActivity(intent)
                                                Toast.makeText(context, "Cari 'Mandala Net' dan atur ke 'Jangan Batasi' / 'Don't Optimize'", Toast.LENGTH_LONG).show()
                                            } catch (ex: Exception) {
                                                Toast.makeText(context, "Gagal membuka pengaturan baterai", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Versi Android tidak memerlukan pengecualian", Toast.LENGTH_SHORT).show()
                                    }
                                    // Refresh status
                                    checkBatteryOptimizations()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isBatteryOptimizing) CyberTheme.ErrorRed else CyberTheme.SuccessGreen
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isBatteryOptimizing) Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = "Battery Config",
                                        tint = if (isBatteryOptimizing) Color.White else textOnSuccessGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isBatteryOptimizing) "BEBASKAN BATASAN BATERAI" else "BATERAI SUDAH OPTIMAL",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBatteryOptimizing) Color.White else textOnSuccessGreen
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    // DEVELOPER INSTRUCTIONS CARD (CONTEXT-AWARE)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMockAllowed) {
                                CyberTheme.SuccessGreen.copy(alpha = 0.05f)
                            } else {
                                CyberTheme.ErrorRed.copy(alpha = 0.05f)
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isMockAllowed) CyberTheme.SuccessGreen.copy(alpha = 0.4f) else CyberTheme.ErrorRed.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (isMockAllowed) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = CyberTheme.SuccessGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "MOCK ACCESS ACTIVE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = CyberTheme.SuccessGreen
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Aplikasi lokasi palsu terdeteksi aktif di Opsi Developer. Mandala Net siap mensimulasikan lokasi dengan performa maksimal!",
                                    fontSize = 9.sp,
                                    lineHeight = 13.sp,
                                    color = CyberTheme.TextSecondary
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Warning",
                                        tint = CyberTheme.ErrorRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "SETUP OPSI DEVELOPER (BLOCKED)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = CyberTheme.ErrorRed
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                val isEmulatorDevice = remember(context) { isEmulator(context) }
                                val stepsText = remember(context) {
                                    if (isEmulatorDevice) {
                                        "Petunjuk Emulator:\n1. Klik tombol 'Buka Opsi Developer' di bawah.\n2. Emulator akan mencoba menggulir & menyoroti menu 'Pilih aplikasi lokasi palsu' (Mock Location App) secara otomatis.\n3. Cukup ketuk menu tersebut lalu pilih 'Mandala Net'."
                                    } else {
                                        getBrandSpecificInstructions()
                                    }
                                }
                                Text(
                                    text = stepsText,
                                    fontSize = 9.sp,
                                    lineHeight = 13.sp,
                                    color = CyberTheme.TextSecondary
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        openDeveloperSettingsCompat(context)
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = bgCardInner),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                ) {
                                    Text("Buka Opsi Developer", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberTheme.PrimaryAccent)
                                }
                            }
                        }
                    }

                    } // End of Tab 0

                    if (selectedSidebarTab == 2) {
                        // RECENT LOCATIONS SECTION
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "RECENT LOCATIONS (LAST 10)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberTheme.TextSecondary
                                )
                                if (historyList.isNotEmpty()) {
                                    Text(
                                        "Hapus Semua",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyberTheme.ErrorRed,
                                        modifier = Modifier.clickable {
                                            historyList.clear()
                                            historyPrefs.edit().remove("history_items").apply()
                                            Toast.makeText(context, "Riwayat dihapus", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            if (historyList.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .background(bgCard, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Belum ada riwayat lokasi",
                                        fontSize = 11.sp,
                                        color = CyberTheme.TextSecondary
                                    )
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    historyList.forEachIndexed { idx, item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(bgCard)
                                                .clickable {
                                                    MockLocationManager.latitude.value = item.lat
                                                    MockLocationManager.longitude.value = item.lng
                                                    maplibreController.animateCameraTo(item.lat, item.lng)
                                                    Toast.makeText(
                                                        context,
                                                        "Terbang ke: ${String.format("%.4f", item.lat)}, ${String.format("%.4f", item.lng)}",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .background(CyberTheme.PrimaryAccent.copy(alpha = 0.1f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.History,
                                                        contentDescription = "History Icon",
                                                        tint = CyberTheme.PrimaryAccent,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = "Lokasi ${idx + 1} (${formatTime(item.timestamp)})",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = CyberTheme.TextPrimary
                                                    )
                                                    Text(
                                                        text = "${String.format("%.6f", item.lat)}, ${String.format("%.6f", item.lng)}",
                                                        fontSize = 9.sp,
                                                        color = CyberTheme.TextSecondary
                                                    )
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val isBookmarked = bookmarks.any { b ->
                                                    Math.abs(b.lat - item.lat) < 0.0001 && Math.abs(b.lng - item.lng) < 0.0001
                                                }

                                                if (!isBookmarked) {
                                                    IconButton(
                                                        onClick = {
                                                            bookmarkNameInput = "Simpanan ${bookmarks.size + 1}"
                                                            MockLocationManager.latitude.value = item.lat
                                                            MockLocationManager.longitude.value = item.lng
                                                            maplibreController.animateCameraTo(item.lat, item.lng)
                                                            showSaveBookmarkDialog = true
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.BookmarkAdd,
                                                            contentDescription = "Simpan ke Favorit",
                                                            tint = CyberTheme.TextSecondary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Bookmark,
                                                        contentDescription = "Tersimpan",
                                                        tint = CyberTheme.PrimaryAccent,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // FAVORIT LOKASI / SAVED PLACES
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("FAVORIT LOKASI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberTheme.TextSecondary)
                                Text(
                                    "+ Simpan Aktif",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberTheme.PrimaryAccent,
                                    modifier = Modifier.clickable {
                                        bookmarkNameInput = "Lokasi Saya ${bookmarks.size + 1}"
                                        showSaveBookmarkDialog = true
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            if (bookmarks.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(70.dp)
                                        .background(bgCard, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Belum ada lokasi tersimpan", fontSize = 11.sp, color = CyberTheme.TextSecondary)
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    bookmarks.forEach { b ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(bgCard)
                                                .clickable {
                                                    MockLocationManager.latitude.value = b.lat
                                                    MockLocationManager.longitude.value = b.lng
                                                    maplibreController.animateCameraTo(b.lat, b.lng)
                                                    addLocationToHistory(b.lat, b.lng)
                                                    Toast.makeText(context, "Terbang ke: ${b.label}", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Bookmark,
                                                    contentDescription = "Bookmark",
                                                    tint = CyberTheme.PrimaryAccent,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(b.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberTheme.TextPrimary, maxLines = 1)
                                                    Text("${String.format("%.5f", b.lat)}, ${String.format("%.5f", b.lng)}", fontSize = 9.sp, color = CyberTheme.TextSecondary)
                                                }
                                            }
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Hapus",
                                                tint = CyberTheme.ErrorRed.copy(alpha = 0.8f),
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clickable {
                                                        sharedPrefs.edit().remove(b.label).apply()
                                                        bookmarks.remove(b)
                                                        Toast.makeText(context, "Favorit dihapus", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .padding(4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (selectedSidebarTab == 1) {
                        // ROUTE GPS WAYPOINTS
                        Column(modifier = Modifier.fillMaxWidth()) {
                            
                            // JOYSTICK & SPEED CONTROL CARD
                            Card(
                                colors = CardDefaults.cardColors(containerColor = bgCard),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, borderStrokeColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("JOYSTICK LAYAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberTheme.TextSecondary)
                                        Switch(
                                            checked = showJoystick,
                                            onCheckedChange = { MockLocationManager.showJoystick.value = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = textOnPrimaryAccent,
                                                checkedTrackColor = CyberTheme.PrimaryAccent,
                                                uncheckedThumbColor = CyberTheme.TextSecondary,
                                                uncheckedTrackColor = bgCardInner
                                            ),
                                            modifier = Modifier.scale(0.8f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("KECEPATAN JOYSTICK", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberTheme.TextSecondary)
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Speed selection chips
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf(
                                            Triple("Motor", 30.0, "🏍️"),
                                            Triple("Mobil", 60.0, "🚗")
                                        ).forEach { (label, value, emoji) ->
                                            val active = currentSpeed == value
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (active) CyberTheme.PrimaryAccent else bgCardInner)
                                                    .clickable { MockLocationManager.speedKmh.value = value }
                                                    .padding(vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$emoji $label",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (active) textOnPrimaryAccent else CyberTheme.TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text("RUTE PERJALANAN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberTheme.TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))

                            Card(
                                colors = CardDefaults.cardColors(containerColor = bgCard),
                                border = BorderStroke(1.dp, borderStrokeColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Titik Waypoint", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberTheme.TextPrimary)
                                        Text(
                                            "+ Tambah Titik Saat Ini",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberTheme.PrimaryAccent,
                                            modifier = Modifier.clickable {
                                                MockLocationManager.userWaypoints.value = MockLocationManager.userWaypoints.value + LocationBookmark("WP ${routeWaypoints.size + 1}", MockLocationManager.latitude.value, MockLocationManager.longitude.value)
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (routeWaypoints.isEmpty()) {
                                        Text("Belum ada waypoint. Tambahkan minimal 2 titik untuk simulasi rute.", fontSize = 9.sp, color = CyberTheme.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            routeWaypoints.forEachIndexed { idx, wp ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().background(bgCardInner, RoundedCornerShape(6.dp)).padding(8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("${idx + 1}. ${String.format("%.4f", wp.lat)}, ${String.format("%.4f", wp.lng)}", fontSize = 9.sp, color = CyberTheme.TextPrimary)
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Hapus",
                                                        tint = CyberTheme.ErrorRed,
                                                        modifier = Modifier.size(14.dp).clickable {
                                                            val list = MockLocationManager.userWaypoints.value.toMutableList()
                                                            list.removeAt(idx)
                                                            MockLocationManager.userWaypoints.value = list
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Kecepatan Simulasi: ${simulationSpeed.toInt()} km/h", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CyberTheme.TextPrimary)
                                    androidx.compose.material3.Slider(
                                        value = simulationSpeed,
                                        onValueChange = { simulationSpeed = it },
                                        valueRange = 5f..150f,
                                        colors = androidx.compose.material3.SliderDefaults.colors(
                                            thumbColor = CyberTheme.PrimaryAccent,
                                            activeTrackColor = CyberTheme.PrimaryAccent,
                                            inactiveTrackColor = borderStrokeColor
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { 
                                            if (!isSimulatingRoute && routeWaypoints.size >= 2) {
                                                if (!isServiceActive) {
                                                    Toast.makeText(context, "Aktifkan layanan mock location dulu!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    isSimulatingRoute = true
                                                    Toast.makeText(context, "Simulasi Rute Dimulai", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                isSimulatingRoute = false
                                                MockLocationManager.isSimulatingRoute.value = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSimulatingRoute) CyberTheme.ErrorRed else CyberTheme.PrimaryAccent
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().height(40.dp)
                                    ) {
                                        Text(if (isSimulatingRoute) "Hentikan Simulasi" else "Mulai Simulasi Rute", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = textOnPrimaryAccent)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Preset Section
                            Text("PRESET RUTE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberTheme.TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = bgCard),
                                border = BorderStroke(1.dp, borderStrokeColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { showLoadPresetDialog = true },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Muat Preset", fontSize = 10.sp, color = CyberTheme.PrimaryAccent)
                                        }
                                        TextButton(
                                            onClick = {
                                                if (routeWaypoints.size >= 2) {
                                                    presetNameInput = ""
                                                    showSavePresetDialog = true
                                                } else {
                                                    Toast.makeText(context, "Minimal 2 waypoint untuk disimpan", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Simpan Saat Ini", fontSize = 10.sp, color = CyberTheme.PrimaryAccent)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            VerticalDivider(
                color = CyberTheme.PrimaryAccent.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxHeight()
            )
        }
    }

        // MAIN MAP INTERACTIVE AREA
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            
            // NATIVE MAPLIBRE MAP
            val mapView = rememberMapViewWithLifecycle()
            
            LaunchedEffect(mapView) {
                mapView.getMapAsync { map ->
                    map.uiSettings.isAttributionEnabled = false
                    map.uiSettings.isLogoEnabled = false
                    
                    map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                        org.maplibre.android.geometry.LatLng(currentLat, currentLng),
                        15.0
                    ))
                    
                    maplibreController.init(map, context, selectedTileStyle)
                    
                    map.addOnMapClickListener { latLng ->
                        MockLocationManager.isSimulatingRoute.value = false
                        isSimulatingRoute = false
                        MockLocationManager.latitude.value = latLng.latitude
                        MockLocationManager.longitude.value = latLng.longitude
                        searchQuery = String.format(java.util.Locale.US, "%.6f, %.6f", latLng.latitude, latLng.longitude)
                        performReverseGeocode(latLng.latitude, latLng.longitude)
                        if (isServiceActive) {
                            addLocationToHistory(latLng.latitude, latLng.longitude)
                        }
                        true
                    }
                    
                    map.addOnMapLongClickListener { latLng ->
                        MockLocationManager.isSimulatingRoute.value = false
                        isSimulatingRoute = false
                        MockLocationManager.latitude.value = latLng.latitude
                        MockLocationManager.longitude.value = latLng.longitude
                        searchQuery = String.format(java.util.Locale.US, "%.6f, %.6f", latLng.latitude, latLng.longitude)
                        performReverseGeocode(latLng.latitude, latLng.longitude)
                        if (isServiceActive) {
                            addLocationToHistory(latLng.latitude, latLng.longitude)
                        }
                        true
                    }
                    
                    map.addOnCameraMoveListener {
                        val center = map.cameraPosition.target
                        if (center != null) {
                            mapCenterLat = center.latitude
                            mapCenterLng = center.longitude
                        }
                    }
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapView },
                update = { _ -> }
            )

            // PERSISTENT SIMULATION ROUTE METRICS OVERLAY
            AnimatedVisibility(
                visible = isSimulatingRoute && routeTotalDistanceMeters > 0,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .padding(horizontal = 60.dp)
                    .zIndex(6f)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = bgFloatingPill),
                    border = BorderStroke(1.5.dp, CyberTheme.PrimaryAccent.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .widthIn(max = 450.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                        .testTag("route_simulation_metrics_overlay")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Title row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                    contentDescription = "Simulasi Rute",
                                    tint = CyberTheme.PrimaryAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Simulasi Rute Aktif",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberTheme.TextPrimary,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            
                            // Speed Badge
                            Box(
                                modifier = Modifier
                                    .background(CyberTheme.PrimaryAccent.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .border(1.dp, CyberTheme.PrimaryAccent.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${simulationSpeed.toInt()} km/h",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CyberTheme.PrimaryAccent
                                )
                            }
                        }
                        
                        HorizontalDivider(color = borderStrokeColor.copy(alpha = 0.5f), thickness = 1.dp)
                        
                        // Metrics row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Distance Column
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "JARAK RUTE",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CyberTheme.TextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val totalDistStr = if (routeTotalDistanceMeters >= 1000f) {
                                    String.format(java.util.Locale.US, "%.2f km", routeTotalDistanceMeters / 1000f)
                                } else {
                                    "${routeTotalDistanceMeters.toInt()} m"
                                }
                                Text(
                                    text = totalDistStr,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberTheme.TextPrimary
                                )
                            }
                            
                            VerticalDivider(color = borderStrokeColor.copy(alpha = 0.5f), modifier = Modifier.height(24.dp))
                            
                            // Duration Column
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "TOTAL DURASI",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CyberTheme.TextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val hours = routeTotalDurationSeconds / 3600
                                val minutes = (routeTotalDurationSeconds % 3600) / 60
                                val seconds = routeTotalDurationSeconds % 60
                                val durationStr = if (hours > 0) {
                                    "${hours}j ${minutes}m"
                                } else {
                                    "${minutes}m ${seconds}s"
                                }
                                Text(
                                    text = durationStr,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberTheme.TextPrimary
                                )
                            }
                            
                            VerticalDivider(color = borderStrokeColor.copy(alpha = 0.5f), modifier = Modifier.height(24.dp))
                            
                            // Remaining Time Column
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "SISA WAKTU",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CyberTheme.TextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val remHours = routeRemainingSeconds / 3600
                                val remMinutes = (routeRemainingSeconds % 3600) / 60
                                val remSeconds = routeRemainingSeconds % 60
                                val remainingStr = if (remHours > 0) {
                                    "${remHours}j ${remMinutes}m"
                                } else if (remMinutes > 0) {
                                    "${remMinutes}m ${remSeconds}s"
                                } else {
                                    "${remSeconds}s"
                                }
                                Text(
                                    text = remainingStr,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CyberTheme.SuccessGreen
                                )
                            }
                        }
                        
                        // Remaining Distance info and progress bar
                        val progress = if (routeTotalDistanceMeters > 0) {
                            ((routeTotalDistanceMeters - routeRemainingDistanceMeters) / routeTotalDistanceMeters).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val remDistStr = if (routeRemainingDistanceMeters >= 1000f) {
                                    String.format(java.util.Locale.US, "%.2f km", routeRemainingDistanceMeters / 1000f)
                                } else {
                                    "${routeRemainingDistanceMeters.toInt()} m"
                                }
                                Text(
                                    text = "Sisa Jarak: $remDistStr",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = CyberTheme.TextSecondary
                                )
                                
                                Text(
                                    text = "${(progress * 100).toInt()}% Selesai",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberTheme.PrimaryAccent
                                )
                            }
                            
                            LinearProgressIndicator(
                                progress = { progress },
                                color = CyberTheme.PrimaryAccent,
                                trackColor = borderStrokeColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }
            }

            // FLOATING TOP-RIGHT ACTIONS (Refresh Map & Center GPS)
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        maplibreController.setStyle(selectedTileStyle)
                        Toast.makeText(context, "Memuat ulang peta...", Toast.LENGTH_SHORT).show()
                    },
                    containerColor = bgFloatingPill,
                    contentColor = CyberTheme.PrimaryAccent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .size(46.dp)
                        .border(1.dp, borderStrokeColor, RoundedCornerShape(12.dp))
                        .testTag("refresh_map_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Muat Ulang Peta",
                        modifier = Modifier.size(20.dp)
                    )
                }

                FloatingActionButton(
                    onClick = {
                        centerOnHardwareLocation()
                    },
                    containerColor = bgFloatingPill,
                    contentColor = CyberTheme.PrimaryAccent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .size(46.dp)
                        .border(1.dp, borderStrokeColor, RoundedCornerShape(12.dp))
                        .testTag("center_hardware_location_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Pusat Lokasi GPS",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // FLOATING SIDEBAR TOGGLE BUTTON (Shows when sidebar is collapsed)
            if (!isSidebarExpanded) {
                FloatingActionButton(
                    onClick = { isSidebarExpanded = true },
                    containerColor = CyberTheme.PrimaryAccent,
                    contentColor = textOnPrimaryAccent,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Buka Menu",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // DYNAMIC MAP CENTER COORDINATES STATUS BAR
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = bgFloatingPill),
                border = BorderStroke(1.dp, borderStrokeColor),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 92.dp)
                    .wrapContentWidth()
                    .testTag("map_center_status_bar")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Pusat Peta",
                        tint = CyberTheme.PrimaryAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Pusat Peta: ${String.format(java.util.Locale.US, "%.6f", mapCenterLat)}, ${String.format(java.util.Locale.US, "%.6f", mapCenterLng)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = CyberTheme.TextPrimary
                    )
                }
            }

            // FLOATING ACTIVE TELEMETRY PILL & BOOKMARK ACTION
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Main telemetry info card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = bgFloatingPill),
                    border = BorderStroke(1.dp, borderStrokeColor),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(if (isServiceActive) CyberTheme.SuccessGreen else Color.Red, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isServiceActive) "MOCK AKTIF" else "MOCK STANDBY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isServiceActive) CyberTheme.SuccessGreen else CyberTheme.TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${String.format("%.5f", currentLat)}, ${String.format("%.5f", currentLng)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberTheme.PrimaryAccent
                            )
                        }

                        // Tap map instructions helper
                        Text(
                            text = "Geser pin / ketuk\npeta untuk memilih",
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CyberTheme.TextSecondary,
                            textAlign = TextAlign.End
                        )
                    }
                }

                // Quick Save Bookmark FAB
                FloatingActionButton(
                    onClick = {
                        bookmarkNameInput = "Lokasi Saya ${bookmarks.size + 1}"
                        showSaveBookmarkDialog = true
                    },
                    containerColor = bgFloatingPill,
                    contentColor = CyberTheme.PrimaryAccent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .size(46.dp)
                        .border(1.dp, borderStrokeColor, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = "Simpan Favorit",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
