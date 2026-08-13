package com.example.sls_assistant_pro.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.sls_assistant_pro.data.model.TaskItem
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun OsmMapView(
    driverLat: Double,
    driverLng: Double,
    tasks: List<TaskItem>,
    contactStatusMap: Map<String, String>,
    onDriverLocationUpdated: (Double, Double, Float) -> Unit,
    onTaskSelected: (TaskItem) -> Unit,
    onClusterSelected: (List<TaskItem>) -> Unit,
    recenterTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var driverBearing by remember { mutableFloatStateOf(0f) }
    var currentLat by remember { mutableDoubleStateOf(driverLat) }
    var currentLng by remember { mutableDoubleStateOf(driverLng) }

    val mapView = remember {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            if (driverLat != 0.0 && driverLng != 0.0) {
                controller.setCenter(GeoPoint(driverLat, driverLng))
            }
        }
    }

    // Recenter when requested by user
    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0) {
            val targetLat = if (currentLat != 0.0) currentLat else driverLat
            val targetLng = if (currentLng != 0.0) currentLng else driverLng
            if (targetLat != 0.0 && targetLng != 0.0) {
                mapView.controller.animateTo(GeoPoint(targetLat, targetLng))
                mapView.controller.setZoom(16.5)
            }
        }
    }

    // Aggressive Current Location acquisition upon opening
    DisposableEffect(context) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        var locationListener: LocationListener? = null

        try {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

            if (hasFine || hasCoarse) {
                // 1. Immediately inspect last known location from all available providers
                val providers = listOfNotNull(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                )

                var bestLocation: Location? = null
                for (p in providers) {
                    try {
                        val loc = locationManager?.getLastKnownLocation(p)
                        if (loc != null) {
                            if (bestLocation == null || loc.accuracy < bestLocation.accuracy || loc.time > bestLocation.time) {
                                bestLocation = loc
                            }
                        }
                    } catch (ignored: Exception) {}
                }

                if (bestLocation != null) {
                    currentLat = bestLocation.latitude
                    currentLng = bestLocation.longitude
                    driverBearing = bestLocation.bearing
                    onDriverLocationUpdated(bestLocation.latitude, bestLocation.longitude, bestLocation.bearing)
                    mapView.controller.setZoom(16.0)
                    mapView.controller.setCenter(GeoPoint(bestLocation.latitude, bestLocation.longitude))
                }

                // 2. Setup real-time listener for current position
                locationListener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        currentLat = location.latitude
                        currentLng = location.longitude
                        driverBearing = location.bearing
                        onDriverLocationUpdated(location.latitude, location.longitude, location.bearing)
                    }
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }

                if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        2000L,
                        3f,
                        locationListener
                    )
                }
                if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        2000L,
                        3f,
                        locationListener
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("OsmMapView", "Location setup error: ${e.message}")
        }

        onDispose {
            locationListener?.let {
                try {
                    locationManager?.removeUpdates(it)
                } catch (e: Exception) {
                    Log.e("OsmMapView", "Error removing location updates: ${e.message}")
                }
            }
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlays.clear()

            // 1. Driver Location Marker (Current Location Arrow)
            val displayLat = if (currentLat != 0.0) currentLat else driverLat
            val displayLng = if (currentLng != 0.0) currentLng else driverLng

            if (displayLat != 0.0 && displayLng != 0.0) {
                val driverPoint = GeoPoint(displayLat, displayLng)
                val driverIcon = createDriverMarkerIcon(context, driverBearing)
                val driverMarker = Marker(map).apply {
                    position = driverPoint
                    title = "موقعي الحالي"
                    icon = driverIcon
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                map.overlays.add(driverMarker)
            }

            // 2. Group / Cluster Tasks by Coordinates (~10 meters precision)
            val navigableTasks = tasks.filter { it.hasNavigableLocation }
            val clusteredGroups = navigableTasks.groupBy { task ->
                val roundedLat = Math.round(task.latitude!! * 10000) / 10000.0
                val roundedLng = Math.round(task.longitude!! * 10000) / 10000.0
                Pair(roundedLat, roundedLng)
            }

            for ((_, groupTasks) in clusteredGroups) {
                if (groupTasks.size == 1) {
                    val task = groupTasks.first()
                    val point = GeoPoint(task.latitude!!, task.longitude!!)
                    val contactStatus = contactStatusMap[task.displayReference] ?: "none"
                    val isCod = task.isCashOnDelivery
                    val markerIcon = createShipmentMarkerIcon(context, contactStatus, isCod, task.codAmount)

                    val paymentLabel = if (isCod) "💵 كاش (${task.codAmount ?: 0.0} ريال)" else "✓ مدفوع"
                    val contactLabel = when (contactStatus) {
                        "answered" -> "العميل أجاب"
                        "no_answer" -> "العميل لم يجب"
                        else -> "لم يتم التواصل"
                    }

                    val marker = Marker(map).apply {
                        position = point
                        title = "${task.displayReference} - ${task.customerName}"
                        snippet = "المتجر: ${task.displayStoreName}\nحالة الدفع: $paymentLabel\nحالة التواصل: $contactLabel"
                        icon = markerIcon
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ ->
                            onTaskSelected(task)
                            true
                        }
                    }
                    map.overlays.add(marker)
                } else {
                    // Cluster Marker
                    val firstTask = groupTasks.first()
                    val point = GeoPoint(firstTask.latitude!!, firstTask.longitude!!)
                    val clusterIcon = createClusterMarkerIcon(context, groupTasks.size)

                    val marker = Marker(map).apply {
                        position = point
                        title = "تجميع: ${groupTasks.size} شحنات في هذا الموقع"
                        icon = clusterIcon
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ ->
                            onClusterSelected(groupTasks)
                            true
                        }
                    }
                    map.overlays.add(marker)
                }
            }
            map.invalidate()
        }
    )
}

