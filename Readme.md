# SnapStamp

**SnapStamp** is a creative photography application for Android, built with modern Jetpack Compose. It automatically transforms your photos into stamp-like art pieces with perforated borders and allows you to add geographical locations and personalized notes.

### Key Features

* **Stamp Generation**: Capture photos with the camera, automatically crop them, and generate classic stamp-style frames.
* **Geotagging**: Automatically retrieves GPS coordinates at the time of shooting and embeds the location info into the photo's Exif data.
* **Stamp Album**: Browse, delete, and share your collection. Flip the stamp to view the detailed capture info and your personal notes.
* **Flexible Export**: Choose to save the stylized stamp with its perforated border or the clean, original photo.
* **Modern UI**: Built with Jetpack Compose, offering smooth animations and an intuitive, elegant user experience.

### Tech Stack

* **Kotlin & Jetpack Compose**：Declarative UI development.
* **CameraX**：High-performance, easy-to-use camera APIs.
* **Coil**：Efficient image loading and processing.
* **ExifInterface**：Handling image metadata (GPS, user comments, etc.).
* **FusedLocationProvider**：Accurate location tracking for your snapshots.
