# SnapStamp

**SnapStamp** is a feature-rich Android photography app that turns ordinary photos into artistic stamp-style keepsakes. Designed with Jetpack Compose and CameraX, the app combines easy photo capture, automatic location tagging, and a polished gallery experience.

## What SnapStamp Does

* Capture photos directly in the app using the camera.
* Automatically crop images into a stamp-like square layout.
* Add a perforated border and classic stamp styling to each photo.
* Embed location data (GPS coordinates and address) into the image metadata.
* Add a personal note or caption to each stamp.
* Save both the decorated stamp version and the original clean photo.
* Browse saved stamps in a gallery with flip-card detail views.
* Share or delete saved stamps from your collection.

## Key Features

* **Stamp Creation**: Take a photo and generate a stylized stamp automatically, complete with crisp white borders and perforated edges.
* **Location Metadata**: Use FusedLocationProvider to capture GPS coordinates at the moment of shooting and store that information in the image Exif data.
* **Personal Notes**: Attach custom text notes or captions to each photo stamp for memory keeping.
* **Dual Export Options**: Choose to export the finished stamp image or the original photo without decoration.
* **Gallery Management**: View saved stamps in a custom album, flip each stamp to reveal details, and manage your collection.
* **Smooth UI**: Built with Jetpack Compose for fluid transitions, clean layouts, and modern Android styling.

## Tech Stack

* **Kotlin**: Core application language.
* **Jetpack Compose**: Declarative UI implementation.
* **CameraX**: Simplified camera capture and preview.
* **Coil**: Efficient image loading, processing, and previews.
* **ExifInterface**: Reading and writing Exif metadata.
* **FusedLocationProvider**: Reliable location capture for geotagging.

## How to Use

1. Open the app and grant camera and location permissions.
2. Capture a new photo from the app camera screen.
3. Let SnapStamp automatically crop the image and apply a stamp frame.
4. Add a location note or personal caption if desired.
5. Save the stamp to your album.
6. Browse saved stamps in the gallery and share or delete as needed.

## Project Structure

* `app/src/main/java/` — Application code and Compose screens.
* `app/src/main/res/` — Layout resources, icons, and images.
* `app/build.gradle.kts` — App module build configuration.
* `build.gradle.kts` — Project-level Gradle configuration.

## Notes

This app is intended for creative photo journaling and travel memory capture. It works best on devices with CameraX support and location services enabled.