private fun createClusterMarkerIcon(context: Context, count: Int): Drawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (38 * density).toInt()

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0D47A1.toInt() // Dark Blue Cluster
        style = Paint.Style.FILL
    }

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
    }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 14 * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val radius = 16 * density

    canvas.drawCircle(cx, cy, radius, bgPaint)
    canvas.drawCircle(cx, cy, radius, borderPaint)

    val yPos = cy - ((textPaint.descent() + textPaint.ascent()) / 2)
    canvas.drawText(count.toString(), cx, yPos, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}

private fun createDriverMarkerIcon(context: Context, bearing: Float = 0f): Drawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (32 * density).toInt()

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1976D2.toInt() // Navigation Blue
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
    }

    val cx = sizePx / 2f
    val cy = sizePx / 2f

    // Draw Navigation Arrow Path (Chevron)
    val arrowPath = Path().apply {
        moveTo(cx, 4 * density) // Top tip
        lineTo(sizePx - 6 * density, sizePx - 6 * density) // Right bottom
        lineTo(cx, sizePx - 11 * density) // Inner notch
        lineTo(6 * density, sizePx - 6 * density) // Left bottom
        close()
    }

    canvas.save()
    if (bearing != 0f) {
        canvas.rotate(bearing, cx, cy)
    }
    canvas.drawPath(arrowPath, paint)
    canvas.drawPath(arrowPath, borderPaint)
    canvas.restore()

    return BitmapDrawable(context.resources, bitmap)
}

private fun createShipmentMarkerIcon(
    context: Context,
    contactStatus: String,
    isCod: Boolean,
    codAmount: Double?
): Drawable {
    val density = context.resources.displayMetrics.density
    val widthPx = (36 * density).toInt()
    val heightPx = (44 * density).toInt()

    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val pinColor = when (contactStatus) {
        "answered" -> 0xFF2E7D32.toInt()   // Dark Green
        "no_answer" -> 0xFFC62828.toInt()  // Dark Red
        else -> 0xFF1565C0.toInt()         // Dark Blue
    }

    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColor
        style = Paint.Style.FILL
    }

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
    }

    val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isCod) 0xFFE65100.toInt() else 0xFF1B5E20.toInt() // Deep Orange for COD, Deep Green for Prepaid
        style = Paint.Style.FILL
    }

    val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 10 * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    // Main Pin Body (Circle at top + Pointer at bottom)
    val pinRadius = 14 * density
    val pinCx = widthPx / 2f
    val pinCy = 16 * density

    canvas.drawCircle(pinCx, pinCy, pinRadius, pinPaint)
    canvas.drawCircle(pinCx, pinCy, pinRadius, borderPaint)

    // Pointer
    val pointerPath = Path().apply {
        moveTo(pinCx - 8 * density, pinCy + 8 * density)
        lineTo(pinCx + 8 * density, pinCy + 8 * density)
        lineTo(pinCx, heightPx - 2 * density)
        close()
    }
    canvas.drawPath(pointerPath, pinPaint)

    // Small Badge Symbol inside pin circle
    val symbol = if (isCod) "💵" else "✓"
    canvas.drawText(symbol, pinCx, pinCy + (3.5f * density), badgeTextPaint)

    return BitmapDrawable(context.resources, bitmap)
}
