# SnapStamp (拍邮)

![Logo](logo.jpg)

**[English](#english) | [中文](#中文)**

---

<a name="中文"></a>

## 🇨🇳 中文说明

**SnapStamp** 是一款基于 Android 平台，结合现代 Jetpack Compose 技术构建的趣味摄影应用。它能将你拍摄的照片自动处理为带有“邮票齿孔”边框的艺术作品，并支持添加地理位置及个性化备注。

### 核心功能

* **拍邮体验**：使用相机捕获照片，自动裁切并生成经典的邮票边框样式。
* **地理坐标**：自动获取拍摄时的经纬度，并将位置信息嵌入照片的 Exif 数据中。
* **集邮册**：在库中浏览、长按删除或者分享你的作品；点击邮票即可翻转查看背后的拍摄信息及备注。
* **灵活保存**：支持保存带边框的完整艺术照，或纯净的无边框原图。
* **现代 UI**：基于 Jetpack Compose 构建，拥有流畅的动画效果和美观的交互界面。

### 技术栈

* **Kotlin & Jetpack Compose**：声明式 UI 构建。
* **CameraX**：提供高性能、易用的相机操作 API。
* **Coil**：高效的图片加载与处理库。
* **ExifInterface**：用于读取与写入照片的元数据（地理位置、备注等）。
* **FusedLocationProvider**：Google Location API 获取精准拍摄地点。

---

<a name="english"></a>

## 🇺🇸 English Description

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
