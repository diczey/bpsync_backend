# BPSync Backend

## 🎯 Genel Bakış

BPSync mobil uygulaması için RESTful API backend servisi.

## 🛠 Teknoloji Seçenekleri

### Seçenek 1: Python + FastAPI (Önerilen - Hızlı Prototipleme)
- Hızlı geliştirme
- Otomatik API dokümantasyonu (Swagger UI)
- Kolay ML/XGBoost entegrasyonu
- Async desteği

### Seçenek 2: Node.js + Express
- JavaScript/TypeScript
- Hızlı ve hafif
- Geniş paket ekosistemi

### Seçenek 3: Kotlin + Spring Boot
- Android ile aynı dil
- Enterprise-grade
- Güçlü tip güvenliği

## 📁 Proje Yapısı

```
backend/
├── app/
│   ├── __init__.py
│   ├── main.py              # FastAPI uygulaması
│   ├── config.py            # Ayarlar
│   ├── database.py          # Veritabanı bağlantısı
│   │
│   ├── models/              # SQLAlchemy modelleri
│   │   ├── __init__.py
│   │   ├── user.py
│   │   ├── health_reading.py
│   │   └── notification.py
│   │
│   ├── schemas/             # Pydantic şemaları (DTO'lar)
│   │   ├── __init__.py
│   │   ├── auth.py
│   │   ├── health.py
│   │   ├── dashboard.py
│   │   ├── trends.py
│   │   ├── reports.py
│   │   └── notifications.py
│   │
│   ├── routers/             # API endpoint'leri
│   │   ├── __init__.py
│   │   ├── auth.py
│   │   ├── dashboard.py
│   │   ├── readings.py
│   │   ├── trends.py
│   │   ├── reports.py
│   │   ├── notifications.py
│   │   └── profile.py
│   │
│   ├── services/            # İş mantığı
│   │   ├── __init__.py
│   │   ├── auth_service.py
│   │   ├── health_service.py
│   │   ├── trend_service.py
│   │   └── ml_service.py    # XGBoost BP tahmini
│   │
│   └── utils/               # Yardımcı fonksiyonlar
│       ├── __init__.py
│       ├── security.py      # JWT, şifreleme
│       └── mock_data.py     # Sahte veri üreteci
│
├── tests/                   # Unit testler
├── requirements.txt
├── .env.example
└── docker-compose.yml
```

## 🚀 Başlangıç Adımları

### Adım 1: Ortam Kurulumu
```bash
cd backend
python -m venv venv
venv\Scripts\activate  # Windows
pip install -r requirements.txt
```

### Adım 2: Geliştirme Sunucusunu Başlat
```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### Adım 3: API Dokümantasyonunu Görüntüle
- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc

## 📡 API Endpoints

Android uygulamanızın beklediği endpoint'ler:

| Method | Endpoint | Açıklama |
|--------|----------|----------|
| POST | /auth/login | Kullanıcı girişi |
| POST | /auth/logout | Çıkış |
| POST | /auth/register | Kayıt |
| GET | /dashboard/summary | Dashboard özeti |
| GET | /readings | Sağlık ölçümleri listesi |
| POST | /readings | Yeni ölçüm kaydet |
| GET | /readings/{id} | Tek ölçüm detayı |
| GET | /trends | Trend verileri |
| GET | /trends/ecg | ECG trendleri |
| GET | /trends/ppg | PPG trendleri |
| GET | /reports/weekly | Haftalık rapor |
| GET | /reports/monthly | Aylık rapor |
| GET | /notifications | Bildirimler |
| PUT | /notifications/{id}/read | Okundu işaretle |
| PUT | /notifications/read-all | Tümünü okundu işaretle |
| DELETE | /notifications/{id} | Bildirim sil |
| GET | /profile | Profil bilgileri |
| PUT | /profile | Profil güncelle |
| DELETE | /profile | Hesap sil |

## 🎲 Mock Veri Stratejisi

Gerçek sensör verileriniz gelmeden önce:

1. **Rastgele fizyolojik veri üreteci** kullanın
2. **Gerçekçi aralıklar** belirleyin:
   - Kalp atış hızı: 60-100 bpm
   - Sistolik BP: 90-140 mmHg
   - Diyastolik BP: 60-90 mmHg
   - SpO2: 95-100%
   - Sıcaklık: 36.0-37.5°C

3. **PTT (Pulse Transit Time) simülasyonu**:
   - ECG R-peak zamanlarını üretin
   - PPG peak zamanlarını üretin
   - PTT = PPG_peak - ECG_R_peak

## 🔐 Güvenlik

- JWT tabanlı kimlik doğrulama
- Bcrypt ile şifre hashleme
- HTTPS zorunlu (production'da)
- Rate limiting

## 📊 Veritabanı

Geliştirme için: SQLite
Production için: PostgreSQL

## 🧪 Test

```bash
pytest tests/ -v
```

