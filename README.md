# 🌐 Mandala Net - Advanced Network Utility & Diagnostics Platform

<p align="center">
  <img src="/app/src/main/res/drawable/mandala.png" alt="Mandala Net Logo" width="120" height="120" style="border-radius: 24%;" />
</p>

<p align="center">
  <strong>Platform utilitas jaringan, diagnostik sinyal, firewall lokal, penganalisis Wi-Fi, dan sistem auto-redialer tercanggih untuk ekosistem Android.</strong>
</p>

<p align="center">
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Language-Kotlin-purple.svg?style=for-the-badge&logo=kotlin" alt="Kotlin" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg?style=for-the-badge&logo=jetpackcompose" alt="Jetpack Compose" /></a>
  <a href="https://developer.android.com/about/versions/12"><img src="https://img.shields.io/badge/Android-8.0%20to%2014-green.svg?style=for-the-badge&logo=android" alt="Android Compatibility" /></a>
  <a href="https://opensource.org/licenses/MIT"><img src="https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge" alt="License MIT" /></a>
</p>

---

## 📌 Daftar Isi
1. [📖 Pendahuluan & Visi](#-pendahuluan--visi)
2. [🏛️ Arsitektur Sistem & Aliran Data](#-arsitektur-sistem--aliran-data)
3. [🚀 Fitur Utama & Detail Implementasi](#-fitur-utama--detail-implementasi)
    - [3.1 Real-time Speed Test](#31-real-time-speed-test)
    - [3.2 Signal Heatmap & Geolocation](#32-signal-heatmap--geolocation)
    - [3.3 Wi-Fi Analyzer & Graph Canvas](#33-wi-fi-analyzer--graph-canvas)
    - [3.4 Local Firewall & Network Shield](#34-local-firewall--network-shield)
    - [3.5 Auto-Redialer & Call Screener](#35-auto-redialer--call-screener)
    - [3.6 Hardware & Device Diagnostics](#36-hardware-device-diagnostics)
    - [3.7 Cyber & AMOLED High-Contrast Themes](#37-cyber--amoled-high-contrast-themes)
4. [🛠️ Stack Teknologi (Tech Stack)](#%EF%B8%8F-stack-teknologi-tech-stack)
5. [🧹 Pencegahan Kebocoran Memori (Zero Memory Leak Architecture)](#-pencegahan-kebocoran-memori-zero-memory-leak-architecture)
6. [📂 Struktur Direktori Proyek](#-struktur-direktori-proyek)
7. [⚙️ Panduan Instalasi & Konfigurasi Pengembangan](#%EF%B8%8F-panduan-instalasi--konfigurasi-pengembangan)
8. [🧪 Pengujian Lokal & Pemeliharaan Kode](#-pengujian-lokal--pemeliharaan-kode)
9. [🤝 Kontribusi & Kode Etik](#-kontribusi--kode-etik)
10. [📄 Lisensi MIT](#-lisensi-mit)

---

## 📖 Pendahuluan & Visi

**Mandala Net** bukan sekadar aplikasi diagnostik jaringan biasa. Ini adalah platform terintegrasi yang dirancang khusus untuk memberikan transparansi penuh atas kondisi jaringan seluler, Wi-Fi, dan status internal perangkat Anda. 

Dengan menggabungkan **Material Design 3 (M3)** yang modern dan sistem tema adaptif berkinerja tinggi, Mandala Net memberikan pengalaman visual yang memanjakan mata sekaligus menyajikan data teknis yang akurat dalam hitungan milidetik. Proyek ini dibangun di atas prinsip utama:
- **Keamanan Tanpa Kompromi (Zero-Trust Local Firewall)**: Mengelola lalu lintas data aplikasi langsung dari perangkat Anda tanpa server perantara pihak ketiga.
- **Efisiensi Energi & Sumber Daya**: Dirancang agar tidak membebani baterai atau memicu kebocoran memori (memory leak) berkat manajemen siklus hidup Android yang sangat ketat.
- **Representasi Visual Kelas Dunia**: Menggunakan grafis berbasis Canvas buatan sendiri untuk visualisasi penganalisis Wi-Fi serta peta interaktif berbasis Leaflet yang efisien.

---

## 🏛️ Arsitektur Sistem & Aliran Data

Mandala Net menerapkan arsitektur **MVVM (Model-View-ViewModel)** yang dipadukan dengan prinsip **Clean Architecture**. Aliran data diproses secara asinkron menggunakan **Kotlin Coroutines** dan **StateFlow** untuk memastikan UI Jetpack Compose tetap responsif dan bebas hambatan (jank-free).

### 📊 Diagram Alir Jaringan & Kontrol Fitur

```
                                  +---------------------------------------+
                                  |         Jetpack Compose UI            |
                                  |  (Screens, Custom Canvas, WebView)    |
                                  +-------------------+-------------------+
                                                      |
                                                      | Observers (StateFlow)
                                                      v
                                  +-------------------+-------------------+
                                  |         ViewViewModels                |
                                  |  (MainViewModel, RedialerViewModel)   |
                                  +-------------------+-------------------+
                                                      |
                         +----------------------------+----------------------------+
                         |                            |                            |
                         v                            v                            v
        +----------------+---------------+  +--------+--------+  +----------------+---------------+
        |       Network Shield Core      |  |  Database (Room)|  |       Hardware/Telemetry       |
        |  (VpnService, Local Firewall,  |  | (Signal log,    |  |  (TelephonyManager, Sensors,   |
        |   App Blocking Control Logs)   |  |  Heatmap GPS)   |  |   BatteryManager, CPU Info)    |
        +----------------+---------------+  +--------+--------+  +----------------+---------------+
                         |                            |                            |
                         v                            v                            v
              [Lalu Lintas Jaringan]           [SQLite Storage]          [Hardware Sensors & OS APIs]
```

Setiap modul beroperasi secara independen dalam lapisan data, dan dikoordinasikan oleh ViewModel masing-masing untuk menjaga pemisahan tanggung jawab (*separation of concerns*).

---

## 🚀 Fitur Utama & Detail Implementasi

### 3.1 Real-time Speed Test
Menguji kecepatan internet Anda sekarang jauh lebih andal dan transparan. Modul **Speed Test** Mandala Net memproses koneksi langsung ke server terdekat menggunakan arsitektur pemrosesan paralel berbasis `OkHttpClient`.

- **Pengukuran Latensi & Jitter**: Melakukan ping berulang secara real-time untuk menghitung rata-rata latensi serta variabilitas ping (*jitter*).
- **Progres Unduh & Unggah Akurat**: Mengunduh dan mengunggah chunk data berukuran optimal secara asinkron menggunakan thread khusus (`Dispatchers.IO`) yang terisolasi untuk menghindari interupsi UI.
- **Gauge Speedometer Kustom**: Komponen melingkar interaktif yang dianimasikan menggunakan Jetpack Compose `animateFloatAsState` untuk transisi jarum penunjuk yang mulus.

| Parameter | Metodologi | Output Visual |
| :--- | :--- | :--- |
| **Ping** | 3x handshake ICMP/HTTP terkontrol | Milidetik (ms) |
| **Jitter** | Deviasi standar dari sampel ping berurutan | Milidetik (ms) |
| **Download** | Stream chunk OkHttp berurutan via IO | Megabit per detik (Mbps) |
| **Upload** | Post payload byte dinamis | Megabit per detik (Mbps) |

---

### 3.2 Signal Heatmap & Geolocation
Fitur ini memetakan kekuatan sinyal operator seluler atau Wi-Fi Anda ke dalam koordinat geografis nyata.

- **Leaflet & WebView Integration**: Menggunakan komponen Android WebView yang dikonfigurasi dengan aman untuk memuat Leaflet JS secara lokal. Ini memberikan rendering peta yang jauh lebih ringan dibanding pustaka native pihak ketiga.
- **Room Database Sync**: Setiap titik sinyal (lintang, bujur, kekuatan sinyal dalam dBm, tipe jaringan) disimpan ke dalam database Room lokal secara real-time.
- **Visualisasi Warna Sinyal**:
  - 🟢 **Hijau (Sangat Baik)**: `-50 dBm` s/d `-80 dBm`
  - 🟡 **Kuning (Sedang)**: `-81 dBm` s/d `-105 dBm`
  - 🔴 **Merah (Buruk/Lemah)**: `-106 dBm` s/d `-121+ dBm`

---

### 3.3 Wi-Fi Analyzer & Graph Canvas
Modul ini mendeteksi titik akses nirkabel (SSID) di sekitar Anda dan memvisualisasikannya ke dalam grafik parabola frekuensi yang elegan menggunakan Jetpack Compose `Canvas`.

- **Dual-Band Support**: Pemisahan tab analisis antara pita frekuensi **2.4 GHz** (saluran 1-14) dan **5 GHz** (saluran 36-165).
- **Kurva Parabola Sinyal (Bell Curve)**: Kurva dihitung secara dinamis menggunakan rumus bezier kuadratik (`cubicTo`) berdasarkan level sinyal dBm yang terdeteksi. Semakin tinggi kurva, semakin kuat sinyal Wi-Fi tersebut.
- **Pendeteksi Tumpang Tindih Saluran**: Membantu pengguna mengidentifikasi saluran Wi-Fi mana yang paling padat sehingga mereka dapat mengoptimalkan pengaturan router mereka untuk menghindari interferensi sinyal.

---

### 3.4 Local Firewall & Network Shield
Melindungi privasi Anda dengan mengontrol akses internet untuk setiap aplikasi yang terpasang di perangkat Android Anda secara mandiri.

- **Mesin VpnService Android**: Membuka antarmuka VPN lokal di mana semua paket IP keluar diperiksa secara lokal. Jika aplikasi yang diblokir mencoba mengirim data, paketnya akan dibuang secara otomatis di tingkat lokal.
- **Pemblokiran Selektif**: Pengguna dapat memblokir akses Wi-Fi, akses Data Seluler, atau keduanya untuk setiap aplikasi secara individu.
- **Log Percobaan Pemblokiran**: Mencatat setiap kali aplikasi yang diblokir mencoba melakukan koneksi internet ilegal, lengkap dengan stempel waktu (timestamp) dan tipe jaringan yang digunakan.

---

### 3.5 Auto-Redialer & Call Screener
Solusi lengkap bagi pengguna yang sering melakukan panggilan berulang atau ingin menghindari panggilan spam yang tidak diinginkan.

- **Call Screening Integration**: Memanfaatkan `CallScreeningService` tingkat sistem untuk mendeteksi nomor masuk dan memblokir panggilan spam secara otomatis sebelum telepon Anda berdering.
- **Otomatisasi Panggilan Berulang (Redialer)**: Membantu melakukan panggilan keluar berulang kali dengan parameter jeda yang dapat disesuaikan (misalnya, menjadwalkan panggilan kembali dalam 5 detik jika saluran sibuk).
- **Deteksi State Panggilan**: Menggunakan `TelephonyManager` dan `PhoneStateListener` untuk memantau status panggilan (`IDLE`, `OFFHOOK`, `RINGING`) secara akurat tanpa membuat aplikasi macet atau terus berjalan di latar belakang tanpa kontrol.

---

### 3.6 Hardware & Device Diagnostics
Modul diagnostik mendalam untuk memahami performa fisik perangkat Anda.

- **Pemantauan CPU**: Membaca frekuensi inti prosesor secara dinamis serta persentase beban kerja CPU.
- **Sensor Telemetry**: Menampilkan pembacaan real-time dari sensor akselerometer, giroskop, magnetometer, dan sensor cahaya jika tersedia di perangkat.
- **Kesehatan & Suhu Baterai**: Memantau suhu baterai (dalam Celsius), tegangan listrik, teknologi baterai, dan sisa persentase daya baterai.

---

### 3.7 Cyber & AMOLED High-Contrast Themes
Kami merancang antarmuka Mandala Net agar nyaman digunakan dalam kondisi pencahayaan apa pun, terutama di malam hari.

- **Tema AMOLED Ultra-Dark**: Menggunakan warna hitam pekat (`#000000`) sebagai latar belakang utama untuk menghemat daya baterai pada layar AMOLED dan memberikan kontras visual maksimum.
- **Cyberpunk Accents**: Elemen UI dipadukan dengan warna aksen neon seperti *Cyber Green* untuk sinyal sukses, *Electric Blue* untuk informasi utama, dan *Neon Red* untuk status pemblokiran atau error.

---

## 🛠️ Stack Teknologi (Tech Stack)

Aplikasi ini dibangun menggunakan pustaka standar industri Android yang sangat stabil:

| Komponen | Pustaka / Teknologi | Deskripsi |
| :--- | :--- | :--- |
| **Bahasa Utama** | [Kotlin 1.9.x](https://kotlinlang.org/) | Bahasa modern, ekspresif, dan aman untuk pengembangan Android. |
| **Desain Antarmuka** | [Jetpack Compose (Material 3)](https://developer.android.com/jetpack/compose) | Toolkit deklaratif modern untuk merancang UI yang dinamis dan adaptif. |
| **Asinkron & Aliran Data** | [Kotlin Coroutines & Flow](https://kotlinlang.org/docs/coroutines-overview.html) | Mengelola operasi latar belakang, waktu tunggu, dan pembaruan UI secara reaktif. |
| **Koneksi HTTP** | [OkHttp3](https://square.github.io/okhttp/) | Mesin utama untuk melakukan Speed Test dan mengambil data server secara efisien. |
| **Penyimpanan Lokal** | [Room Database](https://developer.android.com/training/data-storage/room) | Abstraksi SQLite yang aman untuk menyimpan riwayat sinyal, log firewall, dan heatmap. |
| **Visualisasi Grafik** | [Vico Charts](https://github.com/patrykandpatrick/vico) | Library grafik native berkinerja tinggi untuk menampilkan tren sinyal historis. |
| **Sistem Navigasi** | [Compose Navigation](https://developer.android.com/guide/navigation) | Navigasi type-safe untuk berpindah antar modul tanpa overhead siklus hidup. |

---

## 🧹 Pencegahan Kebocoran Memori (Zero Memory Leak Architecture)

Kebocoran memori (memory leak) adalah musuh utama aplikasi utilitas yang berjalan terus-menerus. Mandala Net menerapkan aturan manajemen siklus hidup yang sangat ketat:

### 📱 1. Siklus Hidup WebView di Heatmap Screen
WebView sering kali menahan referensi ke Activity induk bahkan setelah layar ditutup. Kami membungkus WebView dalam `DisposableEffect` Jetpack Compose untuk membersihkannya secara manual saat pengguna keluar dari layar Heatmap:
```kotlin
DisposableEffect(Unit) {
    onDispose {
        // Hentikan pemuatan halaman, bersihkan cache, dan hancurkan objek WebView secara aman
        webView?.apply {
            stopLoading()
            clearHistory()
            clearCache(true)
            removeAllViews()
            destroy()
        }
    }
}
```

### 📡 2. Pembersihan Koneksi OkHttp di SpeedTest
OkHttpClient menggunakan thread pool internal (`Dispatcher`) dan `ConnectionPool` yang akan tetap aktif di latar belakang jika tidak dimatikan. Saat `MainViewModel` dihancurkan, kami memicu penutupan paksa seluruh thread pool tersebut:
```kotlin
override fun onCleared() {
    super.onCleared()
    // Menghentikan semua pemantauan sinyal seluler
    signalMonitor.stopMonitoring()
    // Batalkan coroutine speedtest yang sedang berjalan
    cancelSpeedTest()
    // Bersihkan thread pool dan pool koneksi OkHttp secara paksa
    speedTestManager.onDestroy()
}
```
Di dalam `SpeedTestManager.onDestroy()`:
```kotlin
fun onDestroy() {
    try {
        speedTestDispatcher.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    try {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    try {
        pingClient.dispatcher.executorService.shutdown()
        pingClient.connectionPool.evictAll()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
```

### 📞 3. Pembersihan Redialer & Service Listener
Redialer menggunakan `Job` coroutine berulang untuk memicu panggilan keluar. Jika pengguna keluar dari layar Redialer, `onCleared()` pada `RedialerViewModel` menjamin tidak ada background job yang tertinggal dan mengirimkan perintah untuk menghentikan Service secara rapi:
```kotlin
override fun onCleared() {
    super.onCleared()
    redialJob?.cancel()
    ringTimerJob?.cancel()
    
    val context = getApplication<Application>()
    try {
        val intent = Intent(context, com.mandala.net.service.RedialerService::class.java).apply {
            action = "STOP_SERVICE"
        }
        context.startService(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
```

---

## 📂 Struktur Direktori Proyek

```
app/src/main/java/com/mandala/net/
│
├── 📂 data/                         # Sumber Data Aplikasi
│   ├── 📂 local/                    # Database Lokal (Room)
│   │   ├── AppDatabase.kt          # Kelas Utama Database Room
│   │   ├── SignalDao.kt            # Operasi CRUD untuk Signal Tracker
│   │   └── HeatmapDao.kt           # Operasi CRUD untuk Titik Koordinat GPS Heatmap
│   └── 📂 model/                    # Model Data Entitas
│       ├── SignalEntity.kt
│       └── HeatmapEntity.kt
│
├── 📂 service/                      # Layanan Latar Belakang (Android Services)
│   ├── FirewallVpnService.kt       # Implementasi Core VpnService untuk Network Shield
│   ├── CallScreener.kt             # Implementasi CallScreeningService untuk Blokir Spam
│   ├── CallStateMonitor.kt         # Registrasi TelephonyManager PhoneStateListener
│   └── RedialerService.kt          # Layanan Latar Belakang untuk Sistem Redial Panggilan
│
├── 📂 ui/                           # Lapisan Antarmuka Pengguna (UI Layer)
│   ├── 📂 theme/                    # Tema Desain Sistem (Material 3)
│   │   ├── Color.kt                # Palet Warna Cyber, Light, & AMOLED Dark
│   │   ├── Theme.kt                # Inisialisasi MandalaTheme & CyberTheme
│   │   └── Type.kt                 # Konfigurasi Tipografi & Font-family
│   │
│   ├── 📂 component/                # Komponen UI Jetpack Compose Reusable
│   │   ├── GaugeSpeedometer.kt     # Animasi Meteran Kecepatan Kustom
│   │   ├── CustomBottomBar.kt      # Navigasi Bar Bawah Modern
│   │   └── SignalStrengthCard.kt   # Kartu Ringkasan Sinyal Seluler & Wi-Fi
│   │
│   ├── SpeedTestScreen.kt          # UI untuk Tes Kecepatan Ping, Download & Upload
│   ├── SignalTrackerScreen.kt      # UI Diagnostik Sinyal Historis & Grafik Vico
│   ├── HeatmapScreen.kt            # UI Peta Interaktif Sinyal (Leaflet WebView)
│   ├── WifiAnalyzerScreen.kt       # UI Grafik Parabola Saluran Wi-Fi (Canvas)
│   ├── HardwareInfoScreen.kt       # UI Informasi Sensor & Hardware Perangkat
│   ├── NetworkShieldScreen.kt      # UI Firewall & Kontrol Blokir Aplikasi
│   └── RedialerScreen.kt           # UI Konfigurasi Auto-Redialer & Call Screening
│
├── MainViewModel.kt                # ViewModel Utama untuk Menghubungkan Sinyal, SpeedTest & Firewall
├── RedialerViewModel.kt            # ViewModel Khusus untuk Manajemen Alur Redial Panggilan
├── SpeedTestManager.kt             # Pengelola Logika Utama Eksekusi Koneksi HTTP SpeedTest
├── TileCacheManager.kt             # Utilitas Manajemen Cache Peta Lokal untuk Heatmap
└── MainActivity.kt                 # Entry Point Utama Aplikasi Android & Alur Inisialisasi
```

---

## ⚙️ Panduan Instalasi & Konfigurasi Pengembangan

Untuk menjalankan, memodifikasi, dan membangun aplikasi Mandala Net di mesin lokal Anda, ikuti langkah-langkah terperinci di bawah ini.

### 📋 Prasyarat Sistem
Sebelum memulai, pastikan sistem pengembangan Anda telah dilengkapi dengan:
- **Java Development Kit (JDK)**: Versi 17 atau yang terbaru.
- **Android Studio**: Koala | 2024.1.1 atau versi yang lebih baru sangat direkomendasikan.
- **Android SDK**: SDK Platform level 34 (Android 14) terpasang via SDK Manager.
- **Gradle**: Versi 8.x ke atas (dikelola secara otomatis melalui Gradle Wrapper).

### 🛠️ Langkah-Langkah Kompilasi via CLI (Command Line Interface)

1. **Unduh repositori proyek**:
   ```bash
   git clone https://github.com/username/mandala-net.git
   cd mandala-net
   ```

2. **Atur variabel lingkungan (Environment Variables)** jika diperlukan. Anda dapat membuat file `.env` di direktori akar proyek dengan menyalin `.env.example`:
   ```bash
   cp .env.example .env
   ```

3. **Verifikasi status build proyek**:
   Gunakan Gradle Wrapper bawaan untuk memastikan semua dependensi terunduh dengan benar dan struktur kode bebas dari kesalahan sintaks:
   ```bash
   ./gradlew assembleDebug
   ```

4. **Menjalankan Unit Test**:
   Untuk menguji fungsionalitas logika bisnis, jalankan pengujian unit lokal:
   ```bash
   ./gradlew testDebugUnitTest
   ```

5. **Membuat File APK (Debug Build)**:
   File APK yang dihasilkan akan berada di direktori `app/build/outputs/apk/debug/`:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 🧪 Pengujian Lokal & Pemeliharaan Kode

Kami sangat menjaga kualitas kode aplikasi Mandala Net agar bebas dari bug fatal yang dapat menyebabkan crash tiba-tiba pada perangkat pengguna.

### 1. Pengujian Unit dengan Robolectric
Untuk mensimulasikan lingkungan OS Android (seperti siklus hidup ViewModel, database Room, dan pembacaan Sensor) di dalam komputer pengembangan tanpa memerlukan emulator fisik:
```bash
./gradlew :app:testDebugUnitTest
```

### 2. Format & Linting Kode (Code Style Validation)
Pastikan kode Anda selalu bersih, rapi, dan mengikuti pedoman penulisan Kotlin yang disarankan oleh Google:
```bash
./gradlew lint
```

---

## 🤝 Kontribusi & Kode Etik

Kami menyambut kontribusi dari pengembang di seluruh dunia! Baik itu berupa perbaikan bug, penambahan fitur baru, pengoptimalan performa, atau penyempurnaan dokumentasi.

### 📝 Cara Berkontribusi
1. **Fork** repositori ini ke akun GitHub Anda pribadi.
2. Buat cabang fitur baru (**Branch Fitur**) dari cabang `main`:
   ```bash
   git checkout -b fitur/fitur-keren-anda
   ```
3. Lakukan perubahan kode, dan pastikan Anda menjalankan `./gradlew assembleDebug` untuk memverifikasi bahwa tidak ada kode yang rusak.
4. Lakukan **Commit** pada perubahan Anda dengan pesan yang jelas dan deskriptif:
   ```bash
   git commit -m "Menambahkan fitur analisis spektrum Wi-Fi 6 GHz"
   ```
5. Dorong cabang Anda ke repositori fork:
   ```bash
   git push origin fitur/fitur-keren-anda
   ```
6. Buka halaman repositori asli dan kirimkan **Pull Request (PR)**. Jelaskan secara detail apa yang telah Anda ubah dan mengapa perubahan tersebut diperlukan.

---

## 📄 Lisensi MIT

Aplikasi **Mandala Net** dirilis di bawah lisensi open-source **MIT License**. Anda bebas menggunakan, memodifikasi, mendistribusikan, dan menjual kode ini baik untuk keperluan pribadi maupun komersial dengan tetap mencantumkan hak cipta asli.

```text
MIT License

Copyright (c) 2026 Ahmad Basith

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

<p align="center">
  Dibuat dengan ❤️ oleh <strong>Ahmad Basith</strong> untuk komunitas pengembang & pengguna Android di seluruh dunia.
</p>
