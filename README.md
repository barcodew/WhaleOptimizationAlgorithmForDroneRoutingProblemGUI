# Drone Routing Problem (DRP) Optimization using WOA, GA, and PSO

## 📌 Overview
Proyek ini merupakan implementasi dan perbandingan beberapa algoritma metaheuristik untuk menyelesaikan **Drone Routing Problem (DRP)** dengan mempertimbangkan keterbatasan energi baterai drone.

Algoritma yang digunakan pada penelitian/program ini yaitu:

- Whale Optimization Algorithm (WOA)
- Genetic Algorithm (GA)
- Particle Swarm Optimization (PSO)

Program dibuat menggunakan bahasa **Java** dan bertujuan mencari rute drone terbaik dengan konsumsi energi minimum serta jarak tempuh optimal.

---

# 🎯 Objectives

Tujuan utama program ini adalah:

- Menentukan urutan kunjungan customer terbaik
- Meminimalkan total jarak tempuh drone
- Meminimalkan konsumsi energi
- Membandingkan performa algoritma WOA, GA, dan PSO pada DRP

---

# 🧠 Problem Description

Drone harus mengunjungi seluruh customer dan kembali ke depot dengan batasan:

- Kapasitas baterai terbatas
- Konsumsi energi berdasarkan jarak
- Drone dapat melakukan recharge pada charging station
- Semua customer wajib dikunjungi tepat satu kali

---

# ⚙️ Algorithms Used

## 1. Whale Optimization Algorithm (WOA)

WOA merupakan algoritma metaheuristik berbasis perilaku berburu paus bungkuk (humpback whale).

Fitur implementasi:
- Encircling prey
- Bubble-net attacking
- Exploration & exploitation balancing
- Continuous-to-discrete decoding menggunakan LOV (Largest Order Value)

### Kelebihan
- Konvergensi cepat
- Sederhana
- Cocok untuk optimasi global

### Kekurangan
- Berpotensi mengalami premature convergence pada iterasi tertentu

---

## 2. Genetic Algorithm (GA)

GA merupakan algoritma evolusi yang meniru proses seleksi alam.

Operator yang digunakan:
- Selection
- Crossover
- Mutation
- Elitism

### Kelebihan
- Diversitas solusi tinggi
- Baik untuk ruang solusi diskrit

### Kekurangan
- Membutuhkan tuning parameter crossover dan mutation rate

---

## 3. Particle Swarm Optimization (PSO)

PSO merupakan algoritma optimasi berbasis perilaku kawanan partikel.

Komponen utama:
- Velocity update
- Position update
- Personal best (pBest)
- Global best (gBest)

### Kelebihan
- Implementasi sederhana
- Cepat menemukan solusi awal bagus

### Kekurangan
- Mudah terjebak local optimum

---

# 🏗️ Project Structure

```bash
src/
│
├── Main/
│   ├── DRP_FULL.java ( WOA )
│   ├── DRP_GA.java
│   ├── DRP_PSO.java
│
├── Algorithms/
│   ├── WOA.java
│   ├── GA.java
│   ├── PSO.java
│
├── Helpers/
│   ├── DatasetGenerator.java
│   ├── DatasetReader.java
│   ├── MatrixBuilder.java
│
├── Model/
│   ├── Node.java
│
└── Datasets/
    └── customer.csv