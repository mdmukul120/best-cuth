# BestCut ✂️ — Android ভিডিও এডিটর

Kotlin + Jetpack Compose + AndroidX Media3 (ExoPlayer / Transformer) দিয়ে তৈরি, ক্যাপকাটের মতো টাইমলাইন-ভিত্তিক ভিডিও এডিটর।

## ফিচার
- একাধিক ভিডিও ইমপোর্ট (Photo Picker, কোনো পারমিশন লাগে না)
- টাইমলাইন, লাইভ প্রিভিউ, স্ক্রাবার
- **Trim**, **Split** (প্লেহেডে কাটা), **Duplicate**, **Delete**, **Move** (ক্রম বদল)
- **Speed** 0.25x – 4x (অডিওসহ)
- **Filters**: Original / B&W / Warm / Cool / Vivid + **Brightness**
- **Text caption** ওভারলে
- ক্লিপ **Mute**
- **Export** 480p / 720p / 1080p (H.264 + AAC) → `Movies/BestCut`

## অটোমেটিক বিল্ড (৩টি উপায়)

### ১) GitHub Actions — ফোন থেকেই সম্ভব (সবচেয়ে সহজ)
```bash
gh auth login
./scripts/push_to_github.sh
```
পুশ করলেই `.github/workflows/build.yml` নিজে থেকে APK বানাবে।
শেষ হলে **Releases → latest → BestCut.apk** থেকে নামিয়ে ইনস্টল করুন।
(ওয়েবসাইট দিয়েও পারবেন: নতুন repo খুলে এই ফোল্ডারের ফাইলগুলো আপলোড করুন।)

### ২) লোকাল স্ক্রিপ্ট (Linux / macOS)
```bash
./scripts/auto_build.sh
```
JDK 17, Android SDK, Gradle নিজে ইনস্টল করে `dist/BestCut.apk` বানায়।

### ৩) Android Studio
প্রজেক্ট ফোল্ডার Open করুন → Run ▶️।

## নোট
- minSdk 29 (Android 10+)
- Release APK debug key দিয়ে সাইন করা, তাই সরাসরি ইনস্টল হবে। Play Store-এর জন্য নিজের keystore বসান (`app/build.gradle.kts`)।
- এক্সপোর্ট চলাকালীন অ্যাপ খোলা রাখুন।
